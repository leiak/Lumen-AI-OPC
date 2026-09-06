package com.ruoyi.opc.agent.service;

import com.ruoyi.opc.agent.domain.OpcAgentDefinition;

import java.util.List;

/**
 * Agent 定义服务
 *
 * @author OAC
 */
public interface IOpcAgentDefinitionService {

    OpcAgentDefinition getById(Long id);

    List<OpcAgentDefinition> listPublished(String category);

    List<OpcAgentDefinition> list(OpcAgentDefinition query);

    int save(OpcAgentDefinition record);

    int update(OpcAgentDefinition record);

    int remove(Long id);

}
