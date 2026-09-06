package com.ruoyi.opc.ai.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 敏感词过滤
 *
 * @author OAC
 */
@Slf4j
@Component
public class SensitiveWordFilter {

    private static final Set<String> WORDS = new HashSet<>(Arrays.asList(
            "违法", "黄赌毒", "枪支", "爆炸物", "毒品",
            "法轮功", "反动", "分裂国家", "恐怖袭击"
    ));

    public String filter(String input) {
        if (input == null || input.isEmpty()) return input;
        String result = input;
        for (String w : WORDS) {
            if (result.contains(w)) {
                log.warn("[SensitiveWord] 命中敏感词：{}", w);
                result = result.replace(w, "***");
            }
        }
        return result;
    }

    public boolean contains(String input) {
        if (input == null) return false;
        for (String w : WORDS) {
            if (input.contains(w)) return true;
        }
        return false;
    }

}
