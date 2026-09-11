package com.ruoyi.opc.crm.mapper;

import com.ruoyi.opc.crm.domain.CrmOrder;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CrmOrderMapper {

    int insert(CrmOrder order);

    int updateById(CrmOrder order);

    CrmOrder selectById(@Param("id") Long id);

    List<CrmOrder> selectByCustomerId(@Param("customerId") Long customerId);

    List<CrmOrder> selectByContractId(@Param("contractId") Long contractId);

    int countByOrderNo(@Param("orderNo") String orderNo);
}
