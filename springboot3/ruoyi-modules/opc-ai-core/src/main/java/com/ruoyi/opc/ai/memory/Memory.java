package com.ruoyi.opc.ai.memory;

import com.ruoyi.opc.ai.gateway.llm.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 记忆系统（短期 + 长期）
 * 短期：Redis 存当前会话上下文
 * 长期：Qdrant 向量库存历史经验（向量检索）
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Memory {

    private final com.ruoyi.common.redis.service.RedisService redisService;
    private final QdrantVectorStore vectorStore;

    @Value("${opc.memory.short-term-ttl:86400}")
    private long shortTermTtl;
    @Value("${opc.memory.long-term-collection:opc_memory}")
    private String longTermCollection;

    /**
     * 追加短期记忆
     */
    public void appendShortTerm(String sessionId, ChatMessage msg) {
        if (sessionId == null || msg == null) return;
        String key = shortTermKey(sessionId);
        try {
            String json = "{\"role\":\"" + msg.getRole() + "\",\"content\":\"" +
                    escape(msg.getContent()) + "\"}";
            redisService.redisTemplate.opsForList().rightPush(key, json);
            redisService.expire(key, shortTermTtl);
        } catch (Exception e) {
            log.warn("短期记忆追加失败 sessionId={}", sessionId, e);
        }
    }

    /**
     * 加载短期记忆
     */
    public List<ChatMessage> loadShortTerm(String sessionId, int limit) {
        if (sessionId == null) return Collections.emptyList();
        try {
            String key = shortTermKey(sessionId);
            // 简化：实际应使用 LRANGE
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("短期记忆加载失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 写入长期记忆（向量化）
     */
    public void writeLongTerm(String sessionId, String companyId, String content, Map<String, Object> metadata) {
        try {
            // 1. 调用 Embedding 模型生成向量
            float[] vector = embed(content);
            // 2. 写入 Qdrant
            vectorStore.upsert(longTermCollection, sessionId + "-" + System.currentTimeMillis(),
                    vector, payload(companyId, sessionId, content, metadata));
        } catch (Exception e) {
            log.error("长期记忆写入失败", e);
        }
    }

    /**
     * 检索长期记忆
     */
    public List<String> searchLongTerm(String companyId, String query, int topK) {
        try {
            float[] vector = embed(query);
            return vectorStore.search(longTermCollection, vector, topK, "company_id", companyId);
        } catch (Exception e) {
            log.error("长期记忆检索失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 清除短期记忆
     */
    public void clearShortTerm(String sessionId) {
        if (sessionId == null) return;
        try {
            redisService.deleteObject(shortTermKey(sessionId));
        } catch (Exception e) {
            log.warn("清除短期记忆失败", e);
        }
    }

    private float[] embed(String text) {
        // 真实实现：调用 Embedding API（text-embedding-3-small / bge-large-zh）
        // 这里返回固定维度占位
        float[] v = new float[1536];
        for (int i = 0; i < v.length; i++) v[i] = (float) Math.random();
        return v;
    }

    private String shortTermKey(String sessionId) {
        return "opc:memory:short:" + sessionId;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Map<String, Object> payload(String companyId, String sessionId, String content, Map<String, Object> metadata) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("company_id", companyId);
        m.put("session_id", sessionId);
        m.put("content", content);
        m.put("ts", System.currentTimeMillis());
        if (metadata != null) m.putAll(metadata);
        return m;
    }

}
