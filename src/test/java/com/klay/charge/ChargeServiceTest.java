package com.klay.charge;

import com.klay.charge.common.BizException;
import com.klay.charge.dto.Requests;
import com.klay.charge.entity.CardChangeLog;
import com.klay.charge.entity.ChargeOrder;
import com.klay.charge.enums.ChargeStatus;
import com.klay.charge.mapper.CardChangeLogMapper;
import com.klay.charge.mapper.CardMapper;
import com.klay.charge.mapper.ChargeOrderMapper;
import com.klay.charge.service.CardService;
import com.klay.charge.service.ChargeService;
import com.klay.charge.service.FinanceReportService;
import com.klay.charge.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** 双卡完整闭环 + 收费结算业务场景 + 并发/幂等回归。 */
@SpringBootTest
class ChargeServiceTest {

    @Autowired private ChargeService chargeService;
    @Autowired private CardService cardService;
    @Autowired private CardMapper cardMapper;
    @Autowired private CardChangeLogMapper logMapper;
    @Autowired private ChargeOrderMapper orderMapper;
    @Autowired private FinanceReportService reportService;
    @Autowired private SettlementService settlementService;

    /** H2 是同一个内存库，报表按全库聚合；每个用例前清库，避免用例间数据串扰。 */
    @BeforeEach
    void resetDatabase() {
        logMapper.delete(null);
        cardMapper.delete(null);
        orderMapper.delete(null);
    }

    private String newCard(int type, String balance) {
        Requests.CardRequest r = new Requests.CardRequest();
        r.setCardNo("T" + UUID.randomUUID().toString().substring(0, 8));
        r.setCardType(type);
        r.setInitialBalance(new BigDecimal(balance));
        cardService.open(r, "tester");
        return r.getCardNo();
    }

    private void recharge(String cardNo, String amount) {
        Requests.RechargeRequest r = new Requests.RechargeRequest();
        r.setAmount(new BigDecimal(amount));
        r.setOperator("cashier01");
        cardService.recharge(cardNo, r);
    }

    private ChargeOrder newOrder(String amount) {
        return newOrder(amount, "100");
    }

    private ChargeOrder newOrder(String amount, String discount) {
        Requests.RegisterRequest r = new Requests.RegisterRequest();
        r.setCustomerName("测试客户");
        r.setTotalAmount(new BigDecimal(amount));
        r.setDiscount(new BigDecimal(discount));
        return chargeService.register(r);
    }

    private void charge(String serialNo, int payMethod, String cardNo) {
        Requests.ChargeRequest r = new Requests.ChargeRequest();
        r.setPayMethod(payMethod);
        r.setCardNo(cardNo);
        r.setOperator("cashier01");
        r.setRole("CASHIER");
        chargeService.charge(serialNo, r);
    }

    private void refund(String serialNo) {
        Requests.RefundRequest r = new Requests.RefundRequest();
        r.setOperator("admin");
        r.setRole("ADMIN");
        r.setRemark("客户申请退费");
        chargeService.refund(serialNo, r);
    }

    private BigDecimal balance(String cardNo) {
        return cardMapper.selectByCardNoForUpdate(cardNo).getBalance();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> report() {
        return reportService.report();
    }

    @SuppressWarnings("unchecked")
    private BigDecimal subjectBalance(String subject) {
        return (BigDecimal) ((Map<String, Object>) report().get(subject)).get("subjectBalance");
    }

    /** 完整业务闭环：开卡 -> 充值 -> 收费(消费/核销) -> 分科目报表 -> 对账结算 -> 退费 -> 复看报表。 */
    @Test
    void doubleCardFullBusinessLoop() {
        String prepaid = newCard(1, "200");
        String health = newCard(2, "500");
        recharge(prepaid, "50");

        ChargeOrder o1 = newOrder("28");
        charge(o1.getSerialNo(), 1, prepaid);
        ChargeOrder o2 = newOrder("60");
        charge(o2.getSerialNo(), 2, health);

        assertEquals(0, new BigDecimal("222").compareTo(balance(prepaid)));  // 200+50-28
        assertEquals(0, new BigDecimal("440").compareTo(balance(health)));   // 500-60

        Map<String, Object> report = report();
        assertEquals(0, new BigDecimal("222").compareTo(subjectBalance("PREPAID_LIABILITY")));
        assertEquals(0, new BigDecimal("60").compareTo(subjectBalance("EHC_RECEIVABLE")));
        assertEquals(0, new BigDecimal("60").compareTo((BigDecimal) report.get("pendingSettlement")));
        assertNotEquals(direction("PREPAID_LIABILITY"), direction("EHC_RECEIVABLE"));

        // 健康卡对账结算：应收冲减归零，待结算归零，重复结算被幂等拦截
        Map<String, Object> settlement = settlementService.settle(health, "finance");
        assertEquals(0, new BigDecimal("60").compareTo((BigDecimal) settlement.get("settledAmount")));
        assertEquals(0, BigDecimal.ZERO.compareTo(subjectBalance("EHC_RECEIVABLE")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) report().get("pendingSettlement")));
        assertThrows(BizException.class, () -> settlementService.settle(health, "finance"));

        // 预付卡退费：卡余额与负债同向恢复
        refund(o1.getSerialNo());
        assertEquals(0, new BigDecimal("250").compareTo(balance(prepaid)));
        assertEquals(0, new BigDecimal("250").compareTo(subjectBalance("PREPAID_LIABILITY")));
        assertEquals(ChargeStatus.REFUNDED.getCode(), chargeService.get(o1.getSerialNo()).getChargeStatus());

        // 新建 / 充值 / 消费 / 退费，四条流水齐全且科目方向一致
        List<CardChangeLog> prepaidLogs = cardService.logs(prepaid);
        assertEquals(4, prepaidLogs.size());
        assertTrue(prepaidLogs.stream().allMatch(l -> "PREPAID_LIABILITY".equals(l.getFinanceSubject())));
        assertEquals(1, prepaidLogs.stream().filter(l -> l.getChangeType() == 2).count());
        assertEquals(1, prepaidLogs.stream().filter(l -> l.getChangeType() == 3).count());
    }

    @SuppressWarnings("unchecked")
    private String direction(String subject) {
        return (String) ((Map<String, Object>) report().get(subject)).get("direction");
    }

    /** 健康卡核销后退费：冲减应收，结算净额为 0。 */
    @Test
    void refundBeforeSettlementOffsetsReceivable() {
        String health = newCard(2, "500");
        ChargeOrder order = newOrder("60");
        charge(order.getSerialNo(), 2, health);
        refund(order.getSerialNo());
        assertEquals(0, BigDecimal.ZERO.compareTo(subjectBalance("EHC_RECEIVABLE")));
        Map<String, Object> settlement = settlementService.settle(health, "finance");
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) settlement.get("settledAmount")));
    }

    /** 场景：余额不足拦截 + 事务回滚（卡余额与订单状态都不变）。 */
    @Test
    void insufficientBalanceRejected() {
        String cardNo = newCard(1, "10");
        ChargeOrder order = newOrder("990");
        BizException ex = assertThrows(BizException.class, () -> charge(order.getSerialNo(), 1, cardNo));
        assertTrue(ex.getMessage().contains("余额不足"));
        assertEquals(0, new BigDecimal("10").compareTo(balance(cardNo)));
        assertEquals(ChargeStatus.UNPAID.getCode(), chargeService.get(order.getSerialNo()).getChargeStatus());
    }

    /** 场景：卡已作废拦截；支付方式与卡类型不匹配拦截。 */
    @Test
    void cardValidation() {
        String voided = newCard(2, "100");
        cardService.voidCard(voided, "admin", "ADMIN");
        ChargeOrder o1 = newOrder("30");
        assertThrows(BizException.class, () -> charge(o1.getSerialNo(), 2, voided));

        String prepaid = newCard(1, "100");
        ChargeOrder o2 = newOrder("30");
        assertThrows(BizException.class, () -> charge(o2.getSerialNo(), 2, prepaid));
    }

    /** 场景：重复收费 / 重复退费幂等，卡只扣一次。 */
    @Test
    void idempotency() {
        String cardNo = newCard(1, "200");
        ChargeOrder order = newOrder("28");
        charge(order.getSerialNo(), 1, cardNo);
        assertThrows(BizException.class, () -> charge(order.getSerialNo(), 1, cardNo));
        assertEquals(0, new BigDecimal("172").compareTo(balance(cardNo)));
        refund(order.getSerialNo());
        assertThrows(BizException.class, () -> refund(order.getSerialNo()));
    }

    /** 场景：金额只读，折后需付由后端向下取 2 位。 */
    @Test
    void payableCalculatedByBackendOnly() {
        String cardNo = newCard(1, "200");
        ChargeOrder order = newOrder("100", "50");
        assertEquals(0, new BigDecimal("50.00").compareTo(order.getPayAmount()));
        charge(order.getSerialNo(), 1, cardNo);
        assertEquals(0, new BigDecimal("150").compareTo(balance(cardNo)));
    }

    /** 场景：非管理员不得退费。 */
    @Test
    void refundRequiresAdmin() {
        String cardNo = newCard(1, "200");
        ChargeOrder order = newOrder("28");
        charge(order.getSerialNo(), 1, cardNo);
        Requests.RefundRequest r = new Requests.RefundRequest();
        r.setOperator("cashier01");
        r.setRole("CASHIER");
        r.setRemark("越权尝试");
        assertThrows(BizException.class, () -> chargeService.refund(order.getSerialNo(), r));
    }

    /** 生产级：并发扣款靠行锁，余额不为负、不超扣。 */
    @Test
    void concurrentChargeNeverOverdraws() throws Exception {
        String cardNo = newCard(1, "40");
        List<ChargeOrder> orders = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            orders.add(newOrder("10"));
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (ChargeOrder order : orders) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    charge(order.getSerialNo(), 1, cardNo);
                    return true;
                } catch (BizException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int success = 0;
        for (Future<Boolean> f : futures) {
            if (f.get(20, TimeUnit.SECONDS)) {
                success++;
            }
        }
        pool.shutdownNow();
        assertEquals(4, success);
        assertEquals(0, BigDecimal.ZERO.compareTo(balance(cardNo)));
    }

    /** 示例链路：开卡 200 -> 收费 28 -> 余额 172 -> 退费恢复 200。 */
    @Test
    void prepaidCardMeasuredLoop() {
        String cardNo = newCard(1, "200");
        ChargeOrder order = newOrder("28");
        charge(order.getSerialNo(), 1, cardNo);
        assertEquals(0, new BigDecimal("172").compareTo(balance(cardNo)));
        refund(order.getSerialNo());
        assertEquals(0, new BigDecimal("200").compareTo(balance(cardNo)));

        List<CardChangeLog> logs = cardService.logs(cardNo);
        assertEquals(3, logs.size());  // 新建 / 消费 / 退费
        assertEquals(1, logs.stream().filter(l -> l.getChangeType() == 2).count());
        assertEquals(1, logs.stream().filter(l -> l.getChangeType() == 3).count());
        assertTrue(logs.stream().allMatch(l -> "PREPAID_LIABILITY".equals(l.getFinanceSubject())));
        assertTrue(logs.stream().filter(l -> l.getChangeType() == 2)
                .allMatch(l -> order.getSerialNo().equals(l.getRelateSerial())));
    }
}
