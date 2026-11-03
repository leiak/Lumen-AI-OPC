package com.ruoyi.opc.agent.workflow;

import java.util.Map;

/**
 * 工作流里的 {@code ${...}} 占位符渲染。
 *
 * <p><b>为什么抽成独立的小工具类</b>（裁定 R-99）：以前只有
 * {@link WorkflowEngine} 里有 {@code renderTemplate}，它是 private 的。
 * 现在 {@link WorkflowPersistHandler} 也要渲染 {@code persist.date}
 * （例如 {@code ${yesterday}}）—— 如果 handler 自己再写一份渲染逻辑，
 * 同一件事就有两份实现，两份会各自漂移（这正是 Task 3 缺陷 F2 的病根：
 * 日期口径写了两遍，其中一份没人测）。</p>
 *
 * <p>所以把渲染逻辑收敛到这一个地方：引擎自己调它，
 * {@link WorkflowPersistHandler} 也调它 —— <b>只有一份口径</b>。</p>
 *
 * <p>渲染规则保持<b>一字不变</b>（与 Task 3 的实现完全一致）：
 * 逐个变量把 {@code ${key}} 替换成 {@code String.valueOf(value)}，
 * 模板为 null 时返回空串，认不出来的占位符原样留着（不编造）。</p>
 *
 * @author OAC
 */
public final class WorkflowTemplate {

    private WorkflowTemplate() {
    }

    /**
     * 渲染模板里的 {@code ${key}} 占位符。
     *
     * @param template 模板文本（可为 null）
     * @param vars     变量表（可为 null → 视为空表）
     * @return 渲染后的文本
     */
    public static String render(String template, Map<String, Object> vars) {
        if (template == null) {
            return "";
        }
        if (vars == null || vars.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            result = result.replace("${" + e.getKey() + "}",
                    String.valueOf(e.getValue()));
        }
        return result;
    }

}
