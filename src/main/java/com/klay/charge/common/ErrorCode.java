package com.klay.charge.common;

import lombok.Getter;

@Getter
public enum ErrorCode {
    PARAM_INVALID(400, "参数校验失败"),
    PERMISSION_DENIED(403, "无操作权限"),
    ORDER_NOT_FOUND(404, "收费记录不存在"),
    CARD_NOT_FOUND(404, "卡号不存在"),
    STATUS_CONFLICT(409, "当前状态不允许该操作"),
    CARD_DISABLED(409, "卡片已作废"),
    BALANCE_NOT_ENOUGH(409, "余额不足"),
    DUPLICATE_OPERATION(409, "重复操作已被幂等拦截"),
    INTERNAL(500, "系统异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
