package com.ruoyi.opc.user.service;

import com.ruoyi.opc.user.domain.OpcCompanyProfile;
import com.ruoyi.opc.user.domain.OpcUserProfile;

import java.util.List;

public interface IOpcUserProfileService {

    OpcUserProfile getByUserId(Long userId);

    OpcUserProfile getByInvitationCode(String code);

    Long createOrUpdate(OpcUserProfile profile);

    int update(OpcUserProfile profile);

    List<OpcCompanyProfile> listCompaniesByOwner(Long ownerUserId);

    Long createCompany(OpcCompanyProfile company);

    int updateCompany(OpcCompanyProfile company);

    OpcCompanyProfile getCompany(Long id);

}
