package com.ruoyi.opc.crm.mapper;

import com.ruoyi.opc.crm.domain.CrmContract;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CrmContractMapper {

    int insert(CrmContract contract);

    int updateById(CrmContract contract);

    /**
     * 按 id 取数，但**必须**挂在指定 owner 名下的客户下（合同自身不带 owner_id）。
     *
     * <p>这个 mapper 里<b>故意没有</b>不带 owner 的 {@code selectById(id)} /
     * {@code selectByCustomerId(customerId)} —— 那两个方法就是当初的洞。</p>
     */
    CrmContract selectByIdAndOwner(@Param("id") Long id, @Param("ownerId") Long ownerId);

    /** 按客户维度列一批，且只限指定 owner。 */
    List<CrmContract> selectByCustomerIdAndOwner(@Param("customerId") Long customerId,
                                                  @Param("ownerId") Long ownerId);

    int countByContractNo(@Param("contractNo") String contractNo);
}
