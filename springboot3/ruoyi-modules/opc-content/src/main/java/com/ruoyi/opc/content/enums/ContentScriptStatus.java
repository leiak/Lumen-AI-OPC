package com.ruoyi.opc.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

/**
 * 脚本状态机 — DRAFT → READY → PUBLISHED/FAILED → 回滚 DRAFT / 重试 READY
 *
 * 允许转换:
 * - DRAFT    → READY / DELETED
 * - READY    → PUBLISHED / FAILED / DRAFT (回滚)
 * - PUBLISHED → DRAFT (重新编辑)
 * - FAILED   → DRAFT (重新编辑) / READY (重试直接回到就绪,见 spec §3.5)
 * - DELETED  → 终态
 */
@Getter
@AllArgsConstructor
public enum ContentScriptStatus {

    DRAFT("DRAFT", "草稿"),
    READY("READY", "就绪"),
    PUBLISHED("PUBLISHED", "已发布"),
    FAILED("FAILED", "失败"),
    DELETED("DELETED", "已删除");

    private final String code;
    private final String desc;

    private static final Set<ContentScriptStatus> FROM_DRAFT     = Set.of(READY, DELETED);
    private static final Set<ContentScriptStatus> FROM_READY     = Set.of(PUBLISHED, FAILED, DRAFT);
    private static final Set<ContentScriptStatus> FROM_PUBLISHED = Set.of(DRAFT);
    private static final Set<ContentScriptStatus> FROM_FAILED    = Set.of(DRAFT, READY);
    private static final Set<ContentScriptStatus> FROM_DELETED   = Set.of();

    /**
     * 是否可转换到目标状态 (同状态返回 false)
     */
    public boolean canTransitionTo(ContentScriptStatus target) {
        if (target == null || target == this) return false;
        return switch (this) {
            case DRAFT     -> FROM_DRAFT.contains(target);
            case READY     -> FROM_READY.contains(target);
            case PUBLISHED -> FROM_PUBLISHED.contains(target);
            case FAILED    -> FROM_FAILED.contains(target);
            case DELETED   -> FROM_DELETED.contains(target);
        };
    }

    public boolean isTerminal() {
        return this == DELETED;
    }

    public static ContentScriptStatus of(String code) {
        if (code == null) {
            throw new IllegalArgumentException("脚本状态不能为空");
        }
        for (ContentScriptStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("Unknown ContentScriptStatus: " + code);
    }
}