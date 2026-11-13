package com.klay.charge.controller;

import com.klay.charge.common.Result;
import com.klay.charge.service.FinanceReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final FinanceReportService reportService;

    /** 分科目报表：预付卡(预收/负债) 与 健康卡(应收/资产) 严禁合并。 */
    @GetMapping("/finance")
    public Result<Map<String, Object>> finance() {
        return Result.ok(reportService.report());
    }
}
