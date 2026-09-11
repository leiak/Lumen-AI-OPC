package com.ruoyi.opc.hr.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HrApplicationStatus {
    NEW("NEW", "新投递"),
    SCREENING("SCREENING", "筛选中"),
    INTERVIEW("INTERVIEW", "面试中"),
    OFFER("OFFER", "已发 Offer"),
    HIRED("HIRED", "已入职"),
    REJECTED("REJECTED", "已拒绝");

    private final String code;
    private final String desc;

    public static HrApplicationStatus of(String code) {
        for (HrApplicationStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("Unknown HrApplicationStatus: " + code);
    }

    /**
     * 合法状态转换:
     * NEW → SCREENING/REJECTED
     * SCREENING → INTERVIEW/REJECTED
     * INTERVIEW → OFFER/REJECTED
     * OFFER → HIRED/REJECTED
     * HIRED → 终态
     * REJECTED → 终态
     */
    public boolean canTransitionTo(HrApplicationStatus next) {
        if (this == next) return false;
        if (next == REJECTED) return this != HIRED && this != REJECTED;
        switch (this) {
            case NEW:        return next == SCREENING;
            case SCREENING:  return next == INTERVIEW;
            case INTERVIEW:  return next == OFFER;
            case OFFER:      return next == HIRED;
            default:         return false;
        }
    }
}
