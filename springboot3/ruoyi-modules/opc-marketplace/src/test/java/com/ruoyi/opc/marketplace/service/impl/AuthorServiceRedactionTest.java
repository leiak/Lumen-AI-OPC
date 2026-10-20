package com.ruoyi.opc.marketplace.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.opc.marketplace.domain.MpAuthor;
import com.ruoyi.opc.marketplace.dto.AuthorVO;
import com.ruoyi.opc.marketplace.mapper.MpAuthorMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AuthorServiceImpl#toPublicVO} 脱敏契约测试 — 安全回归
 *
 * <p>{@code GET /opc/marketplace/author/{id}} 在网关白名单里允许<b>匿名</b>访问
 * （Task 10 的 {@code security.ignore.whites}），因此任何漏在公开 VO 上的联系方式 /
 * 营收都是全网可读的。脱敏因此必须落在 service 层而不是 controller 层 ——
 * controller 里那行 {@code vo.setContactEmail(null)} 一次重构就会被顺手删掉，
 * 而白名单是公开的。
 *
 * <p>{@link com.ruoyi.opc.marketplace.controller.AuthorControllerTest} 里的
 * {@code get_publicProfile_redactsContactFields} 走的是 {@code @MockBean} 的 service，
 * 证明不了「service 真的清空了字段」；本类补上这一环 ——
 * <b>mock 掉 controller 测试会漏、但本测试漏不掉</b>：把 {@code toPublicVO} 改回
 * 直接返回 {@code toVO(...)}，下面的断言立刻红。
 *
 * @author OAC
 */
class AuthorServiceRedactionTest {

    private AuthorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthorServiceImpl(mock(MpAuthorMapper.class), mock(MpPublicationMapper.class));
    }

    private MpAuthor sensitiveAuthor() {
        return MpAuthor.builder()
                .id(10L).authorCompanyId(100L).authorName("TestAuthor")
                .contactEmail("secret-boss@example.com")
                .contactPhone("13800001111")
                .intro("公开简介")
                .level("PREMIUM").status("ACTIVE")
                .totalAgents(3).totalSubscribers(12)
                .totalRevenue(new BigDecimal("9876543.21"))
                .build();
    }

    @Test
    @DisplayName("toVO 保留联系方式与营收(仅本人/内部流程可用)")
    void toVO_keepsSensitiveFields() {
        AuthorVO vo = service.toVO(sensitiveAuthor());

        assertThat(vo.getContactEmail()).isEqualTo("secret-boss@example.com");
        assertThat(vo.getTotalRevenue()).isEqualByComparingTo("9876543.21");
    }

    @Test
    @DisplayName("toPublicVO 清空 contactEmail / contactPhone / totalRevenue(公开端点用)")
    void toPublicVO_redactsSensitiveFields() {
        AuthorVO vo = service.toPublicVO(sensitiveAuthor());

        assertThat(vo.getContactEmail()).as("邮箱必须脱敏").isNull();
        assertThat(vo.getTotalRevenue()).as("营收必须脱敏").isNull();
        // AuthorVO 目前没有 contactPhone 字段(domain 有),该字段不会进响应;
        // 一旦给 VO 补上该字段,下面的 Jackson 断言会立刻抓到泄露
        assertThat(vo.getId()).isEqualTo(10L);
        assertThat(vo.getAuthorName()).as("公开资料仍需展示作者名").isEqualTo("TestAuthor");
        assertThat(vo.getIntro()).as("公开资料仍需展示简介").isEqualTo("公开简介");
        assertThat(vo.getLevel()).isEqualTo("PREMIUM");
        assertThat(vo.getTotalAgents()).as("公开统计保留").isEqualTo(3);
    }

    @Test
    @DisplayName("toPublicVO(null) → null(不 NPE)")
    void toPublicVO_nullSafe() {
        assertThat(service.toPublicVO(null)).isNull();
    }

    @Test
    @DisplayName("toPublicVO 的序列化结果里搜不到邮箱/电话/营收的字面值")
    void toPublicVO_serializedJson_hasNoSensitiveLiteral() throws Exception {
        String json = new ObjectMapper().writeValueAsString(service.toPublicVO(sensitiveAuthor()));

        assertThat(json)
                .doesNotContain("secret-boss@example.com")
                .doesNotContain("13800001111")
                .doesNotContain("9876543.21");
        // 反证:同一个 domain 走 toVO 时这些值都在,证明断言有效而非恒真
        String internalJson = new ObjectMapper().writeValueAsString(service.toVO(sensitiveAuthor()));
        assertThat(internalJson).contains("secret-boss@example.com").contains("9876543.21");
    }

    @Test
    @DisplayName("stats() 嵌套的 author 同样是脱敏 VO(authorId 由调用方任意指定)")
    void stats_redactsNestedAuthor() throws Exception {
        MpAuthorMapper authorMapper = mock(MpAuthorMapper.class);
        MpPublicationMapper publicationMapper = mock(MpPublicationMapper.class);
        AuthorServiceImpl svc = new AuthorServiceImpl(authorMapper, publicationMapper);
        when(authorMapper.selectById(10L)).thenReturn(sensitiveAuthor());
        when(publicationMapper.countList(Map.of("authorId", 10L, "status", "PUBLISHED"))).thenReturn(7);

        Map<String, Object> stats = svc.stats(10L);
        String json = new ObjectMapper().writeValueAsString(stats);

        assertThat(json)
                .doesNotContain("secret-boss@example.com")
                .doesNotContain("13800001111")
                .doesNotContain("9876543.21")
                .contains("TestAuthor")
                .contains("publicationCount");
    }
}