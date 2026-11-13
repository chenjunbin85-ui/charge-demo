package com.klay.charge.enums;

import com.klay.charge.common.BizException;
import com.klay.charge.common.ErrorCode;

/** 角色权限：收费=收费员，退费/作废=管理员，敏感操作留痕。 */
public enum OperatorRole {
    CASHIER, ADMIN;

    public static OperatorRole of(String role) {
        try {
            return valueOf(role == null ? "" : role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未知角色: " + role);
        }
    }

    public void requireAdmin() {
        if (this != ADMIN) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "退费/作废仅管理员可操作");
        }
    }
}
