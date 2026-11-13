package com.klay.charge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("card")
public class Card {
    @TableId(type = IdType.AUTO)
    private Long cardId;
    private String cardNo;
    private Integer cardType;
    private BigDecimal balance;
    private Boolean registered;
    /** 0 正常 / 1 作废 */
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
