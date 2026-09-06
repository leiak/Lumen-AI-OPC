package com.ruoyi.opc.ai.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 向量数据库客户端（薄封装）
 *
 * @author OAC
 */
@Slf4j
@Component
public class QdrantVectorStore {

    @Value("${qdrant.host:127.0.0.1}")
    private String host;
    @Value("${qdrant.port:6334}")
    private int port;
    @Value("${qdrant.api-key:}")
    private String apiKey;

    public void upsert(String collection, String id, float[] vector, Map<String, Object> payload) {
        // 实际实现：使用 Qdrant Java SDK
        // qdrantClient.upsertAsync(collection, PointStruct.newBuilder()...)
        log.debug("[Qdrant] upsert collection={} id={} dim={}", collection, id, vector.length);
    }

    public List<String> search(String collection, float[] vector, int topK, String filterField, String filterValue) {
        // 实际实现：qdrantClient.searchAsync(SearchParams.newBuilder()...)
        log.debug("[Qdrant] search collection={} topK={} filter={}={}", collection, topK, filterField, filterValue);
        return new ArrayList<>();
    }

    public void delete(String collection, String id) {
        log.debug("[Qdrant] delete collection={} id={}", collection, id);
    }

}
