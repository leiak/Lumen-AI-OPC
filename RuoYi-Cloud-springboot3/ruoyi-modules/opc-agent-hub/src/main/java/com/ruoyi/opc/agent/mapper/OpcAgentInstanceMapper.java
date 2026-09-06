package com.ruoyi.opc.agent.mapper;

import com.ruoyi.opc.agent.domain.OpcAgentInstance;

import java.util.List;

/**
 * Agent 实例 Mapper
 *
 * @author OAC
 */
public interface OpcAgentInstanceMapper {

    OpcAgentInstance selectById(Long id);

    OpcAgentInstance selectByCode(String instanceCode);

    List<OpcAgentInstance> selectByCompany(Long companyId);

    List<OpcAgentInstance> selectRunningByUser(Long userId);

    int insert(OpcAgentInstance record);

    int update(OpcAgentInstance record);

    int incrementTaskCount(Long id);

    int addTokenUsed(Long id, Long delta);

}
