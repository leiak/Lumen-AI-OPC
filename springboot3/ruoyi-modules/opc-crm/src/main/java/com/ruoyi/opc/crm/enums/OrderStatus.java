package com.ruoyi.opc.crm.enums;
public enum OrderStatus {
    PENDING, PAID, SHIPPED, COMPLETED, CANCELLED;

    public boolean isFinal() { return this == COMPLETED || this == CANCELLED; }
}
