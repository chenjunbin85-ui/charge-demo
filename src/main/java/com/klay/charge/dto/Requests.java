package com.klay.charge.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 所有入参集中定义；payAmount 故意不出现在任何请求里（金额只读）。 */
public final class Requests {
    private Requests() {
    }

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "客户姓名必填")
        private String customerName;
        @NotNull(message = "应收总额必填")
        @DecimalMin(value = "0.01", message = "金额必须大于 0")
        private BigDecimal totalAmount;
        @DecimalMin(value = "0.01", message = "折扣非法")
        @DecimalMax(value = "100", message = "折扣不能超过 100")
        private BigDecimal discount = BigDecimal.valueOf(100);
    }

    @Data
    public static class ChargeRequest {
        @NotNull(message = "支付方式必填")
        private Integer payMethod;
        @NotBlank(message = "卡号必填")
        private String cardNo;
        @NotBlank(message = "操作人必填")
        private String operator;
        @NotBlank(message = "角色必填")
        private String role;
    }

    @Data
    public static class RefundRequest {
        @NotBlank(message = "操作人必填")
        private String operator;
        @NotBlank(message = "角色必填")
        private String role;
        private String remark;
    }

    @Data
    public static class CardRequest {
        @NotBlank(message = "卡号必填")
        private String cardNo;
        @NotNull(message = "卡类型必填")
        private Integer cardType;
        private Boolean registered = false;
        @DecimalMin(value = "0.00", message = "初始余额不能为负")
        private BigDecimal initialBalance = BigDecimal.ZERO;
    }

    @Data
    public static class RechargeRequest {
        @NotNull(message = "充值金额必填")
        @DecimalMin(value = "0.01", message = "充值金额必须大于 0")
        private BigDecimal amount;
        @NotBlank(message = "操作人必填")
        private String operator;
    }
}
