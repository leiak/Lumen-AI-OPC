package com.ruoyi.opc.agent.mapper;

import com.ruoyi.opc.agent.domain.OpcAgentTask;

import java.util.List;

/**
 * Agent 任务 Mapper
 *
 * @author OAC
 */
public interface OpcAgentTaskMapper {

    OpcAgentTask selectById(Long id);

    OpcAgentTask selectByCode(String taskCode);

    List<OpcAgentTask> selectByCompany(Long companyId, Integer limit);

    List<OpcAgentTask> selectByInstance(Long instanceId, Integer limit);

    int insert(OpcAgentTask record);

    int update(OpcAgentTask record);

}
