package com.klay.charge.enums;

import com.klay.charge.common.BizException;
import com.klay.charge.common.ErrorCode;
import lombok.Getter;

/** 收费状态机：未收费 -> 已收费 -> 已退费；记账态可转已收费。 */
@Getter
public enum ChargeStatus {
    UNPAID(0, "未收费"),
    PAID(1, "已收费"),
    CREDIT(2, "记账"),
    REFUNDED(3, "已退费");

    private final int code;
    private final String label;

    ChargeStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean canCharge() {
        return this == UNPAID || this == CREDIT;
    }

    public boolean canRefund() {
        return this == PAID;
    }

    public static ChargeStatus of(int code) {
        for (ChargeStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知收费状态: " + code);
    }
}
