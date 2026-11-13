package com.klay.charge.service;

import com.klay.charge.enums.PayMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 策略工厂：当前只注册双卡策略；将来加现金/优惠券只需新增实现并在此登记，核心服务不动。 */
@Component
@RequiredArgsConstructor
public class PaymentStrategyFactory {

    private final CardPaymentStrategy cardStrategy;

    public PaymentStrategy get(PayMethod method) {
        return cardStrategy;
    }
}
