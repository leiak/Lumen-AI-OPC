package com.ruoyi.opc.agent.service;

import com.ruoyi.opc.agent.domain.OpcAgentWorkflowRun;

/**
 * 工作流触发服务：被 ruoyi-job(Quartz) 通过内部 HTTP 调用，负责执行工作流并持久化执行历史。
 *
 * @author OAC
 */
public interface IWorkflowTriggerService {

    /**
     * 按工作流编码触发一次执行（同步），并写入 opc_agent_workflow_run。
     *
     * @param workflowCode  工作流编码
     * @param triggerSource 触发来源标识，写入 run 记录便于排错
     * @return 本次执行记录；若命中 30 秒去重窗口，返回已存在的那条 RUNNING 记录
     */
    OpcAgentWorkflowRun trigger(String workflowCode, String triggerSource);

}
