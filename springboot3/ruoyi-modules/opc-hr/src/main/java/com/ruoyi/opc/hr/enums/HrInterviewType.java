package com.ruoyi.opc.hr.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HrInterviewType {
    PHONE("PHONE", "电话"),
    VIDEO("VIDEO", "视频"),
    ONSITE("ONSITE", "现场");

    private final String code;
    private final String desc;

    public static HrInterviewType of(String code) {
        for (HrInterviewType t : values()) {
            if (t.code.equals(code)) return t;
        }
        throw new IllegalArgumentException("Unknown HrInterviewType: " + code);
    }
}
