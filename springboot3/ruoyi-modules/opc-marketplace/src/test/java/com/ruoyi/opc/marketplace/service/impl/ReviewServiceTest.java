package com.ruoyi.opc.marketplace.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpPublication;
import com.ruoyi.opc.marketplace.domain.MpReview;
import com.ruoyi.opc.marketplace.dto.ReviewCreateRequest;
import com.ruoyi.opc.marketplace.dto.ReviewUpdateRequest;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.mapper.MpReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReviewServiceImpl} 单元测试 — P1 Task 8.5
 *
 * <p>关注两条不变量：
 * <ol>
 *   <li><b>唯一性</b>：{@code uk_review(publication_id, user_id)} 不含 {@code deleted}，
 *       即便是 HIDDEN 的历史行也算「已评价」→ 409。</li>
 *   <li><b>评分回写</b>：create / update / delete 三条路径都必须让
 *       {@code mp_publication.avg_rating / rating_count} 保持新鲜 ——
 *       尤其 delete：行已离开 VISIBLE 集却不重算的话，商品聚合会永久陈旧（C1 回归）。</li>
 * </ol>
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    @Mock
    MpReviewMapper reviewMapper;
    @Mock
    MpPublicationMapper publicationMapper;

    private ReviewServiceImpl reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewServiceImpl(reviewMapper, publicationMapper);
    }

    private static MpPublication publishedPub() {
        return MpPublication.builder().id(1L).status("PUBLISHED").build();
    }

    @Test
    @DisplayName("create — 商品不存在 → 404")
    void create_404_whenPublicationMissing() {
        when(publicationMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> reviewService.create(
                ReviewCreateRequest.builder().publicationId(1L).rating(5).build(), 1L, 1L))
                .isInstanceOf(OpcException.class);

        verify(reviewMapper, never()).insert(any(MpReview.class));
    }

    @Test
    @DisplayName("create — 商品未发布 → 400「未发布」(DRAFT/PENDING 上评价会污染 avg)")
    void create_400_whenPublicationNotPublished() {
        when(publicationMapper.selectById(1L)).thenReturn(
                MpPublication.builder().id(1L).status("DRAFT").build());

        assertThatThrownBy(() -> reviewService.create(
                ReviewCreateRequest.builder().publicationId(1L).rating(5).build(), 1L, 1L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("未发布");

        verify(reviewMapper, never()).insert(any(MpReview.class));
    }

    @Test
    @DisplayName("create — 同一用户重复评价 → 409「已评价」(uk_review 不含 deleted,HIDDEN 行也算命中)")
    void create_409_whenAlreadyReviewed() {
        when(publicationMapper.selectById(1L)).thenReturn(publishedPub());
        when(reviewMapper.selectByPublicationAndUser(1L, 1L))
                .thenReturn(MpReview.builder().id(99L).status("HIDDEN").build());

        assertThatThrownBy(() -> reviewService.create(
                ReviewCreateRequest.builder().publicationId(1L).rating(5).build(), 1L, 1L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("已评价");

        verify(reviewMapper, never()).insert(any(MpReview.class));
    }

    @Test
    @DisplayName("create — 正常路径:落库 VISIBLE 行并把 AVG 归一到 2 位小数回写商品")
    void create_success_updatesAvgRating() {
        when(publicationMapper.selectById(1L)).thenReturn(publishedPub());
        when(reviewMapper.selectByPublicationAndUser(1L, 1L)).thenReturn(null);
        when(reviewMapper.insert(any(MpReview.class))).thenAnswer(inv -> {
            MpReview r = inv.getArgument(0);
            r.setId(50L);
            return 1;
        });
        when(reviewMapper.averageForPublication(1L)).thenReturn(new BigDecimal("4.5"));
        when(reviewMapper.countForPublication(1L)).thenReturn(3);

        MpReview result = reviewService.create(
                ReviewCreateRequest.builder().publicationId(1L).rating(5).content("Great").build(), 1L, 1L);

        assertThat(result.getId()).isEqualTo(50L);
        assertThat(result.getStatus()).isEqualTo("VISIBLE");
        verify(publicationMapper).updateRating(1L, new BigDecimal("4.50"), 3);
    }

    @Test
    @DisplayName("update — 非本人评价 → 403「本人」(fail-closed,不因 userId 为 null 而 NPE)")
    void update_403_whenNotOwner() {
        MpReview r = MpReview.builder().id(1L).userId(99L).build();
        when(reviewMapper.selectById(1L)).thenReturn(r);

        assertThatThrownBy(() -> reviewService.update(1L,
                ReviewUpdateRequest.builder().rating(4).build(), 1L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("本人");

        verify(reviewMapper, never()).updateById(any(MpReview.class));
    }

    @Test
    @DisplayName("delete — 软删 HIDDEN 后必须重算 avg/count(C1 回归:否则商品聚合永久陈旧)")
    void delete_updatesAvgRating() {
        MpReview r = MpReview.builder().id(1L).userId(1L).publicationId(10L).build();
        when(reviewMapper.selectById(1L)).thenReturn(r);
        when(reviewMapper.countForPublication(10L)).thenReturn(2);
        when(reviewMapper.averageForPublication(10L)).thenReturn(new BigDecimal("4.5"));

        reviewService.delete(1L, 1L);

        verify(reviewMapper).updateStatus(1L, "HIDDEN");
        verify(publicationMapper).updateRating(10L, new BigDecimal("4.50"), 2);
    }
}