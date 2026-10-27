package com.ruoyi.opc.marketplace.mapper;

import com.ruoyi.opc.marketplace.domain.MpPublication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * mp_publication Mapper
 *
 * <p>params 支持的 key：category / productType / status / keyword / authorId /
 * sortBy(rating|installs|newest) / offset / limit</p>
 *
 * @author OAC
 */
@Mapper
public interface MpPublicationMapper {
    int insert(MpPublication p);

    MpPublication selectById(Long id);

    /**
     * 仅返回 PUBLISHED 的商品,供匿名公开详情端点使用。
     * <p>
     * {@code selectById} 不过滤 status,是作者侧(编辑/上下架/我的列表)所需,
     * 切勿把 status 过滤塞进 {@code selectById} —— 那会打断作者对自己 DRAFT 的编辑。
     * 网关白名单 {@code /opc/marketplace/publication/{id:[0-9]+}} 会绕过鉴权,
     * 必须走本方法,以防匿名调用方遍历 id 枚举 DRAFT/PENDING/SUSPENDED 草稿。
     *
     * @param id 主键
     * @return PUBLISHED 行;非 PUBLISHED 一律返回 {@code null},由 service 层映射为 404
     */
    MpPublication selectPublishedById(Long id);

    MpPublication selectByCode(String code);

    List<MpPublication> selectList(Map<String, Object> params);

    int countList(Map<String, Object> params);

    List<MpPublication> selectByAuthorId(Long authorId);

    int updateById(MpPublication p);

    int incrementViewCount(Long id);

    int incrementInstallCount(Long id);

    /**
     * 订阅数 +1 — W80 P2 起有真实调用方（{@code SubscriptionServiceImpl}）。
     *
     * <p><b>口径</b>：{@code subscription_count == COUNT(mp_subscription WHERE status='ACTIVE')}。
     * 订阅成功 +1、退订 -1、重新订阅 +1。健康检查第 19 节断言这个不变量。
     *
     * <p>P1 时本方法<b>刻意不接线</b> —— 那时没有「订阅」这个产品动作，
     * 计数器统计的是一个产品上不存在的行为，页面上会出现一个永远不动的数字，
     * 比不显示更糟。P2 把 {@code mp_subscription} 表与 UI 一起补上后才接的。
     */
    int incrementSubscriptionCount(Long id);

    /**
     * 订阅数 -1（退订）— W80 P2。
     *
     * <p>配 {@link #incrementSubscriptionCount(Long)} 用。下限保护在 SQL 里做：
     * 计数不会减到负数（{@code GREATEST(subscription_count - 1, 0)}）——
     * 计数器出现负数比偏大更像「数据坏了」。
     */
    int decrementSubscriptionCount(Long id);

    int updateRating(@Param("id") Long id, @Param("avg") BigDecimal avg, @Param("count") Integer count);

    int updateStatus(@Param("id") Long id, @Param("status") String status);
}