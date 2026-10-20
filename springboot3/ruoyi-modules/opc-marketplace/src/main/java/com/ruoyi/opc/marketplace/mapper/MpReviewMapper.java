package com.ruoyi.opc.marketplace.mapper;

import com.ruoyi.opc.marketplace.domain.MpReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * mp_review Mapper
 *
 * <p>params 支持的 key：publicationId / status / keyword / offset / limit</p>
 *
 * @author OAC
 */
@Mapper
public interface MpReviewMapper {
    int insert(MpReview r);

    MpReview selectById(Long id);

    /**
     * 按 uk_review(publication_id, user_id) 取唯一记录。
     * 该唯一键不含 deleted —— 软删除后仍占用，故不加 deleted 过滤，
     * 保证「已评价（含已软删）」仍能被识别为重复评价。
     */
    MpReview selectByPublicationAndUser(@Param("publicationId") Long publicationId, @Param("userId") Long userId);

    List<MpReview> selectList(Map<String, Object> params);

    int countList(Map<String, Object> params);

    /** 仅统计 VISIBLE 且未删除的评价 */
    BigDecimal averageForPublication(Long publicationId);

    /** 仅统计 VISIBLE 且未删除的评价 */
    int countForPublication(Long publicationId);

    int updateById(MpReview r);

    int updateStatus(@Param("id") Long id, @Param("status") String status);
}