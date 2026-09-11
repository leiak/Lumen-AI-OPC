package com.ruoyi.opc.crm.mapper;

import com.ruoyi.opc.crm.domain.CrmContact;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CrmContactMapper {

    int insert(CrmContact contact);

    int updateById(CrmContact contact);

    CrmContact selectById(@Param("id") Long id);

    List<CrmContact> selectByCustomerId(@Param("customerId") Long customerId);

    int unsetPrimaryForCustomer(@Param("customerId") Long customerId);

    int setPrimary(@Param("id") Long id);

    int deleteById(@Param("id") Long id);
}
