package com.ruoyi.opc.ai.gateway.llm;

import java.util.List;

/**
 * Embedding（向量化）提供者抽象。
 *
 * <p><b>为什么单独一套接口</b>：聊天和向量化是两种能力，厂商提供的模型也不同
 * （MiniMax 聊天用 {@code MiniMax-Text-01}，向量化用 {@code embo-01}）。
 * 硬塞进 {@link ChatModelProvider} 只会让每个 provider 多出一堆用不上的方法。
 *
 * <p>长期记忆（Qdrant）完全依赖它 —— 没有能用的 embedding provider，
 * 记忆系统就既写不进也检索不出，只能明说"没有"，不能拿随机数糊弄过去。
 *
 * @author OAC
 */
public interface EmbeddingProvider {

    /**
     * 向量化的用途。
     *
     * <p>不是所有厂商都一视同仁：MiniMax 的 {@code embo-01} 要求区分
     * 「要入库的文档」和「用来检索的查询」，用错类型检索质量会明显下降。
     */
    enum EmbedType {
        /** 写入向量库的文档 */
        DOCUMENT,
        /** 检索用的查询 */
        QUERY
    }

    /** 提供者名称 */
    String name();

    /** 模型标识 */
    String model();

    /** 是否启用（通常还要求 api-key 非空） */
    boolean enabled();

    /** 优先级（越小越优先） */
    default int priority() { return 100; }

    /** 向量维度 —— 决定 Qdrant 集合怎么建 */
    int dimension();

    /** 单条向量化 */
    float[] embed(String text);

    /** 批量向量化（顺序与入参一致） */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 带用途的向量化（默认实现忽略用途）。
     */
    default float[] embed(String text, EmbedType type) {
        return embed(text);
    }

    /** 带用途的批量向量化 */
    default List<float[]> embedBatch(List<String> texts, EmbedType type) {
        return embedBatch(texts);
    }
}
