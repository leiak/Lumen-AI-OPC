package com.ruoyi.opc.erp.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErpSupplierLevel {
    NORMAL("NORMAL", "普通"),
    PREFERRED("PREFERRED", "首选"),
    BLOCKED("BLOCKED", "黑名单");

    private final String code;
    private final String desc;

    @JsonCreator
    public static ErpSupplierLevel of(String code) {
        for (ErpSupplierLevel v : values()) {
            if (v.code.equals(code)) return v;
        }
        throw new IllegalArgumentException("Unknown ErpSupplierLevel: " + code);
    }
}
