package com.ruoyi.opc.crm.mapper;

import com.ruoyi.opc.crm.domain.CrmCustomer;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CrmCustomerMapper {

    int insert(CrmCustomer customer);

    int updateById(CrmCustomer customer);

    /**
     * 按 id 取数，但**必须**属于指定 owner。
     *
     * <p>归属判定是 CRM 的第二层隔离：租户（公司）之上还有「谁负责这条客户」。
     * 客户表自带 {@code owner_id}，所以这里直接比。</p>
     *
     * <p>这个 mapper 里<b>故意没有</b>不带 owner 的 {@code selectById(id)} ——
     * 那个方法就是当初的洞：谁都能按 id 取到别人的客户。留不得。</p>
     */
    CrmCustomer selectByIdAndOwner(@Param("id") Long id, @Param("ownerId") Long ownerId);

    List<CrmCustomer> selectList(@Param("ownerId") Long ownerId,
                                 @Param("level") String level,
                                 @Param("source") String source,
                                 @Param("tag") String tag,
                                 @Param("keyword") String keyword);

    int countByOwner(@Param("ownerId") Long ownerId);

    int countByOwnerAndLevel(@Param("ownerId") Long ownerId, @Param("level") String level);
}
