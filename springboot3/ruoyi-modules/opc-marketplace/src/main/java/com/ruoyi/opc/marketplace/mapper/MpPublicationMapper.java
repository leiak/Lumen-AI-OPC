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

    int incrementSubscriptionCount(Long id);

    int updateRating(@Param("id") Long id, @Param("avg") BigDecimal avg, @Param("count") Integer count);

    int updateStatus(@Param("id") Long id, @Param("status") String status);
}