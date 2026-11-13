package com.klay.charge.enums;

import lombok.Getter;

/** 两类卡资金归属不同：预付卡自营储值，健康卡第三方代收。 */
@Getter
public enum CardType {
    PREPAID(1, "预付卡", FinanceSubject.PREPAID_LIABILITY),
    HEALTH(2, "电子健康卡", FinanceSubject.EHC_RECEIVABLE);

    private final int code;
    private final String label;
    private final FinanceSubject subject;

    CardType(int code, String label, FinanceSubject subject) {
        this.code = code;
        this.label = label;
        this.subject = subject;
    }

    public static CardType of(int code) {
        for (CardType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知卡类型: " + code);
    }
}
