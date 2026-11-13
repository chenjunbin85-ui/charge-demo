package com.klay.charge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.klay.charge.common.BizException;
import com.klay.charge.common.ErrorCode;
import com.klay.charge.dto.Requests;
import com.klay.charge.entity.Card;
import com.klay.charge.entity.CardChangeLog;
import com.klay.charge.enums.CardType;
import com.klay.charge.enums.ChangeType;
import com.klay.charge.enums.OperatorRole;
import com.klay.charge.mapper.CardChangeLogMapper;
import com.klay.charge.mapper.CardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CardService {

    private final CardMapper cardMapper;
    private final CardChangeLogMapper logMapper;

    @Transactional(rollbackFor = Exception.class)
    public Card open(Requests.CardRequest request, String operator) {
        Card existing = cardMapper.selectByCardNoForUpdate(request.getCardNo());
        if (existing != null) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION, "卡号已存在: " + request.getCardNo());
        }
        CardType type = CardType.of(request.getCardType());
        BigDecimal initial = request.getInitialBalance() == null ? BigDecimal.ZERO : request.getInitialBalance();

        Card card = new Card();
        card.setCardNo(request.getCardNo());
        card.setCardType(type.getCode());
        card.setBalance(initial);
        card.setRegistered(Boolean.TRUE.equals(request.getRegistered()));
        card.setStatus(0);
        card.setCreatedAt(LocalDateTime.now());
        card.setUpdatedAt(LocalDateTime.now());
        cardMapper.insert(card);
        appendLog(card, ChangeType.CREATE, initial, initial, null, operator);
        return card;
    }

    @Transactional(rollbackFor = Exception.class)
    public Card recharge(String cardNo, Requests.RechargeRequest request) {
        Card card = cardMapper.selectByCardNoForUpdate(cardNo);
        if (card == null) {
            throw new BizException(ErrorCode.CARD_NOT_FOUND, "卡号不存在: " + cardNo);
        }
        if (card.getStatus() != null && card.getStatus() == 1) {
            throw new BizException(ErrorCode.CARD_DISABLED, "卡片已作废");
        }
        BigDecimal after = card.getBalance().add(request.getAmount());
        card.setBalance(after);
        card.setUpdatedAt(LocalDateTime.now());
        cardMapper.updateById(card);
        appendLog(card, ChangeType.RECHARGE, request.getAmount(), after, null, request.getOperator());
        return card;
    }

    @Transactional(rollbackFor = Exception.class)
    public Card voidCard(String cardNo, String operator, String role) {
        OperatorRole.of(role).requireAdmin();
        Card card = cardMapper.selectByCardNoForUpdate(cardNo);
        if (card == null) {
            throw new BizException(ErrorCode.CARD_NOT_FOUND, "卡号不存在: " + cardNo);
        }
        card.setStatus(1);
        card.setUpdatedAt(LocalDateTime.now());
        cardMapper.updateById(card);
        appendLog(card, ChangeType.VOID, BigDecimal.ZERO, card.getBalance(), null, operator);
        return card;
    }

    public List<CardChangeLog> logs(String cardNo) {
        return logMapper.selectList(new LambdaQueryWrapper<CardChangeLog>()
                .eq(CardChangeLog::getCardNo, cardNo)
                .orderByAsc(CardChangeLog::getId));
    }

    /** 常规流水：科目影响由卡类型 + 变动类型自动推导。 */
    public void appendLog(Card card, ChangeType type, BigDecimal delta, BigDecimal balance,
                          String relateSerial, String operator) {
        CardType cardType = CardType.of(card.getCardType());
        appendLog(card, type, delta, balance, relateSerial, operator,
                computeSubjectDelta(cardType, type, delta));
    }

    /** 结算等不改变卡余额、只影响科目的流水，显式传入科目金额。 */
    public void appendLog(Card card, ChangeType type, BigDecimal delta, BigDecimal balance,
                          String relateSerial, String operator, BigDecimal subjectDelta) {
        CardType cardType = CardType.of(card.getCardType());
        CardChangeLog log = new CardChangeLog();
        log.setCardNo(card.getCardNo());
        log.setCardType(card.getCardType());
        log.setChangeType(type.getCode());
        log.setChangeAmount(delta);
        log.setBalance(balance);
        log.setRelateSerial(relateSerial);
        // 只有健康卡的消费核销/退费才进对账清单；开卡、充值、作废不产生待结算；
        // 结算流水自身标记为已结算，避免被下一轮重复结算。
        Integer settleStatus = null;
        if (cardType == CardType.HEALTH) {
            if (type == ChangeType.SETTLE) {
                settleStatus = 1;
            } else if (type == ChangeType.CONSUME || type == ChangeType.REFUND) {
                settleStatus = 0;
            }
        }
        log.setSettleStatus(settleStatus);
        log.setFinanceSubject(cardType.getSubject().name());
        log.setSubjectDelta(subjectDelta);
        log.setOperator(operator);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    /**
     * 两类卡科目方向相反，这是本 demo 的核心：
     * 预付卡 = 预收账款(负债)：充值 +（欠客户更多服务），消费 -（服务交付，负债转收入），退费 +。
     * 健康卡 = 应收账款(资产)：核销 +（开卡公司欠我），退费 -（冲减应收），结算回款 -；充值/开卡不产生我方应收。
     */
    public BigDecimal computeSubjectDelta(CardType type, ChangeType changeType, BigDecimal delta) {
        if (type == CardType.PREPAID) {
            return changeType == ChangeType.VOID ? BigDecimal.ZERO : delta;
        }
        if (changeType == ChangeType.CONSUME || changeType == ChangeType.REFUND) {
            return delta.negate();
        }
        return BigDecimal.ZERO;
    }
}
