package com.klay.charge.enums;

import com.klay.charge.common.BizException;
import com.klay.charge.common.ErrorCode;
import lombok.Getter;

/** 本 demo 只保留资金归属相反的两条卡通道。 */
@Getter
public enum PayMethod {
    PREPAID_CARD(1, "预付卡"),
    HEALTH_CARD(2, "电子健康卡");

    private final int code;
    private final String label;

    PayMethod(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public CardType cardType() {
        return this == PREPAID_CARD ? CardType.PREPAID : CardType.HEALTH;
    }

    public static PayMethod of(int code) {
        for (PayMethod m : values()) {
            if (m.code == code) {
                return m;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知支付方式: " + code + "（本 demo 仅支持 1 预付卡 / 2 电子健康卡）");
    }
}
