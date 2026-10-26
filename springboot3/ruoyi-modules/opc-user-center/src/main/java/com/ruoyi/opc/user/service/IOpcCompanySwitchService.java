package com.ruoyi.opc.user.service;

import com.ruoyi.opc.user.domain.OpcCompanyBrief;

import java.util.List;

/**
 * 多公司（多租户）切换
 *
 * <p>一个用户可以同时属于多家公司（{@code opc_company_member} 的唯一键是
 * {@code (company_id, user_id)}，即多对多）。登录时只能解析出<b>一家</b>默认公司
 * （{@code UserCompanyResolver}：老板优先、其次 id 最小），所以「我现在在哪家公司」
 * 必须是<b>可切换</b>的，否则多公司用户就只能看见默认那一家的数据。</p>
 *
 * @author OAC
 */
public interface IOpcCompanySwitchService {

    /**
     * 列出「我加入的公司」（= 可切换的租户）。
     *
     * <p>口径与登录期租户解析完全一致：{@code opc_company_member.status='ACTIVE'}
     * 且 {@code opc_company_profile.status='NORMAL'}。</p>
     *
     * @param userId          当前用户
     * @param currentCompanyId 当前登录态所在的公司（可为 null）—— 用来打「当前」标记
     * @return 我加入的公司列表（按 老板优先、其次 id 升序）
     */
    List<OpcCompanyBrief> listMyCompanies(Long userId, Long currentCompanyId);

    /**
     * 切换当前公司（改写登录态里的租户）。
     *
     * <p>只能切到自己是<b>在职成员</b>的公司 —— 否则就是把「改一个参数」
     * 变成「进别家的租户」，那正是 3b 修掉的那类洞。</p>
     *
     * @param userId         当前用户
     * @param targetCompanyId 目标公司 id
     * @return 切换后的公司 id
     */
    Long switchCompany(Long userId, Long targetCompanyId);

}
