package com.ruoyi.opc.user.mapper;

import com.ruoyi.opc.user.domain.OpcCompanyProfile;

import java.util.List;

public interface OpcCompanyProfileMapper {

    OpcCompanyProfile selectById(Long id);

    OpcCompanyProfile selectByCode(String code);

    List<OpcCompanyProfile> selectByOwner(Long ownerUserId);

    int insert(OpcCompanyProfile record);

    int update(OpcCompanyProfile record);

}
