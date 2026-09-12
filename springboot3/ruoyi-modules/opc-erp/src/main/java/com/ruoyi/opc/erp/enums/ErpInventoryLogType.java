package com.ruoyi.opc.erp.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErpInventoryLogType {
    PURCHASE_IN("PURCHASE_IN", "采购入库"),
    SALE_OUT("SALE_OUT", "销售出库"),
    SALES_RETURN_IN("SALES_RETURN_IN", "销退入库"),
    SUPPLIER_RETURN_OUT("SUPPLIER_RETURN_OUT", "采退出库"),
    ADJUST("ADJUST", "手动调整");

    private final String code;
    private final String desc;

    @JsonCreator
    public static ErpInventoryLogType of(String code) {
        for (ErpInventoryLogType v : values()) {
            if (v.code.equals(code)) return v;
        }
        throw new IllegalArgumentException("Unknown ErpInventoryLogType: " + code);
    }
}
