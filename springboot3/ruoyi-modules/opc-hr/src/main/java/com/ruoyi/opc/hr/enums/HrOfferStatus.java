package com.ruoyi.opc.hr.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HrOfferStatus {
    PENDING("PENDING", "待响应"),
    ACCEPTED("ACCEPTED", "已接受"),
    REJECTED("REJECTED", "已拒绝"),
    EXPIRED("EXPIRED", "已过期");

    private final String code;
    private final String desc;

    public static HrOfferStatus of(String code) {
        for (HrOfferStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("Unknown HrOfferStatus: " + code);
    }
}
