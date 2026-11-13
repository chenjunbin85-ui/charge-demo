package com.klay.charge.service;

import com.klay.charge.common.BizException;
import com.klay.charge.common.ErrorCode;
import com.klay.charge.dto.Requests;
import com.klay.charge.entity.Card;
import com.klay.charge.entity.ChargeOrder;
import com.klay.charge.enums.CardType;
import com.klay.charge.enums.ChangeType;
import com.klay.charge.enums.PayMethod;
import com.klay.charge.mapper.CardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 预付卡 / 电子健康卡：行锁扣款、余额不得为负、每笔变动落流水、退费原路退回。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardPaymentStrategy implements PaymentStrategy {

    private final CardMapper cardMapper;
    private final CardService cardService;

    @Override
    public void pay(ChargeOrder order, Requests.ChargeRequest request) {
        String cardNo = request.getCardNo();
        if (cardNo == null || cardNo.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "卡支付必须提供卡号");
        }
        PayMethod method = PayMethod.of(request.getPayMethod());
        CardType expected = method.cardType();

        Card card = lockAndValidate(cardNo, expected);
        BigDecimal amount = order.getPayAmount();
        if (card.getBalance().compareTo(amount) < 0) {
            throw new BizException(ErrorCode.BALANCE_NOT_ENOUGH,
                    "余额不足，当前余额 " + card.getBalance() + " 元");
        }

        BigDecimal after = card.getBalance().subtract(amount);
        card.setBalance(after);
        card.setUpdatedAt(LocalDateTime.now());
        cardMapper.updateById(card);

        cardService.appendLog(card, ChangeType.CONSUME, amount.negate(), after,
                order.getSerialNo(), request.getOperator());
        log.info("卡支付成功 serial={} card={} amount={} balance={}",
                order.getSerialNo(), cardNo, amount, after);
    }

    @Override
    public void refund(ChargeOrder order, String operator) {
        Card card = cardMapper.selectByCardNoForUpdate(order.getCardNo());
        if (card == null) {
            throw new BizException(ErrorCode.CARD_NOT_FOUND, "退费失败，原卡不存在");
        }
        BigDecimal amount = order.getPayAmount();
        BigDecimal after = card.getBalance().add(amount);
        card.setBalance(after);
        card.setUpdatedAt(LocalDateTime.now());
        cardMapper.updateById(card);

        cardService.appendLog(card, ChangeType.REFUND, amount, after,
                order.getSerialNo(), operator);
        log.info("原路退费成功 serial={} card={} amount={} balance={}",
                order.getSerialNo(), card.getCardNo(), amount, after);
    }

    private Card lockAndValidate(String cardNo, CardType expected) {
        Card card = cardMapper.selectByCardNoForUpdate(cardNo);
        if (card == null) {
            throw new BizException(ErrorCode.CARD_NOT_FOUND, "卡号不存在: " + cardNo);
        }
        if (card.getStatus() != null && card.getStatus() == 1) {
            throw new BizException(ErrorCode.CARD_DISABLED, "卡片已作废");
        }
        if (!Integer.valueOf(expected.getCode()).equals(card.getCardType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "支付方式与卡类型不匹配");
        }
        return card;
    }
}
