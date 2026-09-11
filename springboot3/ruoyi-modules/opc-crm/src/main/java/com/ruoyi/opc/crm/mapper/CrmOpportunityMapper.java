package com.ruoyi.opc.crm.mapper;

import com.ruoyi.opc.crm.domain.CrmOpportunity;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface CrmOpportunityMapper {

    int insert(CrmOpportunity opportunity);

    int updateById(CrmOpportunity opportunity);

    CrmOpportunity selectById(@Param("id") Long id);

    List<CrmOpportunity> selectList(@Param("ownerId") Long ownerId,
                                    @Param("stage") String stage,
                                    @Param("customerId") Long customerId);

    List<CrmOpportunity> selectByCustomerId(@Param("customerId") Long customerId);

    List<CrmOpportunity> selectByStage(@Param("stage") String stage);

    List<CrmOpportunity> selectTopByOwner(@Param("ownerId") Long ownerId, @Param("limit") int limit);

    int countByStage(@Param("ownerId") Long ownerId, @Param("stage") String stage);

    BigDecimal sumAmountByOwnerAndStages(@Param("ownerId") Long ownerId, @Param("stages") List<String> stages);
}
