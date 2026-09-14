package com.ruoyi.opc.content.service;

import com.ruoyi.opc.content.domain.OpcContentPublish;
import com.ruoyi.opc.content.domain.OpcContentScript;
import com.ruoyi.opc.content.dto.OpcContentDashboardDto;
import com.ruoyi.opc.content.dto.OpcContentGenerateRequest;
import com.ruoyi.opc.content.dto.OpcContentListResponse;
import com.ruoyi.opc.content.dto.OpcContentScriptDto;

import java.util.List;

/**
 * 内容脚本 Service 接口 — 短剧 / 视频 / 图文 / 适配 (含 LLM 生成 + 状态机守卫)
 *
 * <p>W74 Task 4: Service 接口 + impl。
 *
 * @author OAC
 */
public interface IOpcContentScriptService {

    /**
     * 创建脚本并触发 LLM 生成 (Task 6 真实接入 ContentLlmClient)。
     * 入库状态:DRAFT
     *
     * @return 新脚本 ID
     */
    Long create(OpcContentGenerateRequest req);

    /**
     * 脚本详情(跨租户隔离)
     */
    OpcContentScript detail(Long id, Long companyId);

    /**
     * 分页列表 + type/status 过滤
     */
    OpcContentListResponse<OpcContentScript> list(Long companyId, String type, String status,
                                                   int page, int size);

    /**
     * 更新脚本 title/contentMd(仅 DRAFT 状态)
     */
    int update(Long id, Long companyId, OpcContentScriptDto dto);

    /**
     * 删除脚本(仅 DRAFT 状态,软删 status=DELETED)
     */
    int delete(Long id, Long companyId);

    /**
     * 重新调 LLM 生成(基于新 promptInput)
     *
     * @return 脚本 ID(同入参)
     */
    Long regenerate(Long id, Long companyId, String promptInput);

    /**
     * 部分精修: 把 lineNo/instruction 拼到 prompt 后缀再调用 LLM
     */
    void refine(Long id, Long companyId, Integer lineNo, String instruction);

    /**
     * DRAFT → READY(标记为可发布)
     */
    void markReady(Long id, Long companyId);

    /**
     * 某脚本的全部发布历史
     */
    List<OpcContentPublish> publishHistory(Long scriptId, Long companyId);

    /**
     * Dashboard 聚合: 4 统计卡 + 7 天趋势 + 最近 5 脚本
     */
    OpcContentDashboardDto dashboard(Long companyId);
}