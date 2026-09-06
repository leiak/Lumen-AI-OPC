package com.ruoyi.opc.agent.mapper;

import com.ruoyi.opc.agent.domain.OpcAgentWorkflowRun;

import java.util.Date;

/**
 * 工作流执行历史 Mapper
 *
 * @author OAC
 */
public interface OpcAgentWorkflowRunMapper {

    int insert(OpcAgentWorkflowRun record);

    int update(OpcAgentWorkflowRun record);

    /**
     * 查 since 之后同一工作流仍处于 RUNNING 的最新一条，用于重复触发去重。
     */
    OpcAgentWorkflowRun findRunningByCodeSince(String workflowCode, Date since);

}
