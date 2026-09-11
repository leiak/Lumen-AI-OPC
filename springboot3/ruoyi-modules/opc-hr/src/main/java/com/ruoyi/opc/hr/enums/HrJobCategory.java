package com.ruoyi.opc.hr.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HrJobCategory {
    TECH("TECH", "技术"),
    SALES("SALES", "销售"),
    OPERATION("OPERATION", "运营"),
    FINANCE("FINANCE", "财务"),
    MARKETING("MARKETING", "市场");

    private final String code;
    private final String desc;

    public static HrJobCategory of(String code) {
        for (HrJobCategory c : values()) {
            if (c.code.equals(code)) return c;
        }
        throw new IllegalArgumentException("Unknown HrJobCategory: " + code);
    }
}
