package com.klay.charge.enums;

import lombok.Getter;

/** 业务规则：两类卡资金归属与科目方向完全相反，严禁合并入同一科目。 */
@Getter
public enum FinanceSubject {
    PREPAID_LIABILITY("预收账款", "负债", "自营储值，钱在自己账上，我欠客户服务"),
    EHC_RECEIVABLE("应收账款", "资产", "第三方代收，钱在开卡公司账上，对方欠我回款");

    private final String subjectName;
    private final String direction;
    private final String note;

    FinanceSubject(String subjectName, String direction, String note) {
        this.subjectName = subjectName;
        this.direction = direction;
        this.note = note;
    }
}
