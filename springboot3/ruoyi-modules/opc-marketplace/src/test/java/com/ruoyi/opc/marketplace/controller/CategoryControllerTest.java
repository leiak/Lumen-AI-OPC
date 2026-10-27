package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.security.handler.GlobalExceptionHandler;
import com.ruoyi.opc.marketplace.domain.MpCategory;
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
import com.ruoyi.opc.marketplace.service.CategoryService;
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
 * {@link CategoryController} 集成测试（{@code @WebMvcTest}）— P1 Task 8
 *
 * <p>5 个场景：
 * <ul>
 *   <li>{@code tree_returnsList}          — GET /tree 透传 service.tree()</li>
 *   <li>{@code list_noParam_returnsAll}   — GET /list 无参 → 回落 tree()（不调 listByType）</li>
 *   <li>{@code list_withProductType_returnsList} — GET /list?productType= 走 listByType</li>
 *   <li>{@code get_returnsCategory}       — GET /{id} 命中内存过滤</li>
 *   <li>{@code get_404_whenMissing}        — GET /{id} 未命中 → OpcException(404) → JSON code=404</li>
 * </ul>
 *
 * <p>装配同 {@link AuthorControllerTest}：{@code @WebMvcTest(controllers=...)} +
 * {@code @AutoConfigureMockMvc(addFilters=false)} + {@code @Import(GlobalExceptionHandler.class)}
 * + 6 个 mapper / {@link AgentHubClient} {@code @MockBean} 占位（{@code @EnableCustomConfig}
 * → {@code @MapperScan} 会把它们注册成 bean，{@code @WebMvcTest} 不启 MyBatis）。
 *
 * <p><b>本测试类刻意不 mockStatic(SecurityUtils)</b>：类目是全局参考数据，两个 controller
 * 都没有任何 {@code SecurityUtils.getCompanyId()} 调用（公开读，无需租户守卫）。这也顺带
 * 锁住一条回归 —— 有人「顺手」给公开的类目接口加上 fail-closed 租户守卫时，本类仍然全绿，
 * 因为它压根不碰登录态；真要加守卫必须连同租户测试一起改。
 *
 * @author OAC
 */
@WebMvcTest(controllers = CategoryController.class,
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
class CategoryControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CategoryService categoryService;

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

    private MpCategory sample(long id, String productType, String code, String name) {
        return MpCategory.builder()
                .id(id).parentId(null).productType(productType).code(code).name(name)
                .sortOrder(1).build();
    }

    private List<MpCategory> samples() {
        return List.of(
                sample(1L, "AGENT", "CUSTOMER_SERVICE", "客服"),
                sample(2L, "AGENT", "FINANCE", "财务"),
                sample(3L, "MODULE", "OA", "协同办公"));
    }

    // ==================== GET /tree ====================

    @Test
    @DisplayName("tree — 正常路径: service.tree() 透传 → HTTP 200 + JSON code=200")
    void tree_returnsList() throws Exception {
        when(categoryService.tree()).thenReturn(samples());

        mvc.perform(get("/opc/marketplace/category/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].code").value("CUSTOMER_SERVICE"))
                .andExpect(jsonPath("$.data[2].productType").value("MODULE"));

        // /tree 是公开读，不按类型过滤
        verify(categoryService, never()).listByType(any());
    }

    // ==================== GET /list ====================

    @Test
    @DisplayName("list — 无参: 回落 service.tree() 全量, 不调 listByType")
    void list_noParam_returnsAll() throws Exception {
        when(categoryService.tree()).thenReturn(samples());

        mvc.perform(get("/opc/marketplace/category/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(3));

        verify(categoryService).tree();
        verify(categoryService, never()).listByType(any());
    }

    @Test
    @DisplayName("list — 带 productType=AGENT: 走 listByType 而非 tree()")
    void list_withProductType_returnsList() throws Exception {
        when(categoryService.listByType("AGENT")).thenReturn(
                List.of(sample(1L, "AGENT", "CUSTOMER_SERVICE", "客服"),
                        sample(2L, "AGENT", "FINANCE", "财务")));

        mvc.perform(get("/opc/marketplace/category/list").param("productType", "AGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].productType").value("AGENT"));

        // 分流正确：过滤走 listByType，tree() 一次都不该被调
        verify(categoryService).listByType("AGENT");
        verify(categoryService, never()).tree();
    }

    @Test
    @DisplayName("list — productType 为空串(?productType=): 同样回落全量, 不打 product_type='' 查询")
    void list_emptyStringParam_fallsBackToAll() throws Exception {
        when(categoryService.tree()).thenReturn(samples());

        mvc.perform(get("/opc/marketplace/category/list").param("productType", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(3));

        // 「清空筛选」不能退化成 WHERE product_type='' 的空结果
        verify(categoryService).tree();
        verify(categoryService, never()).listByType(any());
    }

    // ==================== GET /{id} ====================

    @Test
    @DisplayName("get — 正常路径: 从 tree() 结果按 id 命中 → HTTP 200 + 透出该类目")
    void get_returnsCategory() throws Exception {
        when(categoryService.tree()).thenReturn(samples());

        mvc.perform(get("/opc/marketplace/category/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.code").value("FINANCE"))
                .andExpect(jsonPath("$.data.name").value("财务"));
    }

    @Test
    @DisplayName("get — 类目不存在: controller 抛 OpcException(404) → HTTP 200 + JSON code=404 (W7 修复语义)")
    void get_404_whenMissing() throws Exception {
        when(categoryService.tree()).thenReturn(samples());

        mvc.perform(get("/opc/marketplace/category/9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("类目不存在"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}