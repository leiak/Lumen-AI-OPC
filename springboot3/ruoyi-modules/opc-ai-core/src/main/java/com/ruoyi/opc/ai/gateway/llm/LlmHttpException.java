package com.ruoyi.opc.ai.gateway.llm;

/**
 * LLM 厂商返回非 2xx —— 带状态码,让上层能判断「该不该重试」。
 *
 * <p><b>为什么不用普通 RuntimeException</b>：以前 {@code HttpLlmClient} 把状态码
 * 拼进消息字符串就扔了（{@code "HTTP 429 from ..."}），上层要判断「429 是配额问题、
 * 重试只会再撞一次」就只能去 {@code grep} 一个中文句子 —— 那是 CLAUDE.md 坑 #8
 * 的同构病（真相藏在字符串里）。状态码必须是<b>结构化</b>的字段。</p>
 *
 * <p>继承 {@link RuntimeException} 是为了让 provider 的
 * {@code catch (Exception e)} 不必改签名；但注意 provider 里
 * {@code catch (LlmHttpException)} 必须排在 {@code catch (Exception)} <b>前面</b>，
 * 顺序反了状态码就又只剩字符串了。</p>
 *
 * @author OAC
 */
public class LlmHttpException extends RuntimeException {

    private final int statusCode;

    public LlmHttpException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    /** 厂商返回的 HTTP 状态码（401 / 429 / 503 ...）。 */
    public int getStatusCode() {
        return statusCode;
    }
}
