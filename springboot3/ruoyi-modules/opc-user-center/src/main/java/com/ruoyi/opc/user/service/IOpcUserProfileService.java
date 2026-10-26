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

    /**
     * 判断某用户是否是某公司的在职成员。
     *
     * <p>租户归属的事实来源是 {@code opc_company_member}（{@code status='ACTIVE'}）。
     * 「公司的资料」这类按 id 取数的端点必须先过这一关：
     * 公司档案里有营业执照号、税号、法人、联系电话、邮箱 ——
     * 任何登录用户改一下 id 就能读别家的，属于跨租户读。</p>
     *
     * @param companyId 公司 ID
     * @param userId    用户 ID
     * @return 是该公司的在职成员则 true
     */
    boolean isCompanyMember(Long companyId, Long userId);

}
