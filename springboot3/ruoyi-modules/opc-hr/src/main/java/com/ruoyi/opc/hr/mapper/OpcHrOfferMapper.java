package com.ruoyi.opc.hr.mapper;

import com.ruoyi.opc.hr.domain.OpcHrOffer;
import org.apache.ibatis.annotations.Param;

public interface OpcHrOfferMapper {
    int insert(OpcHrOffer offer);

    int updateById(OpcHrOffer offer);

    OpcHrOffer selectById(@Param("id") Long id, @Param("companyId") Long companyId);

    OpcHrOffer selectByApplicationId(@Param("applicationId") Long applicationId);
}
