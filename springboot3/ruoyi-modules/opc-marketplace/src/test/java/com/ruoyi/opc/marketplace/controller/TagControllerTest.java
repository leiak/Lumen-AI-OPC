package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.security.handler.GlobalExceptionHandler;
import com.ruoyi.opc.marketplace.domain.MpTag;
import com.ruoyi.opc.marketplace.feign.AgentHubClient;
import com.ruoyi.opc.marketplace.feign.CommunityClient;
import com.ruoyi.opc.marketplace.mapper.CommunityModuleMapper;
import com.ruoyi.opc.marketplace.mapper.MpAuthorMapper;
import com.ruoyi.opc.marketplace.mapper.MpCategoryMapper;
import com.ruoyi.opc.marketplace.mapper.MpInstallMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.mapper.MpReviewMapper;
import com.ruoyi.opc.marketplace.mapper.MpSubscriptionMapper;
import com.ruoyi.opc.marketplace.mapper.MpTagMapper;
import com.ruoyi.opc.marketplace.service.TagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link TagController} 集成测试（{@code @WebMvcTest}）— P1 Task 8
 *
 * <p>4 个场景：
 * <ul>
 *   <li>{@code list_noParam_returnsAll}              — 无 {@code category} → service.list()</li>
 *   <li>{@code list_withCategory_returnsFiltered}    — 带 {@code category=} → service.listByCategory()</li>
 *   <li>{@code list_empty_returnsOk}                 — 空结果集仍 code=200 + {@code data: []}（不是 404）</li>
 *   <li>{@code list_emptyStringParam_fallsBackToAll} — {@code ?category=}（空串，非 null）也回落全量</li>
 * </ul>
 *
 * <p>最后一条专门锁 {@code TagController.list} 里的 {@code isEmpty()} 分支：
 * 缺参是 {@code null}、显式空串是 {@code ""}，两者语义都应等同「不过滤」。若有人把
 * {@code isEmpty()} 判断删掉，{@code ?category=} 会打出一个 {@code WHERE category=''}
 * 的查询，永远返回空列表 —— 前端「清空筛选」按钮就会静默失效。
 *
 * <p>装配与 {@link CategoryControllerTest} 完全一致（同一批 mapper / Feign 占位 bean），
 * 且同样<b>不</b> mockStatic(SecurityUtils)：标签是全局参考数据，公开读，无租户守卫。
 *
 * <p>计划里标注的「catch-all error log not tested」是有意留白 —— {@code TagController}
 * 没有异常分支可测，唯一的错误路径在 mapper 层，不属于 controller 契约。
 *
 * @author OAC
 */
@WebMvcTest(controllers = TagController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.ruoyi\\.opc\\.marketplace\\.mapper\\..*"))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "spring.main.banner-mode=off",
        "logging.level.root=ERROR",
        "spring.profiles.active=test",
        "spring.application.name=opc-marketplace"
})
class TagControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TagService tagService;

    @MockBean private MpAuthorMapper authorMapper;
    @MockBean private MpPublicationMapper publicationMapper;
    @MockBean private MpInstallMapper installMapper;
    @MockBean private MpReviewMapper reviewMapper;
    @MockBean private MpCategoryMapper categoryMapper;
    @MockBean private MpTagMapper tagMapper;
    @MockBean private AgentHubClient agentHubClient;
    // W80 P2:社区模块只读 mapper / 订阅 mapper / 社区 Feign 客户端 ——
    // @EnableCustomConfig → @MapperScan 把 mapper 注册成 bean,@WebMvcTest 不启 MyBatis
    // (缺 sqlSessionFactory);Feign 同理没有 FeignClientFactory。缺任何一个都起不来。
    @MockBean private CommunityModuleMapper communityModuleMapper;
    @MockBean private MpSubscriptionMapper subscriptionMapper;
    @MockBean private CommunityClient communityClient;

    private MpTag sample(long id, String name, String category) {
        return MpTag.builder().id(id).name(name).category(category).sortOrder(1).build();
    }

    private List<MpTag> samples() {
        return List.of(
                sample(1L, "高质量", "quality"),
                sample(2L, "多语言", "lang"),
                sample(3L, "按量计费", "billing"));
    }

    // ==================== GET /list ====================

    @Test
    @DisplayName("list — 无 category 参数: 走 service.list() 全量, 不调 listByCategory")
    void list_noParam_returnsAll() throws Exception {
        when(tagService.list()).thenReturn(samples());

        mvc.perform(get("/opc/marketplace/tag/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].category").value("quality"))
                .andExpect(jsonPath("$.data[2].name").value("按量计费"));

        verify(tagService).list();
        verify(tagService, never()).listByCategory(any());
    }

    @Test
    @DisplayName("list — 带 category=quality: 走 service.listByCategory 而非 list()")
    void list_withCategory_returnsFiltered() throws Exception {
        when(tagService.listByCategory("quality")).thenReturn(List.of(sample(1L, "高质量", "quality")));

        mvc.perform(get("/opc/marketplace/tag/list").param("category", "quality"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("高质量"))
                .andExpect(jsonPath("$.data[0].category").value("quality"));

        // 分流正确：过滤参数原样透传给 service
        verify(tagService).listByCategory("quality");
        verify(tagService, never()).list();
    }

    @Test
    @DisplayName("list — 空结果集: 仍 code=200 + data 为空数组(标签无匹配不是错误)")
    void list_empty_returnsOk() throws Exception {
        when(tagService.listByCategory("unknown")).thenReturn(List.of());

        mvc.perform(get("/opc/marketplace/tag/list").param("category", "unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("list — category 为空串(?category=): 同样回落全量, 不打 category='' 查询")
    void list_emptyStringParam_fallsBackToAll() throws Exception {
        when(tagService.list()).thenReturn(samples());

        mvc.perform(get("/opc/marketplace/tag/list").param("category", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(3));

        // 「清空筛选」不能退化成 WHERE category='' 的空结果
        verify(tagService).list();
        verify(tagService, never()).listByCategory(any());
    }
}