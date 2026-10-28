package com.ruoyi.opc.ai.memory;

import io.qdrant.client.ConditionFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * Qdrant 向量数据库客户端。
 *
 * <p><b>这块以前是空壳</b>：upsert / search / delete 三个方法的方法体全是注释，
 * 加上 {@code qdrant.host/port/api-key} 三个配置项写得再对也不影响任何行为
 * —— 长期记忆因此既写不进去也检索不出来，而接口照样返回"成功"。
 *
 * <p>现在是真的：
 * <ul>
 *   <li>走 Qdrant Java SDK（{@code io.qdrant:client}，仓库根 pom 已声明）</li>
 *   <li>SDK 只说 gRPC → 端口是 <b>6334</b>（6333 是给 curl 用的 REST 口）</li>
 *   <li>集合按需创建（余弦距离），维度以配置 {@code opc.memory.vector-dimension} 为准</li>
 * </ul>
 *
 * <p><b>id 的坑</b>：Qdrant 的 point id 只认 UUID 或无符号整数，
 * 而业务侧的 id 是 {@code sessionId-时间戳} 这种字符串。
 * 这里用 {@link UUID#nameUUIDFromBytes} 做**确定性**映射（同一个业务 id 永远
 * 映射到同一个 UUID），这样 upsert / delete 指向同一个点；
 * 原始 id 另外存进 payload 的 {@code point_key} 字段，检索时能拿回来。
 *
 * @author OAC
 */
@Slf4j
@Component
public class QdrantVectorStore {

    /** 容器里由 compose 注入 {@code QDRANT_HOST=qdrant}；本机直跑时默认 127.0.0.1。 */
    @Value("${qdrant.host:127.0.0.1}")
    private String host;

    /** gRPC 端口（6333 是 REST，SDK 不走那条）。 */
    @Value("${qdrant.port:6334}")
    private int port;

    @Value("${qdrant.api-key:}")
    private String apiKey;

    @Value("${qdrant.timeout-seconds:10}")
    private long timeoutSeconds;

    /** 已经确认存在、维度已知的集合，避免每次写都去问一遍服务端。 */
    private final Map<String, Long> knownDimension = new ConcurrentHashMap<>();

    private volatile QdrantClient client;

    /**
     * 写一个点。
     *
     * @param collection 集合名
     * @param id         业务 id（任意字符串，内部映射成 UUID）
     * @param vector     向量
     * @param payload    业务负载，会额外塞一个 {@code point_key} = 原始 id
     */
    public void upsert(String collection, String id, float[] vector, Map<String, Object> payload) {
        if (collection == null || id == null || vector == null || vector.length == 0) {
            throw new IllegalArgumentException("upsert 参数不完整: collection=" + collection + " id=" + id);
        }
        QdrantClient c = client();
        long dim = alignDimension(collection, vector.length);

        if (dim != vector.length) {
            // 集合维度与本次向量对不上 —— 多半是换了 embedding 模型。
            // 此时写进去只会得到一堆互相不可比的向量，直接失败比写脏数据诚实。
            // 注意这里**不会**自动重建集合（那会清空所有租户的长期记忆），
            // 要换向量空间请显式调用 recreateCollection。
            throw new IllegalStateException(
                    "Qdrant 集合 " + collection + " 的维度是 " + dim + "，收到的向量是 " + vector.length
                            + " —— 向量空间不一致，本次写入已拒绝（未重建集合，旧记忆仍在）。"
                            + "改 opc.memory.vector-dimension，或显式重建该集合后再写。");
        }
        Map<String, JsonWithInt.Value> qdrantPayload = new java.util.LinkedHashMap<>();
        if (payload != null) {
            payload.forEach((k, v) -> qdrantPayload.put(k, toQdrantValue(v)));
        }
        // 原始业务 id 留一份在 payload 里：Qdrant 那边只有 UUID，认不出的字符串
        qdrantPayload.put("point_key", ValueFactory.value(id));

        Points.PointStruct point = Points.PointStruct.newBuilder()
                .setId(Points.PointId.newBuilder().setUuid(pointId(id).toString()).build())
                .setVectors(Points.Vectors.newBuilder()
                        .setVector(Points.Vector.newBuilder().addAllData(toFloatList(vector)).build())
                        .build())
                .putAllPayload(qdrantPayload)
                .build();

        try {
            c.upsertAsync(collection, List.of(point)).get();
            log.debug("[Qdrant] upsert collection={} id={} dim={}", collection, id, vector.length);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant upsert 被中断: " + e.getMessage(), e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Qdrant upsert 失败 collection=" + collection
                    + " id=" + id + ": " + rootMessage(e), e);
        }
    }

    /**
     * 向量检索。
     *
     * @param filterField 过滤字段（可空）；为空则不过滤
     * @param filterValue 过滤值（可空）
     * @return 命中的 payload 里的 {@code content}；没有 content 时退回 {@code point_key}
     */
    public List<String> search(String collection, float[] vector, int topK, String filterField, String filterValue) {
        List<MemoryHit> hits = searchHits(collection, vector, topK, filterField, filterValue);
        List<String> out = new ArrayList<>(hits.size());
        for (MemoryHit hit : hits) {
            out.add(hit.content() != null ? hit.content() : hit.id());
        }
        return out;
    }

    /**
     * 向量检索（带分数），供需要看相似度的调用方使用。
     */
    public List<MemoryHit> searchHits(String collection, float[] vector, int topK,
                                       String filterField, String filterValue) {
        if (collection == null || vector == null || vector.length == 0) {
            return List.of();
        }
        QdrantClient c = client();

        Points.SearchPoints.Builder builder = Points.SearchPoints.newBuilder()
                .setCollectionName(collection)
                .addAllVector(toFloatList(vector))
                .setLimit(topK <= 0 ? 1 : topK)
                .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build());

        if (filterField != null && !filterField.isBlank() && filterValue != null) {
            builder.setFilter(Points.Filter.newBuilder()
                    .addMust(ConditionFactory.matchKeyword(filterField, filterValue))
                    .build());
        }

        List<Points.ScoredPoint> result;
        try {
            result = c.searchAsync(builder.build()).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (ExecutionException e) {
            // 集合还不存在是最常见的一种 —— 检索没命中而已，不该把调用方带崩
            log.warn("[Qdrant] search 失败 collection={}: {}", collection, rootMessage(e));
            return List.of();
        }

        List<MemoryHit> hits = new ArrayList<>(result.size());
        for (Points.ScoredPoint p : result) {
            String uuid = p.getId() != null && p.getId().hasUuid() ? p.getId().getUuid() : null;
            Map<String, JsonWithInt.Value> payload = p.getPayloadMap();
            String content = payload.containsKey("content") && payload.get("content").hasStringValue()
                    ? payload.get("content").getStringValue() : null;
            String key = payload.containsKey("point_key") && payload.get("point_key").hasStringValue()
                    ? payload.get("point_key").getStringValue() : uuid;
            hits.add(new MemoryHit(key, uuid, content, p.getScore()));
        }
        return hits;
    }

    /**
     * 删一个点（按业务 id）。
     */
    public void delete(String collection, String id) {
        if (collection == null || id == null) return;
        QdrantClient c = client();
        try {
            c.deleteAsync(collection, List.of(Points.PointId.newBuilder()
                    .setUuid(pointId(id).toString()).build())).get();
            log.debug("[Qdrant] delete collection={} id={}", collection, id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            log.warn("[Qdrant] delete 失败 collection={} id={}: {}", collection, id, rootMessage(e));
        }
    }

    /**
     * 集合是否已经存在（运维/自检用）。
     */
    public boolean collectionExists(String collection) {
        if (collection == null) return false;
        try {
            return Boolean.TRUE.equals(client().collectionExistsAsync(collection).get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            log.warn("[Qdrant] collectionExists 查询失败 collection={}: {}", collection, rootMessage(e));
            return false;
        }
    }

    /**
     * 集合的真实向量维度；集合不存在返回 -1。
     */
    public long collectionDimension(String collection) {
        Long cached = knownDimension.get(collection);
        if (cached != null) return cached;
        try {
            Collections.CollectionInfo info = client().getCollectionInfoAsync(collection).get();
            if (info == null || !info.hasConfig()) return -1;
            Collections.VectorsConfig vectors = info.getConfig().getParams().getVectorsConfig();
            if (vectors == null || vectors.getConfigCase() != Collections.VectorsConfig.ConfigCase.PARAMS) {
                return -1;
            }
            long size = vectors.getParams().getSize();
            knownDimension.put(collection, size);
            return size;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (ExecutionException e) {
            log.warn("[Qdrant] 读取集合维度失败 collection={}: {}", collection, rootMessage(e));
            return -1;
        }
    }

    /**
     * 确保集合存在，返回它的向量维度。
     *
     * <p>已存在时以服务端为准；不存在时按给定维度建（余弦距离）。
     */
    public long ensureCollection(String collection, long dimension) {
        Long cached = knownDimension.get(collection);
        if (cached != null) return cached;
        QdrantClient c = client();
        try {
            if (Boolean.TRUE.equals(c.collectionExistsAsync(collection).get())) {
                long actual = collectionDimension(collection);
                if (actual > 0) {
                    knownDimension.put(collection, actual);
                    return actual;
                }
                return dimension;
            }
            c.createCollectionAsync(collection, Collections.VectorParams.newBuilder()
                    .setSize((int) dimension)
                    .setDistance(Collections.Distance.Cosine)
                    .build()).get();
            knownDimension.put(collection, dimension);
            log.info("[Qdrant] 已创建集合 collection={} 维度={} 距离=Cosine", collection, dimension);
            return dimension;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant 建集合被中断: " + e.getMessage(), e);
        } catch (ExecutionException e) {
            // 并发建集合时另一个线程可能已经建好了 —— 那不是错误
            if (collectionExists(collection)) {
                long actual = collectionDimension(collection);
                if (actual > 0) {
                    knownDimension.put(collection, actual);
                    return actual;
                }
            }
            throw new IllegalStateException("Qdrant 建集合失败 collection=" + collection + ": " + rootMessage(e), e);
        }
    }

    /**
     * 把集合的维度对齐到 {@code dimension}。
     *
     * <p><b>维度对不上时绝不自动重建集合。</b>以前这里是「不一致 → 删集合 → 按新维度重建」，
     * 看起来诚实（不留噪声），实际上等于：<b>一次普通写入就能清空所有租户的长期记忆</b> ——
     * 只要把 {@code opc.memory.vector-dimension} 改错一次，或者换了个 embedding 模型，
     * 下一个写入请求就会把所有人的记忆删掉。而这一切没有任何报错，
     * 业务侧只看到「搜索结果空了」。
     *
     * <p>现在的做法是<b>如实失败</b>：维度对不上就返回集合的真实维度，
     * 由 {@link #upsert} 判定「收到的向量和集合不是一个空间」并抛异常。
     * 重建集合是<b>显式的运维动作</b>（{@link #recreateCollection}），
     * 该由人决定，不该由一条数据写入顺带触发。
     *
     * @return 集合的真实维度（对不上时就是它，不是 {@code dimension}）
     */
    public long alignDimension(String collection, long dimension) {
        long actual = collectionDimension(collection);
        if (actual < 0) {
            // 集合还不存在 —— 按本次向量的维度建
            return ensureCollection(collection, dimension);
        }
        if (actual == dimension) {
            knownDimension.put(collection, actual);
            return actual;
        }
        log.error("[Qdrant] 集合 {} 的维度是 {}，但 embedding 给出的是 {} —— 向量空间不一致，"
                        + "本次写入将被拒绝（不会自动重建集合，那会清空所有租户的长期记忆）。"
                        + "确认要换向量空间的话，请显式重建该集合",
                collection, actual, dimension);
        return actual;
    }

    /**
     * 显式重建集合（<b>会丢掉集合里的全部向量</b>）。
     *
     * <p>这是「向量空间变了，旧记忆作废」时唯一该走的路 ——
     * 由人或由运维流程明确调用，而不是被一次写入顺带触发。
     *
     * @param collection 集合名
     * @param dimension  新的向量维度
     * @return 新集合的维度
     */
    public long recreateCollection(String collection, long dimension) {
        log.warn("[Qdrant] 显式重建集合 collection={} 新维度={} —— 旧向量全部作废", collection, dimension);
        try {
            client().deleteCollectionAsync(collection).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant 删集合被中断: " + e.getMessage(), e);
        } catch (ExecutionException e) {
            // 集合本来就不存在 —— 那就当重建过了
            if (!collectionExists(collection)) {
                knownDimension.remove(collection);
                return ensureCollection(collection, dimension);
            }
            throw new IllegalStateException("Qdrant 删集合失败 collection=" + collection + ": " + rootMessage(e), e);
        }
        knownDimension.remove(collection);
        return ensureCollection(collection, dimension);
    }

    /**
     * 业务 id → Qdrant point id（确定性 UUID）。
     *
     * <p>用 nameUUIDFromBytes 而不是随机 UUID：随机的话每次 upsert 都是新点，
     * 同一条记忆会越写越多，delete 也删不掉。
     */
    private UUID pointId(String id) {
        return UUID.nameUUIDFromBytes(("opc-memory:" + id).getBytes(StandardCharsets.UTF_8));
    }

    private QdrantClient client() {
        QdrantClient local = client;
        if (local != null) return local;
        synchronized (this) {
            if (client == null) {
                QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(host, port, false)
                        .withTimeout(Duration.ofSeconds(timeoutSeconds));
                if (apiKey != null && !apiKey.isBlank()) {
                    builder.withApiKey(apiKey);
                }
                client = new QdrantClient(builder.build());
                log.info("[Qdrant] 客户端已建立 host={} port={} (gRPC) apiKey={}",
                        host, port, (apiKey != null && !apiKey.isBlank()) ? "已配置" : "未配置");
            }
            return client;
        }
    }

    private static List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }

    private static JsonWithInt.Value toQdrantValue(Object v) {
        if (v == null) return ValueFactory.nullValue();
        if (v instanceof String s) return ValueFactory.value(s);
        if (v instanceof Boolean b) return ValueFactory.value(b);
        if (v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte) {
            return ValueFactory.value(((Number) v).longValue());
        }
        if (v instanceof Number n) return ValueFactory.value(n.doubleValue());
        return ValueFactory.value(String.valueOf(v));
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.toString() : cur.getMessage();
    }

    /**
     * 检索命中项。
     *
     * @param id      业务 id（payload 里的 point_key）
     * @param pointId Qdrant 侧的 UUID
     * @param content payload 里的 content
     * @param score   相似度分数
     */
    public record MemoryHit(String id, String pointId, String content, double score) {}
}
