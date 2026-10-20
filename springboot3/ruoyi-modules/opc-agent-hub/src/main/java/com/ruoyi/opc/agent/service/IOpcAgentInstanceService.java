package com.ruoyi.opc.agent.service;

import com.ruoyi.opc.agent.domain.OpcAgentInstance;
import com.ruoyi.opc.common.exception.OpcException;

import java.util.List;

/**
 * Agent 实例服务
 *
 * @author OAC
 */
public interface IOpcAgentInstanceService {

    /**
     * 按主键查(无租户过滤 — 内部用,不暴露给 controller)。
     * 公开入口请使用 {@link #getByIdAndCompany(Long, Long)}。
     */
    OpcAgentInstance getById(Long id);

    /**
     * 租户作用域的主键查询。
     * 返回 null 表示「不存在 / 不属于该租户」,调用方按 403 处理。
     */
    OpcAgentInstance getByIdAndCompany(Long id, Long companyId);

    OpcAgentInstance getByCode(String code);

    List<OpcAgentInstance> listByCompany(Long companyId);

    List<OpcAgentInstance> listRunningByUser(Long userId);

    /** 雇佣 Agent（创建实例） */
    OpcAgentInstance hire(Long companyId, Long definitionId, String hireType, Integer duration, String nickname);

    /** 暂停(RUNNING→PAUSED) — 租户作用域,rowsAffected=0 抛 OpcException(403) */
    int pause(Long id, Long companyId, String operatorId);

    int resume(Long id, Long companyId, String operatorId);

    /**
     * 退订(任何状态→REVOKED) — 租户作用域。
     * @param companyId 租户公司 id（来自登录态或 marketplace 兜底时由调用方传入）
     */
    int revoke(Long id, Long companyId, String operatorId);

    /** 累加 Token 消耗 */
    void addTokenUsed(Long id, long delta);

    /** 累加任务数 */
    void incrementTaskCount(Long id);

}
