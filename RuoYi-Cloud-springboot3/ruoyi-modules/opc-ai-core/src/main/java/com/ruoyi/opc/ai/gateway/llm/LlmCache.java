package com.ruoyi.opc.ai.gateway.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.opc.common.constant.OpcConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * LLM 响应缓存（基于 Redis，避免相同 prompt 重复计费）
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmCache {

    private final RedisService redisService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String buildKey(List<ChatMessage> messages, ChatModelProvider.ChatOptions options) {
        try {
            String json = objectMapper.writeValueAsString(messages)
                    + "|" + (options == null ? "" : objectMapper.writeValueAsString(options));
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return OpcConstants.REDIS_KEY_PREFIX + "llm:cache:" + sb;
        } catch (Exception e) {
            log.warn("生成 LLM 缓存 key 失败", e);
            return OpcConstants.REDIS_KEY_PREFIX + "llm:cache:fallback:" + System.nanoTime();
        }
    }

    public ChatResponse get(String key) {
        try {
            Object obj = redisService.getCacheObject(key);
            if (obj == null) return null;
            return objectMapper.readValue(obj.toString(), ChatResponse.class);
        } catch (Exception e) {
            log.warn("读取 LLM 缓存失败 key={}", key, e);
            return null;
        }
    }

    public void put(String key, ChatResponse response, double placeholderPrice) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisService.setCacheObject(key, json, 3600L, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入 LLM 缓存失败 key={}", key, e);
        }
    }

}
