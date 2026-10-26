package com.ruoyi.opc.user.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.common.utils.OpcCodeGenerator;
import com.ruoyi.opc.user.domain.OpcCompanyProfile;
import com.ruoyi.opc.user.domain.OpcUserProfile;
import com.ruoyi.opc.user.mapper.OpcCompanyProfileMapper;
import com.ruoyi.opc.user.mapper.OpcUserProfileMapper;
import com.ruoyi.opc.user.service.IOpcUserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OpcUserProfileServiceImpl implements IOpcUserProfileService {

    private final OpcUserProfileMapper userMapper;
    private final OpcCompanyProfileMapper companyMapper;
    /** 成员关系（opc_company_member）没有 mapper，用 JdbcTemplate 直查 —— 与 OpcInvitationServiceImpl 一致。 */
    private final JdbcTemplate jdbcTemplate;

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
            // W48.5: user_type NOT NULL 列,前端 saveProfile 不传,这里默认 ENTREPRENEUR
            if (profile.getUserType() == null || profile.getUserType().isEmpty()) {
                profile.setUserType("ENTREPRENEUR");
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
    @Transactional(rollbackFor = Exception.class)
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
        // 创建人自动成为这家公司的 OWNER 成员。
        //
        // 租户归属的唯一事实来源是 opc_company_member —— 不写这一行，
        // 新建的公司对创建人自己都是「不存在的租户」：登录期解析不出公司
        // (UserCompanyResolver 只认 opc_company_member)，companyId 为 null，
        // 于是所有租户维度的接口一律 400「当前账号未关联企业」。
        // 整段放在事务里：成员关系写不进去就把公司一起回滚，
        // 不能留下「有公司、没成员」的半截数据。
        jdbcTemplate.update(
                "INSERT INTO opc_company_member (company_id, user_id, role, joined_at, status, create_by, create_time) "
                        + "VALUES (?, ?, 'OWNER', NOW(), 'ACTIVE', ?, NOW())",
                company.getId(), company.getOwnerUserId(), String.valueOf(company.getOwnerUserId()));
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

    @Override
    public boolean isCompanyMember(Long companyId, Long userId) {
        if (companyId == null || userId == null) {
            return false;
        }
        // 租户归属的唯一事实来源 —— 与 UserCompanyResolver(SysUserCompanyMapper) 同一张表。
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from opc_company_member where company_id = ? and user_id = ? and status = 'ACTIVE'",
                Integer.class, companyId, userId);
        return count != null && count > 0;
    }

}
