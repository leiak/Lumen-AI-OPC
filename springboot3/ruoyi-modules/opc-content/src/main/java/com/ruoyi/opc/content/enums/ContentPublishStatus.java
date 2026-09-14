package com.ruoyi.opc.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发布状态 — PENDING → SUCCESS / FAILED (FAILED → PENDING retry)
 */
@Getter
@AllArgsConstructor
public enum ContentPublishStatus {

    PENDING("PENDING", "等待中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String desc;

    public static ContentPublishStatus of(String code) {
        if (code == null) {
            throw new IllegalArgumentException("发布状态不能为空");
        }
        for (ContentPublishStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("Unknown ContentPublishStatus: " + code);
    }

    public boolean isTerminal() {
        return this == SUCCESS;
    }
}