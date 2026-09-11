package com.ruoyi.opc.crm.mapper;

import com.ruoyi.opc.crm.domain.CrmFollowUp;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CrmFollowUpMapper {

    int insert(CrmFollowUp followUp);

    int updateById(CrmFollowUp followUp);

    CrmFollowUp selectById(@Param("id") Long id);

    List<CrmFollowUp> selectByCustomerId(@Param("customerId") Long customerId);

    List<CrmFollowUp> selectByOwnerId(@Param("ownerId") Long ownerId);

    List<CrmFollowUp> selectUpcoming(@Param("ownerId") Long ownerId, @Param("now") LocalDateTime now);

    int deleteById(@Param("id") Long id);
}
