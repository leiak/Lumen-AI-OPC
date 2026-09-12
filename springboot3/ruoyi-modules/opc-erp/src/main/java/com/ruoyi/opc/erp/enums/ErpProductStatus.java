package com.ruoyi.opc.erp.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErpProductStatus {
    ACTIVE("ACTIVE", "在售"),
    INACTIVE("INACTIVE", "下架");

    private final String code;
    private final String desc;

    @JsonCreator
    public static ErpProductStatus of(String code) {
        for (ErpProductStatus v : values()) {
            if (v.code.equals(code)) return v;
        }
        throw new IllegalArgumentException("Unknown ErpProductStatus: " + code);
    }
}
