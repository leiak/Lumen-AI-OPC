package com.ruoyi.opc.crm.service;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.crm.mapper.CrmContactMapper;
import com.ruoyi.opc.crm.mapper.CrmContractMapper;
import com.ruoyi.opc.crm.mapper.CrmCustomerMapper;
import com.ruoyi.opc.crm.mapper.CrmOpportunityMapper;

/**
 * CRM 归属判定 —— 租户隔离之上的第二层。
 *
 * <p><b>为什么要有这一层</b>：租户（公司）管的是「A 公司别看 B 公司的数据」，
 * 但公司<b>内部</b>同样是隔离的 —— 销售 1 不该翻销售 2 的客户档案。原来的实现
 * 一律只按 id 取数（{@code selectById(id)}），又把 {@code ownerId} /
 * {@code customerId} 交给客户端，于是「把路径里的 id 换成别人的」就等于越权读、
 * 越权改、越权删。这是同一类洞的另一个作用域：租户隔离（3b）管住了公司，
 * 这里管住公司里的人。</p>
 *
 * <p><b>归属怎么算</b>：</p>
 * <ul>
 *   <li>客户 / 商机 / 跟进记录 —— 表里自带 {@code owner_id}，直接比；</li>
 *   <li>联系人 / 订单 / 合同 —— 自己不带 owner_id，归属沿客户走（join 客户表）。</li>
 * </ul>
 *
 * <p><b>为什么「不存在」和「不是你的」用同一句话</b>：分开报就等于送出一个
 * <b>存在性预言机</b> —— 探测方能靠两者的差别把别人的 id 一个一个试出来。所以两种
 * 情况都回同一句「不存在或无权访问」。这跟 {@code TenantGuard} 的思路一致：
 * 覆盖而不是拒绝。</p>
 *
 * @author OAC
 */
public final class CrmOwnership {

    private CrmOwnership() {
    }

    /**
     * 统一的拒绝语义。
     *
     * <p>用 {@link ServiceException} 带 403 —— RuoYi 的约定是业务错误走
     * <b>HTTP 200 + body.code</b>，不是 HTTP 错误码。</p>
     *
     * @param what 资源名（客户 / 商机 / …），只出现在提示文案里
     * @return 带 403 码的 ServiceException
     */
    public static ServiceException denied(String what) {
        return new ServiceException(what + "不存在或无权访问 (not found or not accessible)", 403);
    }

    /**
     * 引用的客户必须属于当前用户 —— 往别人客户下挂子记录同样是越权。
     *
     * <p>id 为 null 时放过（可选引用）；「必填」的检查由各 service 自己做，
     * 报的是参数错误而不是越权。</p>
     */
    public static void requireCustomer(CrmCustomerMapper mapper, Long customerId, Long ownerId) {
        if (customerId != null && mapper.selectByIdAndOwner(customerId, ownerId) == null) {
            throw denied("客户");
        }
    }

    /** 引用的联系人必须属于当前用户。 */
    public static void requireContact(CrmContactMapper mapper, Long contactId, Long ownerId) {
        if (contactId != null && mapper.selectByIdAndOwner(contactId, ownerId) == null) {
            throw denied("联系人");
        }
    }

    /** 引用的商机必须属于当前用户。 */
    public static void requireOpportunity(CrmOpportunityMapper mapper, Long opportunityId, Long ownerId) {
        if (opportunityId != null && mapper.selectByIdAndOwner(opportunityId, ownerId) == null) {
            throw denied("商机");
        }
    }

    /** 引用的合同必须属于当前用户。 */
    public static void requireContract(CrmContractMapper mapper, Long contractId, Long ownerId) {
        if (contractId != null && mapper.selectByIdAndOwner(contractId, ownerId) == null) {
            throw denied("合同");
        }
    }
}
