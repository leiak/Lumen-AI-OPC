package com.ruoyi.opc.ai.gateway.llm;

import com.ruoyi.opc.common.exception.OpcException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Embedding 网关：按优先级挑 provider，失败自动 fallback。
 *
 * <p><b>这里以前根本不存在</b> —— 记忆系统里的 {@code embed()} 直接返回
 * {@code Math.random()} 填出来的 1536 维"向量"。于是：
 * <ul>
 *   <li>写进 Qdrant 的是噪声，检索出来的也是噪声</li>
 *   <li>而接口一路返回成功，日志里什么都看不出来</li>
 * </ul>
 *
 * <p>现在没有可用的 embedding provider 就**明说没有**（抛
 * {@link OpcException}），不再拿随机数冒充向量。
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingGateway {

    private final List<EmbeddingProvider> providers;

    /**
     * 当前可用的 provider（按优先级从小到大）。
     */
    public List<EmbeddingProvider> available() {
        return providers.stream()
                .filter(EmbeddingProvider::enabled)
                .sorted(Comparator.comparingInt(EmbeddingProvider::priority))
                .toList();
    }

    /**
     * 有没有可用的 embedding provider。
     *
     * <p>记忆系统在写不进去之前会先问这个 —— 让"没有"变成一个能查的事实，
     * 而不是日志里一句看不出来的 warning。
     */
    public boolean available4Memory() {
        return !available().isEmpty();
    }

    /**
     * 当前首选 provider 的名字（一个都没有时返回 {@code "none"}）。
     *
     * <p>对外接口要如实说明「这次是谁算的」—— 免得出了问题查不到人头上。
     */
    public String primaryName() {
        List<EmbeddingProvider> a = available();
        return a.isEmpty() ? "none" : a.get(0).name();
    }

    /**
     * 单条向量化（自动 fallback）。
     */
    public float[] embed(String text) {
        return embed(text, EmbeddingProvider.EmbedType.DOCUMENT);
    }

    /**
     * 单条向量化（带用途）。
     */
    public float[] embed(String text, EmbeddingProvider.EmbedType type) {
        List<float[]> r = embedBatch(List.of(text == null ? "" : text), type);
        return r.get(0);
    }

    /**
     * 批量向量化（自动 fallback）。
     */
    public List<float[]> embedBatch(List<String> texts, EmbeddingProvider.EmbedType type) {
        return embedBatchWithProvider(texts, type).vectors();
    }

    /**
     * 批量向量化，并带上实际用到的 provider（对外接口要如实说明是谁算的）。
     */
    public EmbeddingResult embedBatchWithProvider(List<String> texts, EmbeddingProvider.EmbedType type) {
        // 入参问题在入口就拒掉。别让它掉进下面的 provider 循环 ——
        // 那会把「调用方传了 null」说成「所有 provider 都挂了」，
        // 排查的人会去查 provider，查半天查不出东西。
        if (texts == null || texts.isEmpty()) {
            throw new OpcException("texts 不能为空 —— 没有文本就没法向量化");
        }
        List<EmbeddingProvider> candidates = available();
        if (candidates.isEmpty()) {
            throw new OpcException("没有可用的 embedding provider —— 长期记忆写不进去"
                    + "（检查 opc.llm.minimax.api-key 与 opc.llm.minimax.enabled）");
        }
        Exception last = null;
        for (EmbeddingProvider p : candidates) {
            try {
                List<float[]> vectors = p.embedBatch(texts, type);
                if (vectors == null || vectors.size() != texts.size()) {
                    throw new IllegalStateException("向量条数与请求条数对不上");
                }
                int dim = vectors.isEmpty() ? 0 : vectors.get(0).length;
                log.debug("[Embedding] provider={} model={} type={} texts={} dim={}",
                        p.name(), p.model(), type, texts.size(), dim);
                return new EmbeddingResult(p.name(), p.model(), dim, vectors);
            } catch (Exception e) {
                last = e;
                log.warn("[Embedding] provider={} 失败，尝试下一个: {}", p.name(), e.getMessage());
            }
        }
        // last.getMessage() 本身可能是 null —— 不兜底的话错误信息会变成「…失败：null」
        throw new OpcException("所有 embedding provider 失败：" + String.valueOf(last == null ? null : last.getMessage()));
    }

    /**
     * 一次向量化的结果。
     *
     * @param provider  实际使用的 provider
     * @param model     实际使用的模型
     * @param dimension 向量维度
     * @param vectors   向量本身
     */
    public record EmbeddingResult(String provider, String model, int dimension, List<float[]> vectors) {}
}
