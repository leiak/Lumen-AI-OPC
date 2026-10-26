package com.ruoyi.opc.crm.mapper;

import com.ruoyi.opc.crm.domain.CrmContact;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CrmContactMapper {

    int insert(CrmContact contact);

    int updateById(CrmContact contact);

    /**
     * 按 id 取数，但**必须**挂在指定 owner 名下的客户下。
     *
     * <p>联系人自己不带 owner_id，归属沿客户走，所以要 join 客户表比 owner_id。</p>
     *
     * <p>这个 mapper 里<b>故意没有</b>不带 owner 的 {@code selectById(id)} /
     * {@code selectByCustomerId(customerId)} / {@code deleteById(id)} ——
     * 那几个方法就是当初的洞：谁都能按 id 动到别人的联系人。留不得。</p>
     */
    CrmContact selectByIdAndOwner(@Param("id") Long id, @Param("ownerId") Long ownerId);

    /** 同 {@link #selectByIdAndOwner}，但按客户维度列一批。 */
    List<CrmContact> selectByCustomerIdAndOwner(@Param("customerId") Long customerId,
                                                @Param("ownerId") Long ownerId);

    int unsetPrimaryForCustomer(@Param("customerId") Long customerId);

    int setPrimary(@Param("id") Long id);
}
