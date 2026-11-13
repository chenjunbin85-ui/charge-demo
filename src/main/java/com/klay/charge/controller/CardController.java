package com.klay.charge.controller;

import com.klay.charge.common.Result;
import com.klay.charge.dto.Requests;
import com.klay.charge.entity.Card;
import com.klay.charge.entity.CardChangeLog;
import com.klay.charge.service.CardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    @PostMapping
    public Result<Card> open(@Valid @RequestBody Requests.CardRequest request,
                             @RequestParam(defaultValue = "system") String operator) {
        return Result.ok(cardService.open(request, operator));
    }

    @PostMapping("/{cardNo}/recharge")
    public Result<Card> recharge(@PathVariable String cardNo,
                                 @Valid @RequestBody Requests.RechargeRequest request) {
        return Result.ok(cardService.recharge(cardNo, request));
    }

    @PostMapping("/{cardNo}/void")
    public Result<Card> voidCard(@PathVariable String cardNo,
                                 @RequestParam String operator,
                                 @RequestParam String role) {
        return Result.ok(cardService.voidCard(cardNo, operator, role));
    }

    /** 使用记录：新建 / 充值 / 消费 / 退费 / 作废全量流水，可按关联收费流水号追溯。 */
    @GetMapping("/{cardNo}/logs")
    public Result<List<CardChangeLog>> logs(@PathVariable String cardNo) {
        return Result.ok(cardService.logs(cardNo));
    }
}
