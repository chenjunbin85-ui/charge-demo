package com.klay.charge.controller;

import com.klay.charge.common.Result;
import com.klay.charge.entity.CardChangeLog;
import com.klay.charge.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    /** 对账清单：按卡号/时段可查的待结算核销流水。 */
    @GetMapping("/pending")
    public Result<List<CardChangeLog>> pending(@RequestParam(required = false) String cardNo) {
        return Result.ok(settlementService.pending(cardNo));
    }

    /** 结算回款：待结算 -> 已结算，冲减应收账款。 */
    @PostMapping
    public Result<Map<String, Object>> settle(@RequestParam(required = false) String cardNo,
                                              @RequestParam(defaultValue = "finance") String operator) {
        return Result.ok(settlementService.settle(cardNo, operator));
    }
}
