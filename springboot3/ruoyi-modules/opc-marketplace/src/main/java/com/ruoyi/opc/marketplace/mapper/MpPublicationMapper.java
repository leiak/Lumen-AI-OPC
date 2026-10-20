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