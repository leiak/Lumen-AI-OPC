package com.ruoyi.opc.agent.service.impl;

import com.ruoyi.opc.agent.domain.OpcAgentDefinition;
import com.ruoyi.opc.agent.mapper.OpcAgentDefinitionMapper;
import com.ruoyi.opc.agent.service.IOpcAgentDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 定义服务实现
 *
 * @author OAC
 */
@Service
@RequiredArgsConstructor
public class OpcAgentDefinitionServiceImpl implements IOpcAgentDefinitionService {

    private final OpcAgentDefinitionMapper mapper;

    @Override
    public OpcAgentDefinition getById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public List<OpcAgentDefinition> listPublished(String category) {
        return mapper.selectPublished(category);
    }

    @Override
    public List<OpcAgentDefinition> list(OpcAgentDefinition query) {
        return mapper.selectList(query);
    }

    @Override
    public int save(OpcAgentDefinition record) {
        return mapper.insert(record);
    }

    @Override
    public int update(OpcAgentDefinition record) {
        return mapper.update(record);
    }

    @Override
    public int remove(Long id) {
        return mapper.deleteById(id);
    }

}
