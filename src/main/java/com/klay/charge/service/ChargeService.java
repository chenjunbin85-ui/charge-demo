package com.klay.charge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.klay.charge.common.BizException;
import com.klay.charge.common.ErrorCode;
import com.klay.charge.dto.Requests;
import com.klay.charge.entity.ChargeOrder;
import com.klay.charge.enums.ChargeStatus;
import com.klay.charge.enums.OperatorRole;
import com.klay.charge.enums.PayMethod;
import com.klay.charge.mapper.ChargeOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/** 收费结算核心：状态机 + 行锁串行化 + 幂等 + 事务边界 + 支付方式策略。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeService {

    private final ChargeOrderMapper orderMapper;
    private final PaymentStrategyFactory strategyFactory;
    private final AtomicInteger sequence = new AtomicInteger(0);

    @Transactional(rollbackFor = Exception.class)
    public ChargeOrder register(Requests.RegisterRequest request) {
        BigDecimal discount = request.getDiscount() == null
                ? BigDecimal.valueOf(100) : request.getDiscount();
        // 折后需付金额由后端计算，向下保留 2 位；接口不接收该字段。
        BigDecimal payable = request.getTotalAmount()
                .multiply(discount)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);

        ChargeOrder order = new ChargeOrder();
        order.setSerialNo(nextSerialNo());
        order.setCustomerName(request.getCustomerName());
        order.setTotalAmount(request.getTotalAmount());
        order.setDiscount(discount);
        order.setPayAmount(payable);
        order.setChargeStatus(ChargeStatus.UNPAID.getCode());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.insert(order);
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public ChargeOrder charge(String serialNo, Requests.ChargeRequest request) {
        // 1. 事务内行锁：同一流水号并发收费排队，第二个事务看到 PAID 后被幂等拦截。
        ChargeOrder order = orderMapper.selectBySerialForUpdate(serialNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "收费记录不存在: " + serialNo);
        }
        ChargeStatus status = ChargeStatus.of(order.getChargeStatus());
        if (!status.canCharge()) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION,
                    "该记录已是「" + status.getLabel() + "」，不可重复收费");
        }
        PayMethod method = PayMethod.of(request.getPayMethod());
        OperatorRole.of(request.getRole());

        // 2. 策略执行（卡扣款 + 流水），异常时整个事务回滚，状态保持未收费。
        strategyFactory.get(method).pay(order, request);

        // 3. 状态流转 + 操作留痕。
        order.setChargeStatus(ChargeStatus.PAID.getCode());
        order.setPayMethod(method.getCode());
        order.setCardNo(request.getCardNo());
        order.setOperator(request.getOperator());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public ChargeOrder refund(String serialNo, Requests.RefundRequest request) {
        OperatorRole.of(request.getRole()).requireAdmin();
        ChargeOrder order = orderMapper.selectBySerialForUpdate(serialNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "收费记录不存在: " + serialNo);
        }
        ChargeStatus status = ChargeStatus.of(order.getChargeStatus());
        if (!status.canRefund()) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION,
                    "仅「已收费」可退费，当前状态「" + status.getLabel() + "」");
        }
        PayMethod method = PayMethod.of(order.getPayMethod());
        strategyFactory.get(method).refund(order, request.getOperator());

        order.setChargeStatus(ChargeStatus.REFUNDED.getCode());
        order.setRemark(request.getRemark());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return order;
    }

    public ChargeOrder get(String serialNo) {
        ChargeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getSerialNo, serialNo));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "收费记录不存在: " + serialNo);
        }
        return order;
    }

    /** demo 用进程内序列；生产请换数据库序列 / 雪花 ID，保证多实例全局唯一。 */
    private String nextSerialNo() {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return String.format("CH%s%04d", day, sequence.incrementAndGet());
    }
}
