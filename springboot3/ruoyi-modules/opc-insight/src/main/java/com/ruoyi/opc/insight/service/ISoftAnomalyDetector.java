package com.ruoyi.opc.insight.service;

import com.ruoyi.opc.insight.vo.AnomalyVo;
import com.ruoyi.opc.insight.vo.KpiSnapshot;

import java.util.List;

/**
 * 软异常检测器接口（M4 Task 6）。
 *
 * <p>设计动机：opc-ai-core 的 {@code LlmGateway} 只提供通用的
 * {@code chat(messages, options, ctx)} 入口（返回 {@code ChatResponse}），
 * 而 <b>解析 ChatResponse 为异常列表</b> 是 INSIGHT 业务专属逻辑。
 *
 * <p>把这一段封装在 opc-insight 内部的接口背后，有 3 个好处：
 * <ol>
 *   <li>模块边界清晰 —— INSIGHT 不感知 LLM 消息构造 / JSON 解析细节。</li>
 *   <li>易于测试 —— {@code AnomalyServiceImplTest} 可直接 mock 本接口返回
 *       {@code List<AnomalyVo>}，无需 stub 整条 LLM 调用链。</li>
 *   <li>替换实现无关 —— 未来可换本地规则引擎 / 远端规则服务，签名不变。</li>
 * </ol>
 *
 * <p>调用方契约：返回的列表元素必须填齐 {@code ruleCode / description /
 * level / llmConfidence} 字段；其它字段（companyId/period/status/createTime）
 * 由 {@code AnomalyServiceImpl.scan()} 在持久化前补全。
 *
 * @author OAC
 */
public interface ISoftAnomalyDetector {

    /**
     * 对 KPI 快照执行软异常检测（典型实现：调用 LLM 解析 KPI → 异常列表）。
     *
     * @param snapshot KPI 快照
     * @return LLM 识别出的异常列表（可能为空；调用方负责按置信度阈值过滤）
     */
    List<AnomalyVo> detect(KpiSnapshot snapshot);
}
