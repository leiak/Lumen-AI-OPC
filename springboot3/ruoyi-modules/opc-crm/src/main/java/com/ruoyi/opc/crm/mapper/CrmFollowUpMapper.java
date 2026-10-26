package com.ruoyi.opc.crm.mapper;

import com.ruoyi.opc.crm.domain.CrmFollowUp;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CrmFollowUpMapper {

    int insert(CrmFollowUp followUp);

    int updateById(CrmFollowUp followUp);

    /**
     * 按 id 取数，但**必须**属于指定 owner（跟进记录自带 owner_id）。
     *
     * <p>这个 mapper 里<b>故意没有</b>不带 owner 的 {@code selectById(id)} /
     * {@code selectByCustomerId(customerId)} / {@code deleteById(id)} ——
     * 那几个方法就是当初的洞。</p>
     */
    CrmFollowUp selectByIdAndOwner(@Param("id") Long id, @Param("ownerId") Long ownerId);

    /** 按客户维度列一批，且只限指定 owner。 */
    List<CrmFollowUp> selectByCustomerIdAndOwner(@Param("customerId") Long customerId,
                                                  @Param("ownerId") Long ownerId);

    List<CrmFollowUp> selectByOwnerId(@Param("ownerId") Long ownerId);

    List<CrmFollowUp> selectUpcoming(@Param("ownerId") Long ownerId, @Param("now") LocalDateTime now);
}
