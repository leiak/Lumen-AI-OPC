package com.ruoyi.opc.crm.mapper;

import com.ruoyi.opc.crm.domain.CrmContract;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CrmContractMapper {

    int insert(CrmContract contract);

    int updateById(CrmContract contract);

    CrmContract selectById(@Param("id") Long id);

    List<CrmContract> selectByCustomerId(@Param("customerId") Long customerId);

    int countByContractNo(@Param("contractNo") String contractNo);
}
