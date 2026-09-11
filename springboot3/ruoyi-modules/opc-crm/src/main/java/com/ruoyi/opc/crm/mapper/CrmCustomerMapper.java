package com.ruoyi.opc.crm.mapper;

import com.ruoyi.opc.crm.domain.CrmCustomer;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CrmCustomerMapper {

    int insert(CrmCustomer customer);

    int updateById(CrmCustomer customer);

    CrmCustomer selectById(@Param("id") Long id);

    List<CrmCustomer> selectList(@Param("ownerId") Long ownerId,
                                 @Param("level") String level,
                                 @Param("source") String source,
                                 @Param("tag") String tag,
                                 @Param("keyword") String keyword);

    int countByOwner(@Param("ownerId") Long ownerId);

    int countByOwnerAndLevel(@Param("ownerId") Long ownerId, @Param("level") String level);
}
