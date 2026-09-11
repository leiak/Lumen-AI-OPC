package com.ruoyi.opc.hr.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HrJobStatus {
    DRAFT("DRAFT", "草稿"),
    OPEN("OPEN", "招聘中"),
    PAUSED("PAUSED", "暂停"),
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String desc;

    public static HrJobStatus of(String code) {
        for (HrJobStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("Unknown HrJobStatus: " + code);
    }
}
