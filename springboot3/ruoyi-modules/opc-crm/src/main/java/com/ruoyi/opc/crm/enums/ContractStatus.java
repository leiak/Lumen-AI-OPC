package com.ruoyi.opc.crm.enums;
public enum ContractStatus {
    DRAFT, ACTIVE, EXPIRED, TERMINATED;

    public boolean isActive() { return this == ACTIVE; }
}
