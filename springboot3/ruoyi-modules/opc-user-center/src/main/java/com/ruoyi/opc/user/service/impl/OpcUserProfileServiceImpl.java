package com.ruoyi.opc.user.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.common.utils.OpcCodeGenerator;
import com.ruoyi.opc.user.domain.OpcCompanyProfile;
import com.ruoyi.opc.user.domain.OpcUserProfile;
import com.ruoyi.opc.user.mapper.OpcCompanyProfileMapper;
import com.ruoyi.opc.user.mapper.OpcUserProfileMapper;
import com.ruoyi.opc.user.service.IOpcUserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OpcUserProfileServiceImpl implements IOpcUserProfileService {

    private final OpcUserProfileMapper userMapper;
    private final OpcCompanyProfileMapper companyMapper;

    @Override
    public OpcUserProfile getByUserId(Long userId) {
        return userMapper.selectByUserId(userId);
    }

    @Override
    public OpcUserProfile getByInvitationCode(String code) {
        return userMapper.selectByInvitationCode(code);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrUpdate(OpcUserProfile profile) {
        if (profile.getUserId() == null) throw new OpcException("userId 不能为空");
        OpcUserProfile exist = userMapper.selectByUserId(profile.getUserId());
        if (exist == null) {
            if (profile.getInvitationCode() == null || profile.getInvitationCode().isEmpty()) {
                profile.setInvitationCode(OpcCodeGenerator.inviteCode());
            }
            profile.setStatus("ACTIVE");
            profile.setVerified(0);
            profile.setCreateBy(String.valueOf(profile.getUserId()));
            userMapper.insert(profile);
            return profile.getId();
        } else {
            profile.setId(exist.getId());
            profile.setUpdateBy(String.valueOf(profile.getUserId()));
            userMapper.update(profile);
            return exist.getId();
        }
    }

    @Override
    public int update(OpcUserProfile profile) {
        return userMapper.update(profile);
    }

    @Override
    public List<OpcCompanyProfile> listCompaniesByOwner(Long ownerUserId) {
        return companyMapper.selectByOwner(ownerUserId);
    }

    @Override
    public Long createCompany(OpcCompanyProfile company) {
        if (company.getOwnerUserId() == null) throw new OpcException("ownerUserId 不能为空");
        if (company.getCompanyCode() == null || company.getCompanyCode().isEmpty()) {
            company.setCompanyCode("C" + System.currentTimeMillis() + "" + (int)(Math.random() * 1000));
        }
        company.setStatus("NORMAL");
        company.setVerified(0);
        company.setScale(company.getScale() == null ? "SMALL" : company.getScale());
        company.setCreateBy(String.valueOf(company.getOwnerUserId()));
        companyMapper.insert(company);
        return company.getId();
    }

    @Override
    public int updateCompany(OpcCompanyProfile company) {
        return companyMapper.update(company);
    }

    @Override
    public OpcCompanyProfile getCompany(Long id) {
        return companyMapper.selectById(id);
    }

}
