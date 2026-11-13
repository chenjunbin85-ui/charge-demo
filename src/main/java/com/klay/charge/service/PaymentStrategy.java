package com.klay.charge.service;

import com.klay.charge.dto.Requests;
import com.klay.charge.entity.ChargeOrder;

/** 支付方式策略：卡类走余额与流水，非卡类只做状态流转。 */
public interface PaymentStrategy {

    void pay(ChargeOrder order, Requests.ChargeRequest request);

    void refund(ChargeOrder order, String operator);
}
