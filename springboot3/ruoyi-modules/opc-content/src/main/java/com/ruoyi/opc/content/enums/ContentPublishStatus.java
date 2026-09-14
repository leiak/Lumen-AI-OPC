package com.ruoyi.opc.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

/**
 * 发布状态 — PENDING → SUCCESS / FAILED (FAILED → PENDING retry)
 *
 * 允许转换:
 * - PENDING  → SUCCESS / FAILED
 * - SUCCESS  → 终态
 * - FAILED   → PENDING (retry)
 */
@Getter
@AllArgsConstructor
public enum ContentPublishStatus {

    PENDING("PENDING", "等待中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String desc;

    private static final Set<ContentPublishStatus> FROM_PENDING = Set.of(SUCCESS, FAILED);
    private static final Set<ContentPublishStatus> FROM_SUCCESS = Set.of();
    private static final Set<ContentPublishStatus> FROM_FAILED  = Set.of(PENDING);

    /**
     * 是否可转换到目标状态 (同状态返回 false)
     */
    public boolean canTransitionTo(ContentPublishStatus target) {
        if (target == null || target == this) return false;
        return switch (this) {
            case PENDING -> FROM_PENDING.contains(target);
            case SUCCESS -> FROM_SUCCESS.contains(target);
            case FAILED  -> FROM_FAILED.contains(target);
        };
    }

    public boolean isTerminal() {
        return this == SUCCESS;
    }

    public static ContentPublishStatus of(String code) {
        if (code == null) {
            throw new IllegalArgumentException("发布状态不能为空");
        }
        for (ContentPublishStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("Unknown ContentPublishStatus: " + code);
    }
}