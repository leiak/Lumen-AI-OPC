package com.ruoyi.opc.insight.service;

import com.ruoyi.opc.insight.vo.AdviceVo;

import java.util.List;

/**
 * INSIGHT 决策建议服务接口（M4 Task 9）。
 *
 * <p>提供按主题（topic）的 AI 建议生成 + 历史查询 + 重生成能力。
 * 7 天内同一 (companyId, topic) 组合命中缓存，避免重复 LLM 调用。</p>
 *
 * <p>所有方法的 {@code AdviceVo.id} 在调用 {@link #generate(Long, String)}
 * 后由 mapper 回填；{@link #regenerate(Long)} 会更新同一行的 advice_md /
 * llm_used / confidence（id 保持）。</p>
 *
 * @author OAC
 */
public interface IAdviceService {

    /**
     * 生成或返回缓存建议。
     *
     * <p>先查 7 天内是否有同 topic 的建议，有则直接返回（跳过 LLM 调用）；
     * 无则调 LLM 生成，落库后返回新行。任何 LLM 异常 → fallback 模板，
     * 保证落库不被阻断（与 DailyReportServiceImpl 一致）。</p>
     *
     * @param companyId 公司 ID（必填，>0）
     * @param topic     建议主题（必填，必须在 SUPPORTED_TOPICS 内）
     * @return 建议 VO（包含 id）
     * @throws com.ruoyi.opc.common.exception.OpcException 参数非法时
     */
    AdviceVo generate(Long companyId, String topic);

    /**
     * 按公司列出建议历史，按创建时间倒序。
     *
     * @param companyId 公司 ID（必填）
     * @param limit     条数限制（{@code null}/&lt;=0 = 20，上限 100）
     */
    List<AdviceVo> listByCompany(Long companyId, Integer limit);

    /**
     * 强制重新生成建议（绕过 7 天缓存）。
     *
     * <p>找到原行，调 LLM 重新生成 advice_md / llm_used / confidence。
     * id 保持不变（覆写原行）。</p>
     *
     * @param id 已存在的建议 ID
     * @return 更新后的 VO
     * @throws com.ruoyi.opc.common.exception.OpcException id 不存在时
     */
    AdviceVo regenerate(Long id);

    /**
     * 按主键查询。
     *
     * @param id 建议 ID
     * @throws com.ruoyi.opc.common.exception.OpcException id 不存在时
     */
    AdviceVo getById(Long id);
}