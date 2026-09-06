package com.ruoyi.opc.agent.service;

import com.ruoyi.opc.agent.domain.OpcAgentTokenUsage;

import java.util.List;
import java.util.Map;

public interface IOpcTokenUsageService {

    List<OpcAgentTokenUsage> listByCompany(Long companyId, Integer limit);

    List<Map<String, Object>> aggregateDaily(Long companyId, String startDate, String endDate);

    Map<String, Object> summary(Long companyId, String bizDate);

}
