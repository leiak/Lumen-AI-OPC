package com.ruoyi.opc.marketplace.mapper;

import com.ruoyi.opc.marketplace.domain.MpSubscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * mp_subscription Mapper — W80 P2
 *
 * @author OAC
 */
@Mapper
public interface MpSubscriptionMapper {

    int insert(MpSubscription s);

    /**
     * 按 uk_sub(publication_id, tenant_company_id, user_id) 取唯一记录。
     * 该唯一键<b>不含 deleted</b> —— 软删除行也占位，所以这里<b>不过滤 deleted</b>：
     * 否则「退订 → 重新订阅」会返回 null，随后 INSERT 撞 1062。
     */
    MpSubscription selectUnique(@Param("publicationId") Long publicationId,
                                @Param("tenantCompanyId") Long tenantCompanyId,
                                @Param("userId") Long userId);

    /**
     * 改状态（退订 CANCELLED / 重新订阅 ACTIVE）。
     * 退订时写 end_time —— 订阅的「有效期到什么时候」没有别的来源。
     */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("endTime") LocalDateTime endTime,
                     @Param("updateBy") String updateBy);

    /** 某个商品的活跃订阅数 —— 用来核对 subscription_count。 */
    int countActiveByPublication(@Param("publicationId") Long publicationId);

    /** 某个作者的活跃订阅数（按**去重后的租户公司数**算 —— 一家公司订阅一次算一个订阅者）。 */
    int countDistinctTenantsByAuthor(@Param("authorId") Long authorId);

    List<MpSubscription> selectByUser(@Param("userId") Long userId);

    List<MpSubscription> selectByTenant(@Param("tenantCompanyId") Long tenantCompanyId);
}
