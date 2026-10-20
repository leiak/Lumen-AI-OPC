package com.ruoyi.system.service.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import com.ruoyi.system.mapper.SysUserCompanyMapper;

/**
 * 登录期租户（公司）解析器
 *
 * <h3>为什么租户关系存放在 opc_company_member，而不是 sys_user</h3>
 *
 * <p>租户归属的<b>唯一事实来源</b>是 {@code opc_company_member}。邀请流程
 * （{@code OpcInvitationServiceImpl#accept}）本来就在这张表里维护成员关系，
 * {@code opc_company_profile} 维护公司主体。因此<b>不在 {@code sys_user} 上加
 * company_id 列</b> —— 复制一份状态必然会在成员变更（加入/离职/换公司）时漂移，
 * 而 {@code sys_user} 又是全仓库影响面最大的表（mapper 的
 * {@code selectUserVo} / {@code insertUser} / {@code updateUser} 全部要改）。</p>
 *
 * <h3>为什么在登录时解析而不是各服务自己查</h3>
 *
 * <p>{@code ruoyi-common-security} 里没有数据库访问能力，而租户字段必须在
 * <b>登录那一刻</b>定下来 —— 下游服务只拿到 token，拿不到 OPC 库连接。
 * 解析结果写进 {@code LoginUser.companyId}，随登录缓存（Redis）下发，
 * 业务侧统一用 {@code SecurityUtils.getCompanyId()} 读取。</p>
 *
 * <h3>分层耦合说明（风险登记）</h3>
 *
 * <p>{@code ruoyi-system} 是 RuoYi 内置模块，这里查询 OPC 领域表属于<b>有意的
 * 分层耦合</b>，代价与收益如下：</p>
 * <ul>
 *   <li>代价：纯 RuoYi 部署（没灌 OPC schema）时该查询必然失败。
 *       已通过 {@link #resolveCompanyId} 的降级路径处理：记 ERROR 日志并返回 null，
 *       <b>不阻断登录</b>（阻断会让缺一张可选业务表导致全平台无法登录）。</li>
 *   <li>收益：租户字段集中在一处产生，不会出现 12 个模块各写一套 company 查询。</li>
 * </ul>
 *
 * <p><b>若日后租户模型被重构进 RuoYi 自有的 {@code sys_dept}</b>（例如部门即租户），
 * 本解析器应随之迁移到 {@code sys_dept} 的解析路径上，并删除
 * {@link SysUserCompanyMapper}，避免两套租户来源并存。</p>
 *
 * @author ruoyi
 */
@Component
public class UserCompanyResolver
{
    private static final Logger log = LoggerFactory.getLogger(UserCompanyResolver.class);

    @Autowired
    private SysUserCompanyMapper userCompanyMapper;

    /**
     * 解析用户的默认公司ID
     *
     * <p><b>返回 null 有且只有两种含义，必须区分：</b></p>
     * <ol>
     *   <li><b>正常</b>：查询成功执行，用户确实没有加入任何有效公司
     *       （如从未接受过邀请）—— 这是合法状态，返回 null，调用方按「未关联企业」处理。</li>
     *   <li><b>异常</b>：租户表缺失/结构不对，或数据库不可用 —— 记 <b>ERROR</b> 日志
     *       并在日志中给出修复指引（缺失的表 + 对应迁移文件），同样返回 null。</li>
     * </ol>
     *
     * <p><b>为什么不抛异常</b>：解析发生在登录主链路上，一旦抛异常，
     * 缺一张 OPC 业务表就会导致<b>全平台无人能登录</b>（含 admin、含所有非 marketplace
     * 模块）。而返回 null 在下游是<b>fail-closed</b> 的 —— 所有租户维度的入口
     * （install / review / 订阅）都会显式校验 companyId 非 null，为 null 直接返回
     * 400「当前账号未关联企业」，不会产生跨租户读写。代价只是可用性，
     * 而非数据越权，因此选择「响亮报错 + 降级」而不是「抛异常」。</p>
     *
     * @param userId 用户ID
     * @return 默认公司ID；用户未加入任何有效公司、或租户状态未知时返回 null
     */
    public Long resolveCompanyId(Long userId)
    {
        if (userId == null)
        {
            return null;
        }
        try
        {
            return userCompanyMapper.selectDefaultCompanyId(userId);
        }
        catch (DataAccessException e)
        {
            // 表不存在 / 列名不对 / 连不上库 —— 租户状态未知，记 ERROR 而不是 WARN：
            // 这属于部署配置错误，静默降级会让 marketplace 全量 400 却无人察觉根因。
            log.error("[UserCompanyResolver] 无法确定用户所属公司（租户表不可用），"
                    + "将按『未关联企业』处理，所有租户维度接口会返回 400。"
                    + "请检查 opc_company_member / opc_company_profile 两张表是否存在且已灌入数据"
                    + "（建表见 deploy/mysql-initdb.d/05-opc-main-schema.sql，"
                    + "反查索引见 sql/migrations/V20261021__tenant_company_lookup_index.sql）。"
                    + " userId={}, 原因: {}", userId, e.getMessage());
            return null;
        }
        catch (Exception e)
        {
            log.error("[UserCompanyResolver] 解析用户所属公司时发生未预期异常，"
                    + "将按『未关联企业』处理。userId={}, 原因: {}", userId, e.getMessage(), e);
            return null;
        }
    }
}