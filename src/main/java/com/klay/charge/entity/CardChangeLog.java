package com.klay.charge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 每笔变动必须落流水：卡余额变动 + 财务科目变动，两套口径分开记。 */
@Data
@TableName("card_change_log")
public class CardChangeLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String cardNo;
    private Integer cardType;
    private Integer changeType;
    /** 卡余额变动，带符号：扣款为负，充值/退费为正。 */
    private BigDecimal changeAmount;
    private BigDecimal balance;
    private String relateSerial;
    /** 健康卡专用：0 待结算 / 1 已结算；预付卡为 null。 */
    private Integer settleStatus;
    private String financeSubject;
    /** 该笔流水对财务科目的影响，方向由科目性质决定：负债增加为正 / 资产减少为负。 */
    private BigDecimal subjectDelta;
    private String operator;
    private LocalDateTime createdAt;
}
