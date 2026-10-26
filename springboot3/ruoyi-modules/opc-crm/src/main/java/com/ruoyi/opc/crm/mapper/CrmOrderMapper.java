package com.ruoyi.opc.crm.mapper;

import com.ruoyi.opc.crm.domain.CrmOrder;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CrmOrderMapper {

    int insert(CrmOrder order);

    int updateById(CrmOrder order);

    /**
     * 按 id 取数，但**必须**挂在指定 owner 名下的客户下（订单自身不带 owner_id）。
     *
     * <p>这个 mapper 里<b>故意没有</b>不带 owner 的 {@code selectById(id)} /
     * {@code selectByCustomerId(customerId)} / {@code selectByContractId(contractId)} ——
     * 那几个方法就是当初的洞。</p>
     */
    CrmOrder selectByIdAndOwner(@Param("id") Long id, @Param("ownerId") Long ownerId);

    /** 按客户维度列一批，且只限指定 owner。 */
    List<CrmOrder> selectByCustomerIdAndOwner(@Param("customerId") Long customerId,
                                               @Param("ownerId") Long ownerId);

    /**
     * 按合同维度列订单，且**合同本身**必须属于指定 owner。
     *
     * <p>合同 id 是客户端给的，所以这里 join 合同再 join 客户 —— 只比订单自己的
     * 客户是不够的，那挡不住「拿我的订单去套别人的合同」。</p>
     */
    List<CrmOrder> selectByContractIdAndOwner(@Param("contractId") Long contractId,
                                               @Param("ownerId") Long ownerId);

    int countByOrderNo(@Param("orderNo") String orderNo);
}
