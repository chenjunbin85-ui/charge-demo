package com.klay.charge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.klay.charge.common.BizException;
import com.klay.charge.common.ErrorCode;
import com.klay.charge.entity.Card;
import com.klay.charge.entity.CardChangeLog;
import com.klay.charge.enums.ChangeType;
import com.klay.charge.enums.FinanceSubject;
import com.klay.charge.mapper.CardChangeLogMapper;
import com.klay.charge.mapper.CardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 健康卡周期对账结算：待结算核销流水 -> 已结算，并冲减应收账款，形成完整闭环。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final CardChangeLogMapper logMapper;
    private final CardMapper cardMapper;
    private final CardService cardService;
    private final AtomicInteger sequence = new AtomicInteger(0);

    /** 对账清单：仅健康卡、仅待结算。 */
    public List<CardChangeLog> pending(String cardNo) {
        return logMapper.selectList(new LambdaQueryWrapper<CardChangeLog>()
                .eq(CardChangeLog::getFinanceSubject, FinanceSubject.EHC_RECEIVABLE.name())
                .eq(CardChangeLog::getSettleStatus, 0)
                .eq(cardNo != null && !cardNo.isBlank(), CardChangeLog::getCardNo, cardNo)
                .orderByAsc(CardChangeLog::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> settle(String cardNo, String operator) {
        List<CardChangeLog> pending = pending(cardNo);
        if (pending.isEmpty()) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION, "无待结算流水，重复结算已被幂等拦截");
        }
        String batchNo = "ST" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%04d", sequence.incrementAndGet());

        Map<String, List<CardChangeLog>> byCard = new LinkedHashMap<>();
        for (CardChangeLog log : pending) {
            byCard.computeIfAbsent(log.getCardNo(), k -> new ArrayList<>()).add(log);
        }

        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> details = new ArrayList<>();
        for (Map.Entry<String, List<CardChangeLog>> entry : byCard.entrySet()) {
            BigDecimal net = BigDecimal.ZERO;
            for (CardChangeLog log : entry.getValue()) {
                // change_amount 为负=核销(应收增加)，为正=退费(应收冲减)，取负号得净应收
                net = net.add(log.getChangeAmount().negate());
                log.setSettleStatus(1);
                logMapper.updateById(log);
            }
            Card card = cardMapper.selectByCardNoForUpdate(entry.getKey());
            if (card == null) {
                throw new BizException(ErrorCode.CARD_NOT_FOUND, "结算失败，卡不存在: " + entry.getKey());
            }
            // 结算回款不进卡余额（change_amount=0），只冲减应收：subject_delta = -net
            cardService.appendLog(card, ChangeType.SETTLE, BigDecimal.ZERO, card.getBalance(),
                    batchNo, operator, net.negate());
            total = total.add(net);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("cardNo", entry.getKey());
            row.put("entries", entry.getValue().size());
            row.put("netReceivable", net.setScale(2));
            row.put("subject", FinanceSubject.EHC_RECEIVABLE.name());
            details.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNo", batchNo);
        result.put("settledAmount", total.setScale(2));
        result.put("entries", pending.size());
        result.put("effect", "应收账款(资产) 减少 " + total.setScale(2) + " 元，待结算余额归零");
        result.put("details", details);
        log.info("健康卡结算完成 batch={} amount={} entries={}", batchNo, total, pending.size());
        return result;
    }
}
