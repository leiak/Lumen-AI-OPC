package com.ruoyi.opc.erp.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErpSaleStatus {
    DRAFT("DRAFT", "草稿"),
    CONFIRMED("CONFIRMED", "已确认"),
    COMPLETED("COMPLETED", "已出库"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;

    @JsonCreator
    public static ErpSaleStatus of(String code) {
        for (ErpSaleStatus v : values()) {
            if (v.code.equals(code)) return v;
        }
        throw new IllegalArgumentException("Unknown ErpSaleStatus: " + code);
    }
}
