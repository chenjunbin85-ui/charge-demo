package com.klay.charge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("charge_order")
public class ChargeOrder {
    @TableId(type = IdType.AUTO)
    private Long chargeId;
    private String serialNo;
    private String customerName;
    private BigDecimal totalAmount;
    private BigDecimal discount;
    /** 折后需付：只由后端计算，接口不允许传入。 */
    private BigDecimal payAmount;
    private Integer chargeStatus;
    private Integer payMethod;
    private String cardNo;
    private String operator;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
