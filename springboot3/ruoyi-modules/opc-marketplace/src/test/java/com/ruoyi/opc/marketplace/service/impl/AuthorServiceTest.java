package com.ruoyi.opc.marketplace.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpAuthor;
import com.ruoyi.opc.marketplace.dto.AuthorApplyRequest;
import com.ruoyi.opc.marketplace.dto.AuthorVO;
import com.ruoyi.opc.marketplace.mapper.MpAuthorMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthorServiceImpl} 单元测试 — P1 Task 8.5
 *
 * <p>与 {@link AuthorServiceApplyTest} 的分工：后者只盯「租户绑定契约」这一条安全不变量
 * （{@code author_company_id} 必须来自登录态），本类覆盖 apply / getById / toVO / stats 的
 * 常规业务分支，两者互补而非重复。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthorServiceTest {

    @Mock
    MpAuthorMapper authorMapper;
    @Mock
    MpPublicationMapper publicationMapper;

    private AuthorServiceImpl authorService;

    @BeforeEach
    void setUp() {
        authorService = new AuthorServiceImpl(authorMapper, publicationMapper);
    }

    private AuthorApplyRequest req() {
        return AuthorApplyRequest.builder()
                .authorName("X")
                .contactEmail("a@b.com")
                .build();
    }

    @Test
    @DisplayName("apply — 企业已入驻 → 409(uk_author_company 不含 deleted,任何状态都算占位)")
    void apply_409_whenCompanyAlreadyAuthor() {
        when(authorMapper.selectByCompanyId(100L)).thenReturn(MpAuthor.builder().id(1L).build());

        assertThatThrownBy(() -> authorService.apply(req(), 100L, 1L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("已是作者");
    }

    @Test
    @DisplayName("apply — 首次入驻 → 返回带 id 的作者档案,level 默认 NORMAL")
    void apply_success_returnsAuthor() {
        when(authorMapper.selectByCompanyId(200L)).thenReturn(null);
        when(authorMapper.insert(any(MpAuthor.class))).thenAnswer(inv -> {
            MpAuthor a = inv.getArgument(0);
            a.setId(99L);
            return 1;
        });

        MpAuthor result = authorService.apply(req(), 200L, 1L);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getLevel()).isEqualTo("NORMAL");
        // contactEmail 是 NOT NULL 列且 MpAuthorMapper.xml 的 INSERT 显式列出 contact_email
        // （无 COALESCE）—— 少这一行 setter，任何未填邮箱的请求会撞 MySQL 1364
        assertThat(result.getContactEmail()).isEqualTo("a@b.com");
    }

    @Test
    @DisplayName("getById — 作者不存在 → 404(OpcException),不返回 null")
    void getById_404_whenNotFound() {
        when(authorMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> authorService.getById(999L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("作者不存在");
    }

    @Test
    @DisplayName("toVO — 逐字段映射到 AuthorVO(营收用 BigDecimal 比较,不做字符串化)")
    void toVO_copiesFields() {
        MpAuthor a = MpAuthor.builder()
                .id(1L).authorCompanyId(2L).authorName("X")
                .contactEmail("a@b.com").level("PREMIUM")
                .totalAgents(5).totalSubscribers(10)
                .totalRevenue(new BigDecimal("1000.50"))
                .status("ACTIVE").build();

        AuthorVO vo = authorService.toVO(a);

        assertThat(vo.getAuthorName()).isEqualTo("X");
        assertThat(vo.getLevel()).isEqualTo("PREMIUM");
        assertThat(vo.getTotalRevenue()).isEqualByComparingTo("1000.50");
    }

    @Test
    @DisplayName("stats — 返回 author(脱敏)+ publicationCount,且 countList 带 status=PUBLISHED 过滤")
    @SuppressWarnings("unchecked")
    void stats_returnsAuthorAndPublicationCount() {
        MpAuthor a = MpAuthor.builder().id(1L).authorCompanyId(2L)
                .contactEmail("secret@example.com").totalRevenue(new BigDecimal("999.99")).build();
        when(authorMapper.selectById(1L)).thenReturn(a);
        when(publicationMapper.countList(any())).thenReturn(5);

        var stats = authorService.stats(1L);

        assertThat(stats.get("publicationCount")).isEqualTo(5);
        AuthorVO nested = (AuthorVO) stats.get("author");
        assertThat(nested.getId()).isEqualTo(1L);
        // 嵌套档案同样走脱敏闸门 —— 否则任意登录用户都能读到别家企业的邮箱与营收
        assertThat(nested.getContactEmail()).isNull();
        assertThat(nested.getTotalRevenue()).isNull();

        // 参数必须逐键断言 —— countList(any()) 匹配一切，改成 PENDING 不会被发现。
        // 漏掉 status 会把 DRAFT/SUSPENDED 商品也计进 publicationCount（榜单虚高）
        ArgumentCaptor<Map<String, Object>> q = ArgumentCaptor.forClass(Map.class);
        verify(publicationMapper).countList(q.capture());
        assertThat(q.getValue())
                .containsEntry("status", "PUBLISHED")
                .containsEntry("authorId", 1L);
    }
}