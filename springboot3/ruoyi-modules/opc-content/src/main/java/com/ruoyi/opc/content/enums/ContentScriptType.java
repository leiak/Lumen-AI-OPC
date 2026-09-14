package com.ruoyi.opc.content.enums;

import com.ruoyi.common.core.exception.ServiceException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 脚本类型 — 4 类 LLM 生成场景
 */
@Getter
@AllArgsConstructor
public enum ContentScriptType {

    DRAMA("DRAMA", "短剧脚本"),
    VIDEO("VIDEO", "视频脚本"),
    ARTICLE("ARTICLE", "图文文案"),
    ADAPTER("ADAPTER", "平台适配");

    private final String code;
    private final String desc;

    public static ContentScriptType of(String code) {
        if (code == null) {
            throw new ServiceException("脚本类型不能为空");
        }
        for (ContentScriptType t : values()) {
            if (t.code.equals(code)) return t;
        }
        throw new ServiceException("未知脚本类型: " + code);
    }
}