package com.ruoyi.opc.agent.service.impl;

import com.ruoyi.opc.agent.domain.OpcAgentTokenUsage;
import com.ruoyi.opc.agent.mapper.OpcAgentTokenUsageMapper;
import com.ruoyi.opc.agent.service.IOpcTokenUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpcTokenUsageServiceImpl implements IOpcTokenUsageService {

    private final OpcAgentTokenUsageMapper mapper;

    @Override
    public List<OpcAgentTokenUsage> listByCompany(Long companyId, Integer limit) {
        return mapper.selectByCompany(companyId, limit == null ? 20 : limit);
    }

    @Override
    public List<Map<String, Object>> aggregateDaily(Long companyId, String startDate, String endDate) {
        return mapper.aggregateDaily(companyId, startDate, endDate);
    }

    @Override
    public Map<String, Object> summary(Long companyId, String bizDate) {
        return mapper.summary(companyId, bizDate);
    }

}
