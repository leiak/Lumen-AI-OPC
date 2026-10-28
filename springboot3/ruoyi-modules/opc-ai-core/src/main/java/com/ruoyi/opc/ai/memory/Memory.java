package com.ruoyi.opc.ai.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.opc.ai.gateway.llm.ChatMessage;
import com.ruoyi.opc.ai.gateway.llm.EmbeddingGateway;
import com.ruoyi.opc.ai.gateway.llm.EmbeddingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 记忆系统（短期 + 长期）
 * 短期：Redis 存当前会话上下文
 * 长期：Qdrant 向量库存历史经验（向量检索）
 *
 * <p><b>这块以前是不记事的</b>：
 * <ul>
 *   <li>{@code loadShortTerm()} 永远返回空列表 —— 注释写着"简化：实际应使用 LRANGE"</li>
 *   <li>{@code embed()} 返回 {@code Math.random()} 填的 1536 维"向量" —— 写进 Qdrant 的
 *       是噪声，检索出来的也是噪声</li>
 *   <li>{@code writeLongTerm} / {@code searchLongTerm} 全仓零调用方</li>
 * </ul>
 * 结果是多轮对话没有上下文，长期记忆检索永远返回空 —— 而这一切没有任何报错。
 *
 * <p>现在三条路都是真的：短期记忆真的从 Redis 读回来，长期记忆真的走 embedding +
 * Qdrant。没有可用的 embedding provider 时**明说没有**，不拿随机数凑数。
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Memory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final com.ruoyi.common.redis.service.RedisService redisService;
    private final QdrantVectorStore vectorStore;
    private final EmbeddingGateway embeddingGateway;

    @Value("${opc.memory.short-term-ttl:86400}")
    private long shortTermTtl;

    @Value("${opc.memory.long-term-collection:opc_memory}")
    private String longTermCollection;

    /** 长期记忆集合的向量维度（要与 embedding 模型的实际维度一致）。 */
    @Value("${opc.memory.vector-dimension:1536}")
    private int vectorDimension;

    /**
     * 启动时把长期记忆集合准备好。
     *
     * <p>不这么做的话，集合要等第一次写入才出现 —— 而"向量库到底通不通"
     * 就成了只能靠日志猜的事。现在启动就建好（或者读出真实维度），
     * 运维随时能查得到。
     *
     * <p>Qdrant 还没起来不能拖垮整个服务 —— 记忆是软依赖，失败只记 WARN。
     */
    @jakarta.annotation.PostConstruct
    public void initLongTermCollection() {
        try {
            long dim = vectorStore.ensureCollection(longTermCollection, vectorDimension);
            log.info("长期记忆集合就绪 collection={} 维度={}", longTermCollection, dim);
        } catch (Exception e) {
            log.warn("长期记忆集合初始化失败（Qdrant 可能还没就绪）collection={}: {}",
                    longTermCollection, e.getMessage());
        }
    }

    /**
     * 追加短期记忆
     */
    public void appendShortTerm(String sessionId, ChatMessage msg) {
        if (sessionId == null || msg == null) return;
        String key = shortTermKey(sessionId);
        try {
            String json = MAPPER.writeValueAsString(msg);
            redisService.redisTemplate.opsForList().rightPush(key, json);
            redisService.expire(key, shortTermTtl);
        } catch (Exception e) {
            log.warn("短期记忆追加失败 sessionId={}", sessionId, e);
        }
    }

    /**
     * 加载短期记忆（最近 limit 条）。
     *
     * <p>Redis list 是右追加的，最近的对话在尾部 —— 所以取 {@code LRANGE key -limit -1}，
     * 不是从头取。
     */
    public List<ChatMessage> loadShortTerm(String sessionId, int limit) {
        if (sessionId == null || limit <= 0) return Collections.emptyList();
        try {
            String key = shortTermKey(sessionId);
            List<?> raw = redisService.redisTemplate.opsForList().range(key, -limit, -1);
            if (raw == null || raw.isEmpty()) return Collections.emptyList();
            List<ChatMessage> out = new ArrayList<>(raw.size());
            for (Object item : raw) {
                ChatMessage m = parseMessage(item);
                if (m != null) out.add(m);
            }
            return out;
        } catch (Exception e) {
            log.warn("短期记忆加载失败 sessionId={}", sessionId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 写入长期记忆（向量化）
     *
     * @return 是否真的写进去了（false = 没写，原因见日志）
     */
    public boolean writeLongTerm(String sessionId, String companyId, String content,
                                  Map<String, Object> metadata) {
        if (content == null || content.isBlank()) return false;
        if (!embeddingGateway.available4Memory()) {
            log.error("长期记忆未写入：没有可用的 embedding provider —— "
                    + "往 Qdrant 写随机向量等于把噪声存进向量库并假装成功");
            return false;
        }
        try {
            float[] vector = embeddingGateway.embed(content, EmbeddingProvider.EmbedType.DOCUMENT);
            if (vector == null || vector.length == 0) {
                log.error("长期记忆未写入：embedding 返回了空向量");
                return false;
            }
            // id 里必须带随机量：Qdrant 的 point id 是对业务 id 的**确定性** UUID 映射，
            // 同一会话同一毫秒内的两次写入会得到同一个 point id —— 第二次 upsert
            // 直接覆盖第一次，记忆静默丢失，而且日志里什么都看不出来。
            String id = (sessionId == null ? "anon" : sessionId)
                    + "-" + System.currentTimeMillis()
                    + "-" + UUID.randomUUID();
            vectorStore.upsert(longTermCollection, id, vector,
                    payload(companyId, sessionId, content, metadata));
            log.info("长期记忆已写入 collection={} id={} dim={}", longTermCollection, id, vector.length);
            return true;
        } catch (Exception e) {
            log.error("长期记忆写入失败 collection={}: {}", longTermCollection, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 检索长期记忆
     *
     * <p><b>租户隔离</b>：companyId 为空时直接返回空 —— 少了过滤条件就等于
     * 跨租户检索别人的记忆。
     */
    public List<String> searchLongTerm(String companyId, String query, int topK) {
        if (companyId == null || companyId.isBlank()) {
            log.warn("长期记忆检索跳过：缺少 companyId（少了租户过滤就是跨租户检索）");
            return Collections.emptyList();
        }
        if (query == null || query.isBlank() || topK <= 0) return Collections.emptyList();
        if (!embeddingGateway.available4Memory()) {
            log.warn("长期记忆检索跳过：没有可用的 embedding provider");
            return Collections.emptyList();
        }
        try {
            float[] vector = embeddingGateway.embed(query, EmbeddingProvider.EmbedType.QUERY);
            List<String> hits = vectorStore.search(longTermCollection, vector, topK, "company_id", companyId);
            // 契约统一：store 侧返回 null 时也交出空列表，不把 null 递给调用方
            return hits == null ? Collections.emptyList() : hits;
        } catch (Exception e) {
            log.error("长期记忆检索失败 collection={}: {}", longTermCollection, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 检索长期记忆（带分数）。
     */
    public List<QdrantVectorStore.MemoryHit> searchLongTermHits(String companyId, String query, int topK) {
        if (companyId == null || companyId.isBlank() || query == null || query.isBlank() || topK <= 0) {
            return Collections.emptyList();
        }
        if (!embeddingGateway.available4Memory()) {
            return Collections.emptyList();
        }
        try {
            float[] vector = embeddingGateway.embed(query, EmbeddingProvider.EmbedType.QUERY);
            return vectorStore.searchHits(longTermCollection, vector, topK, "company_id", companyId);
        } catch (Exception e) {
            log.error("长期记忆检索失败 collection={}: {}", longTermCollection, e.getMessage(), e);
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

    /**
     * 长期记忆集合名（运维/自检用）。
     */
    public String longTermCollection() {
        return longTermCollection;
    }

    /**
     * 期望的向量维度（自检用）。
     */
    public int vectorDimension() {
        return vectorDimension;
    }

    /**
     * 记忆系统自检：短期 + 长期 + 向量库，到底哪一环是通的。
     *
     * <p><b>为什么不能只查「集合在不在」</b>：集合是启动时
     * {@link #initLongTermCollection()} 建的，跟 embedding 通不通没关系 ——
     * provider 一个都不可用的时候它照样在，看板上还是一片绿。
     * 所以这里做的是**真的往返**：
     * <ol>
     *   <li>向量化一句探针文本</li>
     *   <li>写进 Qdrant</li>
     *   <li>按 point_key 把它搜回来</li>
     *   <li>删掉探针（不留垃圾）</li>
     * </ol>
     * 任何一环不通，对应的字段就是 false —— **不抛异常**：自检接口本身
     * 不该挂掉，它要的就是把「不通」这件事如实报出来。
     *
     * @return 自检结果
     */
    public Map<String, Object> selfCheck() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("collection", longTermCollection);
        out.put("expectedDimension", vectorDimension);
        out.put("embeddingProvider", embeddingGateway.primaryName());
        out.put("embeddingAvailable", embeddingGateway.available4Memory());

        // ---- 短期记忆：Redis 里到底有没有会话 ----
        try {
            out.put("shortTermSessions", countShortTermSessions());
        } catch (Exception e) {
            out.put("shortTermSessions", -1);
            out.put("shortTermError", e.getMessage());
        }

        // ---- 长期记忆：集合在不在、维度多少 ----
        boolean exists = false;
        long dim = -1;
        try {
            exists = vectorStore.collectionExists(longTermCollection);
            dim = vectorStore.collectionDimension(longTermCollection);
        } catch (Exception e) {
            out.put("qdrantError", e.getMessage());
        }
        out.put("collectionExists", exists);
        out.put("collectionDimension", dim);

        // ---- 真的往返一次 ----
        boolean roundTrip = false;
        String probeId = "selftest-" + UUID.randomUUID();
        try {
            if (embeddingGateway.available4Memory() && exists) {
                String text = "记忆自检探针 " + probeId;
                float[] vector = embeddingGateway.embed(text, EmbeddingProvider.EmbedType.DOCUMENT);
                out.put("embeddingDimension", vector == null ? 0 : vector.length);
                vectorStore.upsert(longTermCollection, probeId, vector,
                        payload("selftest", "selftest", text, Map.of("probe", true)));
                List<QdrantVectorStore.MemoryHit> hits =
                        vectorStore.searchHits(longTermCollection, vector, 5, "point_key", probeId);
                roundTrip = hits.stream().anyMatch(h -> probeId.equals(h.id()));
            }
        } catch (Exception e) {
            out.put("roundTripError", e.getMessage());
        } finally {
            // 探针不留下来 —— 它不是记忆，只是用来证明「写得进去也读得回来」
            try {
                vectorStore.delete(longTermCollection, probeId);
            } catch (Exception ignored) {
                // 删不掉不该让自检失败
            }
        }
        out.put("roundTrip", roundTrip);
        return out;
    }

    /**
     * Redis 里有多少个会话的短期记忆。
     *
     * <p>用 SCAN 而不是 KEYS —— KEYS 在大库上是阻塞式的。
     */
    private int countShortTermSessions() {
        int count = 0;
        try (Cursor<String> cursor = redisService.redisTemplate.scan(
                ScanOptions.scanOptions().match(shortTermKey("*")).count(200).build())) {
            while (cursor.hasNext()) {
                cursor.next();
                count++;
            }
        }
        return count;
    }

    private String shortTermKey(String sessionId) {
        return "opc:memory:short:" + sessionId;
    }

    /**
     * 把 Redis 里取出来的元素还原成 {@link ChatMessage}。
     *
     * <p>Redis 的 value 序列化器是 FastJson2（带类型信息），同一条 list 元素
     * 可能是 JSON 字符串、也可能是 Map —— 两种都得认。
     */
    @SuppressWarnings("unchecked")
    private ChatMessage parseMessage(Object raw) {
        if (raw == null) return null;
        try {
            if (raw instanceof ChatMessage m) return m;
            if (raw instanceof String s) {
                if (s.isBlank()) return null;
                return MAPPER.readValue(s, ChatMessage.class);
            }
            if (raw instanceof Map<?, ?> map) {
                Object nested = map.get("value");
                if (nested instanceof String s && !s.isBlank() && !map.containsKey("role")) {
                    return MAPPER.readValue(s, ChatMessage.class);
                }
                return MAPPER.convertValue(map, ChatMessage.class);
            }
            return null;
        } catch (Exception e) {
            log.warn("短期记忆反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> payload(String companyId, String sessionId, String content,
                                        Map<String, Object> metadata) {
        Map<String, Object> m = new HashMap<>();
        // metadata 先铺，保留字段后写 —— 顺序反了的话，调用方传一个
        // company_id 就能把这条记忆写到别的租户名下（自己检索不到，
        // 别人却检索得到）。保留字段不归调用方管。
        if (metadata != null) m.putAll(metadata);
        m.put("company_id", companyId);
        m.put("session_id", sessionId);
        m.put("content", content);
        m.put("ts", System.currentTimeMillis());
        return m;
    }

}
