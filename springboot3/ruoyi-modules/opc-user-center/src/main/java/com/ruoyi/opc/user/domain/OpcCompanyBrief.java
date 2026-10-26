package com.ruoyi.opc.user.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 「我加入的公司」—— 多公司（多租户）切换用的轻量投影
 *
 * <p>租户归属的唯一事实来源是 {@code opc_company_member}（多对多），
 * 所以「我能进的公司」= 我在 {@code opc_company_member} 里的在职成员关系，
 * <b>不是</b>「我创办的公司」（{@code opc_company_profile.owner_user_id}）。</p>
 *
 * <p>两者必须分开：被邀请进别人公司的员工是 STAFF，不是 OWNER ——
 * 按 owner 过滤的话，员工的「公司列表」是空的，于是前端只能显示默认那一家
 * （甚至什么都选不出来）。</p>
 *
 * @author OAC
 */
@Data
public class OpcCompanyBrief implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 公司 id */
    private Long id;

    /** 公司名称 */
    private String companyName;

    /** 我在这家公司的角色：OWNER-老板 / ADMIN-管理员 / STAFF-员工 */
    private String role;

    /** 是否是当前登录态所处的公司 */
    private Boolean current;

}
