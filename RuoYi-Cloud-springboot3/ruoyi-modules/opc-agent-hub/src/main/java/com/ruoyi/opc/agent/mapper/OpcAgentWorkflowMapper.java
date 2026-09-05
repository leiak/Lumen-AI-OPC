package com.ruoyi.opc.agent.mapper;

import com.ruoyi.opc.agent.domain.OpcAgentWorkflow;

import java.util.Date;

/**
 * Agent 编排工作流 Mapper
 *
 * @author OAC
 */
public interface OpcAgentWorkflowMapper {

    OpcAgentWorkflow selectByCode(String workflowCode);

    /**
     * 一次执行完成后回写元数据：last_run_time / next_run_at / run_count + 1。
     * 用 SQL 自增而非读改写，避免并发触发时计数丢失。
     */
    int updateRunStats(Long id, Date lastRunTime, Date nextRunAt);

}
