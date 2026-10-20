package com.ruoyi.opc.marketplace.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpAuthor;
import com.ruoyi.opc.marketplace.dto.AuthorApplyRequest;
import com.ruoyi.opc.marketplace.mapper.MpAuthorMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
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
 * {@link AuthorServiceImpl#apply} 租户绑定契约 — 安全回归
 *
 * <p>{@link com.ruoyi.opc.marketplace.controller.AuthorControllerTest} 里那几个 apply 用例
 * 走的是 {@code @MockBean} 的 service，只能证明「controller 把 companyId 传对了」；
 * 本类补上这一环 —— <b>mock 掉 controller 测试会漏、但本测试漏不掉</b>：把
 * {@code apply} 里的 {@code a.setAuthorCompanyId(companyId)} 改回从别处取值
 * （例如重新加回 DTO 字段、或硬编码），下面 {@code apply_writesLoginCompanyAsAuthorCompanyId}
 * 的 captor 断言立刻红。
 *
 * <p>为什么这是安全问题：{@code mp_author.uk_author_company(author_company_id)} 是
 * <b>不含 {@code deleted} 列</b>的硬唯一键。任何「替别家公司申请」的能力都不是数据泄露，
 * 而是<b>永久占位</b> —— 被冒名的那家公司此后每次申请都撞 1062/409，永远进不了市场。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthorServiceApplyTest {

    @Mock
    MpAuthorMapper authorMapper;
    @Mock
    MpPublicationMapper publicationMapper;

    private AuthorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthorServiceImpl(authorMapper, publicationMapper);
    }

    private AuthorApplyRequest req() {
        return AuthorApplyRequest.builder()
                .authorName("测试企业")
                .contactEmail("boss@example.com")
                .build();
    }

    @Test
    @DisplayName("apply — author_company_id 取自入参 companyId(=登录态),落库时不被任何请求体字段覆盖")
    void apply_writesLoginCompanyAsAuthorCompanyId() {
        when(authorMapper.selectByCompanyId(100L)).thenReturn(null);
        when(authorMapper.insert(any(MpAuthor.class))).thenAnswer(inv -> {
            MpAuthor a = inv.getArgument(0);
            a.setId(99L);
            return 1;
        });

        service.apply(req(), 100L, 1L);

        var captor = org.mockito.ArgumentCaptor.forClass(MpAuthor.class);
        verify(authorMapper).insert(captor.capture());
        assertThat(captor.getValue().getAuthorCompanyId())
                .as("入驻公司必须是登录态那家")
                .isEqualTo(100L);
        // 查重也必须打在同一家公司上(只调用一次、且参数就是登录态那家)
        verify(authorMapper, org.mockito.Mockito.times(1)).selectByCompanyId(100L);
    }

    @Test
    @DisplayName("apply — 同一公司重复申请 → 409(uk_author_company 硬唯一键,被占后该公司永久申请不了)")
    void apply_409_whenCompanyAlreadyAuthor() {
        when(authorMapper.selectByCompanyId(100L)).thenReturn(MpAuthor.builder().id(1L).build());

        assertThatThrownBy(() -> service.apply(req(), 100L, 1L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("已是作者");

        verify(authorMapper, never()).insert(any(MpAuthor.class));
    }

    @Test
    @DisplayName("apply — 落库补齐 mp_author 的 NOT NULL 列(W50 教训:INSERT 显式列名不应用 DDL DEFAULT)")
    void apply_fillsNotNullColumns() {
        when(authorMapper.selectByCompanyId(200L)).thenReturn(null);
        when(authorMapper.insert(any(MpAuthor.class))).thenAnswer(inv -> {
            MpAuthor a = inv.getArgument(0);
            a.setId(99L);
            return 1;
        });

        MpAuthor a = service.apply(req(), 200L, 7L);

        assertThat(a.getLevel()).isEqualTo("NORMAL");
        assertThat(a.getTotalAgents()).isZero();
        assertThat(a.getTotalSubscribers()).isZero();
        assertThat(a.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(a.getStatus()).isEqualTo("ACTIVE");
        assertThat(a.getCreateBy()).isEqualTo("7");
        assertThat(a.getUpdateBy()).isEqualTo("7");
    }
}
