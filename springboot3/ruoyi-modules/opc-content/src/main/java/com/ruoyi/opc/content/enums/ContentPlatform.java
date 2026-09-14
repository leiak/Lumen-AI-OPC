package com.ruoyi.opc.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内容发布平台 — 当前仅抖音开放平台,预留其他平台位
 */
@Getter
@AllArgsConstructor
public enum ContentPlatform {

    DOUYIN("DOUYIN", "抖音开放平台");

    private final String code;
    private final String desc;

    public static ContentPlatform of(String code) {
        if (code == null) {
            throw new IllegalArgumentException("平台代码不能为空");
        }
        for (ContentPlatform p : values()) {
            if (p.code.equals(code)) return p;
        }
        throw new IllegalArgumentException("Unknown ContentPlatform: " + code);
    }
}