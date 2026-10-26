package com.ruoyi.opc.user.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.security.service.TokenService;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.user.domain.OpcCompanyBrief;
import com.ruoyi.opc.user.service.IOpcCompanySwitchService;
import com.ruoyi.system.api.model.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 多公司（多租户）切换 —— 实现
 *
 * <p><b>切换是怎么生效的</b>：租户不在 token 里（JWT 只带 user_key/user_id/user_name），
 * 而在 <b>Redis 的登录缓存</b> 里（{@code login_tokens:{uuid}} → {@link LoginUser}）。
 * 所以「切换」= 把 Redis 里那个 {@code LoginUser.companyId} 改掉 ——
 * 之后所有下游服务（租户守卫 {@code SecurityUtils.getCompanyId()}）读到的
 * 都是新公司，不需要重新登录，也不需要给 token 加字段。</p>
 *
 * <p>成员关系表 {@code opc_company_member} 没有 mapper，这里用 JdbcTemplate 直查 ——
 * 与 {@code OpcUserProfileServiceImpl} / {@code OpcInvitationServiceImpl} 一致。</p>
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcCompanySwitchServiceImpl implements IOpcCompanySwitchService {

    /**
     * 我加入的公司（= 可切换的租户）。
     *
     * <p>口径刻意与登录期租户解析（{@code UserCompanyResolver} →
     * {@code SysUserCompanyMapper#selectDefaultCompanyId}）保持一致：
     * 只认「在职成员」且「公司正常」，排序也是「老板优先、其次 id 升序」。
     * 两处口径一旦分叉，就会出现「登录解析到 A、切换列表里却是 B」的怪事。</p>
     */
    private static final String SELECT_MY_COMPANIES =
            "select m.company_id as companyId, p.company_name as companyName, m.role as role "
                    + "from opc_company_member m "
                    + "inner join opc_company_profile p on p.id = m.company_id "
                    + "where m.user_id = ? and m.status = 'ACTIVE' and p.status = 'NORMAL' "
                    + "order by (m.role = 'OWNER') desc, m.company_id asc";

    private final JdbcTemplate jdbcTemplate;

    /** 登录态读写（Redis 里的 LoginUser） */
    private final TokenService tokenService;

    @Override
    public List<OpcCompanyBrief> listMyCompanies(Long userId, Long currentCompanyId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        // queryForList 返回的列名是大小写不敏感的（LinkedCaseInsensitiveMap），
        // 所以别名写成 companyId / companyName / role 都取得到。
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SELECT_MY_COMPANIES, userId);
        List<OpcCompanyBrief> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            OpcCompanyBrief brief = new OpcCompanyBrief();
            Object id = row.get("companyId");
            if (id instanceof Number number) {
                brief.setId(number.longValue());
            }
            brief.setCompanyName(str(row.get("companyName")));
            brief.setRole(str(row.get("role")));
            brief.setCurrent(Boolean.FALSE);
            list.add(brief);
        }
        if (currentCompanyId != null) {
            list.forEach(b -> b.setCurrent(currentCompanyId.equals(b.getId())));
        }
        return list;
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public Long switchCompany(Long userId, Long targetCompanyId) {
        if (userId == null) {
            throw new ServiceException("未登录，无法切换公司", 401);
        }
        if (targetCompanyId == null) {
            throw new ServiceException("目标公司不能为空", 400);
        }
        // 只能切到自己是「在职成员」的公司 —— 拿可切换列表来判，
        // 免得再写一套成员判断口径（两套口径迟早分叉，分叉出的是越权）。
        boolean member = listMyCompanies(userId, null).stream()
                .anyMatch(c -> targetCompanyId.equals(c.getId()));
        if (!member) {
            throw new ServiceException("无权切换到该公司：你不是它的在职成员", 403);
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            throw new ServiceException("未登录，无法切换公司", 401);
        }
        // 改写 Redis 里的登录态 —— 下游服务下一次读到的就是新公司。
        loginUser.setCompanyId(targetCompanyId);
        tokenService.setLoginUser(loginUser);
        log.info("[公司切换] userId={} 切换到 companyId={}", userId, targetCompanyId);
        return targetCompanyId;
    }

}
