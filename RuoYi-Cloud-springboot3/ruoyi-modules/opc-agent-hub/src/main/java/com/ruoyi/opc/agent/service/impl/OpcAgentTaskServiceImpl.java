package com.ruoyi.opc.agent.service.impl;

import com.ruoyi.opc.agent.domain.OpcAgentTask;
import com.ruoyi.opc.agent.mapper.OpcAgentTaskMapper;
import com.ruoyi.opc.agent.service.IOpcAgentTaskService;
import com.ruoyi.opc.common.utils.OpcCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OpcAgentTaskServiceImpl implements IOpcAgentTaskService {

    private final OpcAgentTaskMapper mapper;

    @Override
    public OpcAgentTask getByCode(String code) {
        return mapper.selectByCode(code);
    }

    @Override
    public List<OpcAgentTask> listByCompany(Long companyId, Integer limit) {
        return mapper.selectByCompany(companyId, limit == null ? 20 : limit);
    }

    @Override
    public List<OpcAgentTask> listByInstance(Long instanceId, Integer limit) {
        return mapper.selectByInstance(instanceId, limit == null ? 20 : limit);
    }

    @Override
    public Long createTask(OpcAgentTask task) {
        if (task.getTaskCode() == null) {
            task.setTaskCode(OpcCodeGenerator.taskCode());
        }
        task.setStatus("PENDING");
        mapper.insert(task);
        return task.getId();
    }

    @Override
    public int update(OpcAgentTask task) {
        return mapper.update(task);
    }

}
