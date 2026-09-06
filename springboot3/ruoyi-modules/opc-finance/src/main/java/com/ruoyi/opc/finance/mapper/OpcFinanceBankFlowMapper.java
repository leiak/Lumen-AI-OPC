package com.ruoyi.opc.finance.mapper;

import com.ruoyi.opc.finance.domain.OpcFinanceBankFlow;

import java.util.List;

public interface OpcFinanceBankFlowMapper {

    OpcFinanceBankFlow selectById(Long id);

    List<OpcFinanceBankFlow> selectByCompany(Long companyId, Integer extracted, Integer limit);

    int insert(OpcFinanceBankFlow record);

    int update(OpcFinanceBankFlow record);

    int insertBatch(List<OpcFinanceBankFlow> records);

}
