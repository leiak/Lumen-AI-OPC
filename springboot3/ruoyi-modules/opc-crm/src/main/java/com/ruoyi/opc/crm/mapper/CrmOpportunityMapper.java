package com.ruoyi.opc.crm.mapper;

import com.ruoyi.opc.crm.domain.CrmOpportunity;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface CrmOpportunityMapper {

    int insert(CrmOpportunity opportunity);

    int updateById(CrmOpportunity opportunity);

    /**
     * 按 id 取数，但**必须**属于指定 owner（商机自带 owner_id）。
     *
     * <p>这个 mapper 里<b>故意没有</b>不带 owner 的 {@code selectById(id)} /
     * {@code selectByCustomerId(customerId)} / {@code selectByStage(stage)} ——
     * 那几个方法就是当初的洞：阶段是全局枚举，不带 owner 更是全员数据。</p>
     */
    CrmOpportunity selectByIdAndOwner(@Param("id") Long id, @Param("ownerId") Long ownerId);

    List<CrmOpportunity> selectList(@Param("ownerId") Long ownerId,
                                    @Param("stage") String stage,
                                    @Param("customerId") Long customerId);

    /** 按客户维度列一批，且只限指定 owner。 */
    List<CrmOpportunity> selectByCustomerIdAndOwner(@Param("customerId") Long customerId,
                                                    @Param("ownerId") Long ownerId);

    /** 按阶段列一批，且只限指定 owner —— 阶段是全局枚举，不带 owner 就是全员数据。 */
    List<CrmOpportunity> selectByStageAndOwner(@Param("stage") String stage,
                                               @Param("ownerId") Long ownerId);

    List<CrmOpportunity> selectTopByOwner(@Param("ownerId") Long ownerId, @Param("limit") int limit);

    int countByStage(@Param("ownerId") Long ownerId, @Param("stage") String stage);

    BigDecimal sumAmountByOwnerAndStages(@Param("ownerId") Long ownerId, @Param("stages") List<String> stages);
}
