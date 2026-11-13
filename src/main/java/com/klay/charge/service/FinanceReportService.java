package com.klay.charge.service;

import com.klay.charge.entity.CardChangeLog;
import com.klay.charge.enums.FinanceSubject;
import com.klay.charge.mapper.CardChangeLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 业务规则：两类卡科目方向相反，报表分科目统计，严禁合并。 */
@Service
@RequiredArgsConstructor
public class FinanceReportService {

    private final CardChangeLogMapper logMapper;

    public Map<String, Object> report() {
        List<CardChangeLog> logs = logMapper.selectList(null);
        Map<String, Map<String, Object>> bySubject = new LinkedHashMap<>();
        for (FinanceSubject subject : FinanceSubject.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subjectName", subject.getSubjectName());
            row.put("direction", subject.getDirection());
            row.put("subjectBalance", BigDecimal.ZERO.setScale(2));
            row.put("cardBalance", BigDecimal.ZERO.setScale(2));
            row.put("entries", 0);
            bySubject.put(subject.name(), row);
        }
        BigDecimal pendingSettlement = BigDecimal.ZERO;
        for (CardChangeLog log : logs) {
            Map<String, Object> row = bySubject.computeIfAbsent(log.getFinanceSubject(),
                    k -> new LinkedHashMap<>());
            BigDecimal subject = (BigDecimal) row.getOrDefault("subjectBalance", BigDecimal.ZERO);
            BigDecimal card = (BigDecimal) row.getOrDefault("cardBalance", BigDecimal.ZERO);
            row.put("subjectBalance", subject.add(log.getSubjectDelta()).setScale(2));
            row.put("cardBalance", card.add(log.getChangeAmount()).setScale(2));
            row.put("entries", (Integer) row.getOrDefault("entries", 0) + 1);
            if (FinanceSubject.EHC_RECEIVABLE.name().equals(log.getFinanceSubject())
                    && Integer.valueOf(0).equals(log.getSettleStatus())) {
                pendingSettlement = pendingSettlement.add(log.getChangeAmount().negate());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>(bySubject);
        result.put("pendingSettlement", pendingSettlement.setScale(2));
        result.put("note", "预付卡=预收账款(负债，方向随卡余额同向)；电子健康卡=应收账款(资产，核销增加/结算减少)。"
                + "科目方向相反，严禁并账；pendingSettlement 为健康卡待回款净额。");
        return result;
    }
}
