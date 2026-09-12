package com.ruoyi.opc.erp.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErpPurchaseStatus {
    DRAFT("DRAFT", "草稿"),
    CONFIRMED("CONFIRMED", "已确认"),
    COMPLETED("COMPLETED", "已入库"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;

    @JsonCreator
    public static ErpPurchaseStatus of(String code) {
        for (ErpPurchaseStatus v : values()) {
            if (v.code.equals(code)) return v;
        }
        throw new IllegalArgumentException("Unknown ErpPurchaseStatus: " + code);
    }
}
