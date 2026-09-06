package com.ruoyi.opc.agent.service;

import com.ruoyi.opc.agent.domain.OpcAgentTask;

import java.util.List;

public interface IOpcAgentTaskService {

    OpcAgentTask getByCode(String code);

    List<OpcAgentTask> listByCompany(Long companyId, Integer limit);

    List<OpcAgentTask> listByInstance(Long instanceId, Integer limit);

    Long createTask(OpcAgentTask task);

    int update(OpcAgentTask task);

}
