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

    /**
     * 生成缓存 key（<b>按租户隔离</b>）。
     *
     * <p><b>为什么 key 里必须有 companyId</b>：缓存复用的是「同样的 prompt → 同样的回答」，
     * 但<b>成本是分开算的</b> —— 谁触发的 provider 调用，谁付钱。
     * 以前 key 只由 messages+options 决定，等于全平台共用一个缓存池：
     * A 公司付的钱让 B 公司白嫖（缓存命中 cost 记 0），
     * 而且 B 的用量数字会取决于 A 当天跑了多少次 —— 账单就没法看了。
     * 把 companyId 放进 key，缓存只在<b>同一家公司内部</b>复用。</p>
     *
     * <p>companyId 为 null（没有归属可判）时退回共享命名空间 —— 宁可共享，
     * 也不能因为算不出租户就把缓存整个关掉。</p>
     *
     * @param companyId 租户；null 表示本次调用没有归属
     */
    public String buildKey(List<ChatMessage> messages, ChatModelProvider.ChatOptions options, Long companyId) {
        try {
            String json = objectMapper.writeValueAsString(messages)
                    + "|" + (options == null ? "" : objectMapper.writeValueAsString(options))
                    + "|company=" + (companyId == null ? "-" : companyId);
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
