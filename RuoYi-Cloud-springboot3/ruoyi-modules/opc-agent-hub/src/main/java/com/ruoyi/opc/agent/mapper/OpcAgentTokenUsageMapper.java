package com.ruoyi.opc.agent.mapper;

import com.ruoyi.opc.agent.domain.OpcAgentTokenUsage;

import java.util.List;
import java.util.Map;

/**
 * Token 消耗 Mapper
 *
 * @author OAC
 */
public interface OpcAgentTokenUsageMapper {

    int insert(OpcAgentTokenUsage record);

    List<OpcAgentTokenUsage> selectByCompany(Long companyId, Integer limit);

    List<Map<String, Object>> aggregateDaily(Long companyId, String startDate, String endDate);

    Map<String, Object> summary(Long companyId, String bizDate);

}
