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

    OpcAgentInstance getById(Long id);

    OpcAgentInstance getByCode(String code);

    List<OpcAgentInstance> listByCompany(Long companyId);

    List<OpcAgentInstance> listRunningByUser(Long userId);

    /** 雇佣 Agent（创建实例） */
    OpcAgentInstance hire(Long companyId, Long definitionId, String hireType, Integer duration, String nickname);

    /** 暂停/恢复 */
    int pause(Long id);

    int resume(Long id);

    /** 退订 */
    int revoke(Long id);

    /** 累加 Token 消耗 */
    void addTokenUsed(Long id, long delta);

    /** 累加任务数 */
    void incrementTaskCount(Long id);

}
