package com.ruoyi.opc.agent.mapper;

import com.ruoyi.opc.agent.domain.OpcAgentDefinition;

import java.util.List;

/**
 * Agent 定义 Mapper
 *
 * @author OAC
 */
public interface OpcAgentDefinitionMapper {

    OpcAgentDefinition selectById(Long id);

    OpcAgentDefinition selectByCode(String agentCode, String version);

    List<OpcAgentDefinition> selectList(OpcAgentDefinition query);

    List<OpcAgentDefinition> selectPublished(String category);

    int insert(OpcAgentDefinition record);

    int update(OpcAgentDefinition record);

    int deleteById(Long id);

    int incrementDownloads(Long id);

}
