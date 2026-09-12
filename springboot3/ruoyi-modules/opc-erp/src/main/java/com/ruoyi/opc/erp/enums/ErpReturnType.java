package com.ruoyi.opc.erp.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErpReturnType {
    SALES_RETURN("SALES_RETURN", "销退"),
    SUPPLIER_RETURN("SUPPLIER_RETURN", "采退");

    private final String code;
    private final String desc;

    @JsonCreator
    public static ErpReturnType of(String code) {
        for (ErpReturnType v : values()) {
            if (v.code.equals(code)) return v;
        }
        throw new IllegalArgumentException("Unknown ErpReturnType: " + code);
    }
}
