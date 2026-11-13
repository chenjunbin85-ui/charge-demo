package com.klay.charge.controller;

import com.klay.charge.common.Result;
import com.klay.charge.dto.Requests;
import com.klay.charge.entity.ChargeOrder;
import com.klay.charge.service.ChargeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class ChargeController {

    private final ChargeService chargeService;

    /** 人员登记：生成唯一流水号，状态「未收费」，金额由后端计算。 */
    @PostMapping
    public Result<ChargeOrder> register(@Valid @RequestBody Requests.RegisterRequest request) {
        return Result.ok(chargeService.register(request));
    }

    /** 收费：4 种支付方式，事务内行锁 + 状态机幂等。 */
    @PostMapping("/{serialNo}/charge")
    public Result<ChargeOrder> charge(@PathVariable String serialNo,
                                      @Valid @RequestBody Requests.ChargeRequest request) {
        return Result.ok(chargeService.charge(serialNo, request));
    }

    /** 退费：仅管理员、仅已收费、原路退回、操作留痕。 */
    @PostMapping("/{serialNo}/refund")
    public Result<ChargeOrder> refund(@PathVariable String serialNo,
                                      @Valid @RequestBody Requests.RefundRequest request) {
        return Result.ok(chargeService.refund(serialNo, request));
    }

    @GetMapping("/{serialNo}")
    public Result<ChargeOrder> get(@PathVariable String serialNo) {
        return Result.ok(chargeService.get(serialNo));
    }
}
