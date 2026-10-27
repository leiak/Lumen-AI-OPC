package com.ruoyi.opc.marketplace.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpPublication;
import com.ruoyi.opc.marketplace.domain.MpSubscription;
import com.ruoyi.opc.marketplace.mapper.MpAuthorMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.mapper.MpSubscriptionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SubscriptionServiceImpl} 单元测试 — W80 P2
 *
 * <p>订阅是本轮新写的产品动作，之前 {@code subscription_count} 这个数字
 * <b>在页面上展示着，却没有任何一条记录支撑它</b>。这里锁住四条契约：
 * <ol>
 *   <li><b>商品必须已发布</b>：草稿/下架的商品不许被订阅</li>
 *   <li><b>重复订阅 → 409</b>；退订后重新订阅走<b>复活</b>（{@code uk_sub} 不含
 *       {@code deleted}，退订行仍占唯一键，再 INSERT 就是 1062）</li>
 *   <li><b>退订不重复减计数</b>：已 CANCELLED 再退订 → 409，计数不能掉到负数</li>
 *   <li><b>租户隔离</b>：订阅行带 {@code tenant_company_id}，同一家公司的不同用户
 *       各占一行；作者的「订阅者数」按<b>去重后的公司数</b>算</li>
 * </ol>
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionServiceTest {

    @Mock
    MpSubscriptionMapper subscriptionMapper;
    @Mock
    MpPublicationMapper publicationMapper;
    @Mock
    MpAuthorMapper authorMapper;

    private SubscriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionServiceImpl(subscriptionMapper, publicationMapper, authorMapper);
    }

    private static MpPublication pub(String status, long authorId) {
        return MpPublication.builder().id(1L).status(status).productType("AGENT")
                .authorId(authorId).code("code-1").name("商品一").build();
    }

    private static MpSubscription sub(long id, String status) {
        return MpSubscription.builder().id(id).publicationId(1L).tenantCompanyId(10L)
                .userId(100L).productType("AGENT").plan("FREE").status(status).build();
    }

    // ==================== subscribe ====================

    @Test
    @DisplayName("subscribe — 商品不存在 → 404,一行都不写")
    void subscribe_404_whenPublicationMissing() {
        when(publicationMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.subscribe(999L, 10L, 100L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("商品不存在");

        verify(subscriptionMapper, never()).insert(any(MpSubscription.class));
        verify(publicationMapper, never()).incrementSubscriptionCount(anyLong());
    }

    @Test
    @DisplayName("subscribe — 商品未发布(DRAFT) → 400,不下探到 subscription 表")
    void subscribe_400_whenNotPublished() {
        when(publicationMapper.selectById(1L)).thenReturn(pub("DRAFT", 5L));

        assertThatThrownBy(() -> service.subscribe(1L, 10L, 100L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("未发布");

        verify(subscriptionMapper, never()).insert(any(MpSubscription.class));
    }

    @Test
    @DisplayName("subscribe — 首次订阅:INSERT 一行 ACTIVE + 商品计数 +1 + 作者订阅者数回写")
    void subscribe_success_insertsAndCounts() {
        when(publicationMapper.selectById(1L)).thenReturn(pub("PUBLISHED", 5L));
        when(subscriptionMapper.selectUnique(1L, 10L, 100L)).thenReturn(null);
        when(subscriptionMapper.countDistinctTenantsByAuthor(5L)).thenReturn(1);

        MpSubscription result = service.subscribe(1L, 10L, 100L);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getPlan()).isEqualTo("FREE");
        assertThat(result.getTenantCompanyId()).isEqualTo(10L);

        var captor = ArgumentCaptor.forClass(MpSubscription.class);
        verify(subscriptionMapper).insert(captor.capture());
        assertThat(captor.getValue().getPublicationId()).isEqualTo(1L);
        assertThat(captor.getValue().getUserId()).isEqualTo(100L);
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");

        verify(publicationMapper).incrementSubscriptionCount(1L);
        verify(authorMapper).refreshTotalSubscribers(5L, 1);
    }

    @Test
    @DisplayName("subscribe — 已 ACTIVE → 409「已订阅」,不重复 INSERT 也不重复加计数")
    void subscribe_409_whenAlreadySubscribed() {
        when(publicationMapper.selectById(1L)).thenReturn(pub("PUBLISHED", 5L));
        when(subscriptionMapper.selectUnique(1L, 10L, 100L)).thenReturn(sub(99L, "ACTIVE"));

        assertThatThrownBy(() -> service.subscribe(1L, 10L, 100L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("已订阅");

        verify(subscriptionMapper, never()).insert(any(MpSubscription.class));
        verify(publicationMapper, never()).incrementSubscriptionCount(anyLong());
    }

    @Test
    @DisplayName("subscribe — 退订后重新订阅:复活那一行,不是再 INSERT(uk_sub 不含 deleted,再 INSERT 就 1062)")
    void subscribe_resurrectsCancelledRow() {
        when(publicationMapper.selectById(1L)).thenReturn(pub("PUBLISHED", 5L));
        when(subscriptionMapper.selectUnique(1L, 10L, 100L)).thenReturn(sub(99L, "CANCELLED"));
        when(subscriptionMapper.countDistinctTenantsByAuthor(5L)).thenReturn(1);

        MpSubscription result = service.subscribe(1L, 10L, 100L);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getEndTime()).as("重新订阅要把结束时间清掉").isNull();
        verify(subscriptionMapper).updateStatus(eq(99L), eq("ACTIVE"), isNull(), eq("100"));
        verify(subscriptionMapper, never()).insert(any(MpSubscription.class));
        // 重新订阅是一次新订阅 → 计数 +1
        verify(publicationMapper).incrementSubscriptionCount(1L);
    }

    // ==================== unsubscribe ====================

    @Test
    @DisplayName("unsubscribe — 从没订阅过 → 404「未订阅」,不碰计数")
    void unsubscribe_404_whenNeverSubscribed() {
        when(subscriptionMapper.selectUnique(1L, 10L, 100L)).thenReturn(null);

        assertThatThrownBy(() -> service.unsubscribe(1L, 10L, 100L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("未订阅");

        verify(publicationMapper, never()).decrementSubscriptionCount(anyLong());
    }

    @Test
    @DisplayName("unsubscribe — 已经退订过 → 409,不重复减计数(否则计数会掉到负数)")
    void unsubscribe_409_whenAlreadyCancelled() {
        when(subscriptionMapper.selectUnique(1L, 10L, 100L)).thenReturn(sub(99L, "CANCELLED"));

        assertThatThrownBy(() -> service.unsubscribe(1L, 10L, 100L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("已取消");

        verify(subscriptionMapper, never()).updateStatus(anyLong(), any(), any(), any());
        verify(publicationMapper, never()).decrementSubscriptionCount(anyLong());
    }

    @Test
    @DisplayName("unsubscribe — 正常路径:置 CANCELLED + 写 end_time + 商品计数 -1")
    void unsubscribe_success() {
        when(subscriptionMapper.selectUnique(1L, 10L, 100L)).thenReturn(sub(99L, "ACTIVE"));
        when(publicationMapper.selectById(1L)).thenReturn(pub("PUBLISHED", 5L));
        when(subscriptionMapper.countDistinctTenantsByAuthor(5L)).thenReturn(0);

        service.unsubscribe(1L, 10L, 100L);

        verify(subscriptionMapper).updateStatus(eq(99L), eq("CANCELLED"), any(LocalDateTime.class), eq("100"));
        verify(publicationMapper).decrementSubscriptionCount(1L);
        verify(authorMapper).refreshTotalSubscribers(5L, 0);
    }

    // ==================== 租户隔离 ====================

    @Test
    @DisplayName("subscribe — 租户隔离:A 公司与 B 公司是两条独立记录,唯一键必须带 company")
    void subscribe_isolatesTenants() {
        when(publicationMapper.selectById(1L)).thenReturn(pub("PUBLISHED", 5L));
        when(subscriptionMapper.selectUnique(eq(1L), eq(10L), eq(100L))).thenReturn(null);
        when(subscriptionMapper.selectUnique(eq(1L), eq(20L), eq(100L))).thenReturn(null);
        when(subscriptionMapper.countDistinctTenantsByAuthor(5L)).thenReturn(2);

        service.subscribe(1L, 10L, 100L);
        service.subscribe(1L, 20L, 100L);

        // 关键断言:两次查唯一键用的公司 id 不同 —— 少了这个条件,A 公司退订会把 B 公司的行也退掉
        verify(subscriptionMapper).selectUnique(1L, 10L, 100L);
        verify(subscriptionMapper).selectUnique(1L, 20L, 100L);

        var captor = ArgumentCaptor.forClass(MpSubscription.class);
        verify(subscriptionMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(MpSubscription::getTenantCompanyId)
                .containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("subscribe — 作者订阅者数按去重后的公司数算(同公司 5 个人订阅算 1 个订阅者)")
    void subscribe_countsDistinctTenantsForAuthor() {
        when(publicationMapper.selectById(1L)).thenReturn(pub("PUBLISHED", 5L));
        when(subscriptionMapper.selectUnique(1L, 10L, 100L)).thenReturn(null);
        // 同一家公司的第 5 个人订阅 —— 公司数没变,还是 1
        when(subscriptionMapper.countDistinctTenantsByAuthor(5L)).thenReturn(1);

        service.subscribe(1L, 10L, 100L);

        verify(authorMapper).refreshTotalSubscribers(5L, 1);
    }

    // ==================== 查询 ====================

    @Test
    @DisplayName("getStatus — 未订阅返回 null(状态查询语义,不是 404)")
    void getStatus_nullWhenNotSubscribed() {
        when(subscriptionMapper.selectUnique(1L, 10L, 100L)).thenReturn(null);

        assertThat(service.getStatus(1L, 10L, 100L)).isNull();
    }

    @Test
    @DisplayName("listByUser / listByTenant — 直接透传 mapper,租户 id 必须原样带下去")
    void list_passesTenantThrough() {
        when(subscriptionMapper.selectByTenant(20L)).thenReturn(java.util.List.of(sub(1L, "ACTIVE")));

        assertThat(service.listByTenant(20L)).hasSize(1);
        verify(subscriptionMapper).selectByTenant(20L);
        verify(subscriptionMapper, never()).selectByTenant(eq(10L));
        verify(subscriptionMapper, never()).insert(any(MpSubscription.class));
    }
}
