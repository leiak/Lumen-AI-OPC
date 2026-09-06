package com.ruoyi.opc.user.mapper;

import com.ruoyi.opc.user.domain.OpcUserProfile;

public interface OpcUserProfileMapper {

    OpcUserProfile selectByUserId(Long userId);

    OpcUserProfile selectByInvitationCode(String code);

    int insert(OpcUserProfile record);

    int update(OpcUserProfile record);

}
