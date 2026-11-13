package com.klay.charge.enums;

import lombok.Getter;

@Getter
public enum ChangeType {
    CREATE(0, "新建"),
    RECHARGE(1, "充值"),
    CONSUME(2, "消费/核销"),
    REFUND(3, "退费"),
    VOID(4, "作废"),
    SETTLE(5, "对账结算");

    private final int code;
    private final String label;

    ChangeType(int code, String label) {
        this.code = code;
        this.label = label;
    }
}
