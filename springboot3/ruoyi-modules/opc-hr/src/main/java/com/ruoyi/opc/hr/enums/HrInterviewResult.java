package com.ruoyi.opc.hr.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HrInterviewResult {
    PENDING("PENDING", "待定"),
    PASS("PASS", "通过"),
    FAIL("FAIL", "未通过");

    private final String code;
    private final String desc;

    public static HrInterviewResult of(String code) {
        for (HrInterviewResult r : values()) {
            if (r.code.equals(code)) return r;
        }
        throw new IllegalArgumentException("Unknown HrInterviewResult: " + code);
    }
}
