package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.security.handler.GlobalExceptionHandler;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpReview;
import com.ruoyi.opc.marketplace.dto.ReviewCreateRequest;
import com.ruoyi.opc.marketplace.dto.ReviewUpdateRequest;
import com.ruoyi.opc.marketplace.feign.AgentHubClient;
import com.ruoyi.opc.marketplace.mapper.MpAuthorMapper;
import com.ruoyi.opc.marketplace.mapper.MpCategoryMapper;
import com.ruoyi.opc.marketplace.mapper.MpInstallMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.mapper.MpReviewMapper;
import com.ruoyi.opc.marketplace.mapper.MpTagMapper;
import com.ruoyi.opc.marketplace.service.ReviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ReviewController} 集成测试（{@code @WebMvcTest}）— P1 Task 7
 *
 * <p>8 个场景，三类契约：
 * <ol>
 *   <li><b>正向路径</b>：create / update / delete / list 全部 200 透传</li>
 *   <li><b>业务异常透传</b>：404 商品不存在 / 409 已评价 / 403 非本人 全部走
 *       GlobalExceptionHandler → HTTP 200 + JSON code=N（W7 修复语义）</li>
 *   <li><b>租户 fail-closed</b>：{@code SecurityUtils.getCompanyId() == null} → 400，
 *       <b>绝不</b>用 0 兜底，绝不让 service 入空对象到 mapper</li>
 * </ol>
 *
 * <p>{@code @WebMvcTest} 装配照抄 {@link PublicationControllerTest}/{@link InstallControllerTest}：
 * {@code excludeFilters} 排除整个 mapper 包 + 6 个 mapper 全部 {@code @MockBean}
 * 占位（{@code @MapperScan} 会注册成 bean，需占位避免 {@code @WebMvcTest} 启动失败）
 * + {@code AgentHubClient} 也 {@code @MockBean}（Feign 在单元测试里需要 mock）。
 *
 * <p><b>scope 决策</b>：与本模块其他 controller 测试一致 —— 在 controller 测试里
 * <b>mock service</b>，service 层细节（归属校验分支、avg 回写、最多一条评价约束）由后续
 * {@code ReviewServiceImplTest} 覆盖。本类只关注 HTTP 契约 + 租户 id 来源 + 异常透传。
 *
 * @author OAC
 */
@WebMvcTest(controllers = ReviewController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.ruoyi\\.opc\\.marketplace\\.mapper\\..*"))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "spring.main.banner-mode=off",
        "logging.level.root=ERROR",
        // bootstrap.yml 在 application.yml 之前处理,以下两个值必须在 @TestPropertySource
        // 显式注入,否则 Nacos config import 的 ${spring.profiles.active} / ${spring.application.name}
        // 都会以字面量形式拼出非法 URI(详见 W80 P1 部署固化)
        "spring.profiles.active=test",
        "spring.application.name=opc-marketplace"
})
class ReviewControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ReviewService reviewService;

    @MockBean private MpAuthorMapper authorMapper;
    @MockBean private MpPublicationMapper publicationMapper;
    @MockBean private MpInstallMapper installMapper;
    @MockBean private MpReviewMapper reviewMapper;
    @MockBean private MpCategoryMapper categoryMapper;
    @MockBean private MpTagMapper tagMapper;
    @MockBean private AgentHubClient agentHubClient;

    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        securityUtils = mockStatic(SecurityUtils.class);
        // 默认 userId=10,与各「本人」测试保持一致;companyId 由 loginAsReviewer 设置
        securityUtils.when(SecurityUtils::getUserId).thenReturn(10L);
    }

    @AfterEach
    void tearDown() {
        if (securityUtils != null) {
            securityUtils.close();
        }
    }

    /** 已登录 + 有租户(companyId=100),userId=10 */
    private void loginAsReviewer() {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(100L);
    }

    private MpReview review(long id, long publicationId, long userId, int rating) {
        return MpReview.builder().id(id).publicationId(publicationId).userId(userId)
                .rating(rating).content("好").status("VISIBLE").build();
    }

    private static final String CREATE_BODY = """
        {"publicationId":1,"rating":5,"content":"很好"}
        """;
    private static final String UPDATE_BODY = """
        {"rating":3,"content":"改后"}
        """;

    // ==================== POST / (create) ====================

    @Test
    @DisplayName("create — 正常路径: service.create 三参(userId+companyId+req)透传")
    void create_returnsReview() throws Exception {
        loginAsReviewer();
        MpReview r = review(99L, 1L, 10L, 5);
        when(reviewService.create(any(ReviewCreateRequest.class), eq(10L), eq(100L))).thenReturn(r);

        mvc.perform(post("/opc/marketplace/review")
                        .contentType("application/json").content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(99))
                .andExpect(jsonPath("$.data.publicationId").value(1))
                .andExpect(jsonPath("$.data.rating").value(5));

        verify(reviewService).create(any(ReviewCreateRequest.class), eq(10L), eq(100L));
    }

    @Test
    @DisplayName("create — 商品不存在: service 抛 OpcException(404) → HTTP 200 + JSON code=404")
    void create_404_whenPublicationMissing() throws Exception {
        loginAsReviewer();
        when(reviewService.create(any(ReviewCreateRequest.class), anyLong(), anyLong()))
                .thenThrow(new OpcException(404, "商品不存在", "MARKETPLACE"));

        mvc.perform(post("/opc/marketplace/review")
                        .contentType("application/json").content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("商品不存在"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("create — 已评价: service 抛 OpcException(409) → HTTP 200 + JSON code=409")
    void create_409_whenAlreadyReviewed() throws Exception {
        loginAsReviewer();
        when(reviewService.create(any(ReviewCreateRequest.class), anyLong(), anyLong()))
                .thenThrow(new OpcException(409, "已评价，请勿重复", "MARKETPLACE"));

        mvc.perform(post("/opc/marketplace/review")
                        .contentType("application/json").content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value("已评价，请勿重复"));
    }

    // ==================== PUT /{id} (update) ====================

    @Test
    @DisplayName("update — 正常路径: 本人 userId=10, service.update 透传")
    void update_returnsReview() throws Exception {
        MpReview r = review(7L, 1L, 10L, 3);
        when(reviewService.update(eq(7L), any(ReviewUpdateRequest.class), eq(10L))).thenReturn(r);

        mvc.perform(put("/opc/marketplace/review/7")
                        .contentType("application/json").content(UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.rating").value(3));

        verify(reviewService).update(eq(7L), any(ReviewUpdateRequest.class), eq(10L));
    }

    @Test
    @DisplayName("update — 非本人 (他人评价): service 抛 OpcException(403) → HTTP 200 + JSON code=403")
    void update_403_whenNotOwner() throws Exception {
        when(reviewService.update(eq(7L), any(ReviewUpdateRequest.class), anyLong()))
                .thenThrow(new OpcException(403, "只有本人可修改", "MARKETPLACE"));

        mvc.perform(put("/opc/marketplace/review/7")
                        .contentType("application/json").content(UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("只有本人可修改"));
    }

    // ==================== DELETE /{id} (delete) ====================

    @Test
    @DisplayName("delete — 正常路径: 本人 userId=10, service.delete 透传 → 200")
    void delete_returnsOk() throws Exception {
        mvc.perform(delete("/opc/marketplace/review/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(reviewService).delete(7L, 10L);
    }

    @Test
    @DisplayName("delete — 非本人: service 抛 OpcException(403) → HTTP 200 + JSON code=403")
    void delete_403_whenNotOwner() throws Exception {
        org.mockito.Mockito.doThrow(new OpcException(403, "只有本人可删除", "MARKETPLACE"))
                .when(reviewService).delete(anyLong(), anyLong());

        mvc.perform(delete("/opc/marketplace/review/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("只有本人可删除"));
    }

    // ==================== GET /list/{publicationId} (list) ====================

    @Test
    @DisplayName("list — 正常路径: 透传 service.listByPublication 的分页信封")
    void list_returnsPage() throws Exception {
        MpReview r1 = review(1L, 5L, 10L, 5);
        MpReview r2 = review(2L, 5L, 11L, 3);
        when(reviewService.listByPublication(eq(5L), eq(1), eq(10))).thenReturn(Map.of(
                "rows", List.of(r1, r2), "total", 2, "page", 1, "pageSize", 10));

        mvc.perform(get("/opc/marketplace/review/list/5").param("page", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.rows.length()").value(2))
                .andExpect(jsonPath("$.data.rows[0].rating").value(5))
                .andExpect(jsonPath("$.data.rows[1].rating").value(3));

        verify(reviewService).listByPublication(5L, 1, 10);
    }

    // ==================== create 租户 fail-closed (核心回归) ====================

    @Test
    @DisplayName("create — 无租户: fail-closed 400,禁止用 companyId=0 兜底(Plan Task6 前置说明)")
    void create_400_whenNoTenant() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(null);

        mvc.perform(post("/opc/marketplace/review")
                        .contentType("application/json").content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("当前账号未关联企业，请先加入或创建企业"))
                .andExpect(jsonPath("$.data").doesNotExist());

        // service.create 永远不该被调到 —— 入参 companyId=null 必须被 controller 拦下
        verify(reviewService, never()).create(any(), any(), any());
    }

    // ==================== Task 7 P1: code-review 修补回归 ====================

    /**
     * I1 守卫的 HTTP 契约测试。service 是 mock,所以只校验 service 抛 OpcException(400,
     * "商品未发布，无法评价") 时响应信封走的是 W7 后的 code=400 + 业务 msg(而不是 500)。
     * 真正的 PUBLISHED 状态检查在 ReviewServiceImplTest(Task 8.5)。
     */
    @Test
    @DisplayName("create — 商品未发布: service 抛 400 → HTTP 200 + JSON code=400 + 业务 msg")
    void create_400_whenPublicationNotPublished() throws Exception {
        loginAsReviewer();
        when(reviewService.create(any(ReviewCreateRequest.class), anyLong(), anyLong()))
                .thenThrow(new OpcException(400, "商品未发布，无法评价", "MARKETPLACE"));

        mvc.perform(post("/opc/marketplace/review")
                        .contentType("application/json").content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("商品未发布，无法评价"));
    }

    /**
     * I2 clamp HTTP 契约测试。<b>声明职责</b>:controller 只做 HTTP → service 的参数转发,
     * 实际 clamp(page=0→1, pageSize=99999→100)在 service 实现里。本测试在 controller
     * slice 验证两条契约:
     * <ol>
     *   <li>controller 把 {@code ?page=0&pageSize=99999} 原样转发到 service(clamp 在 service
     *       里,不在 controller)</li>
     *   <li>service mock 返回的 envelope 能正确序列化回 JSON</li>
     * </ol>
     * 严格的「pageSize > 100 → 100」业务 clamp 由后续 ReviewServiceImplTest(Task 8.5)在
     * service 单测中独立覆盖 —— controller 替身测试不可能校验 service 内部 clamp 逻辑。
     */
    @Test
    @DisplayName("list — controller 透传 page/pageSize 到 service(clamp 在 service 实现层)")
    void list_clampsPageSize() throws Exception {
        when(reviewService.listByPublication(eq(5L), anyInt(), anyInt())).thenReturn(Map.of(
                "rows", List.of(), "total", 0, "page", 1, "pageSize", 100));

        mvc.perform(get("/opc/marketplace/review/list/5")
                        .param("page", "0")
                        .param("pageSize", "99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(100));

        // controller 不夹,原样转发 ——
        // clamp(page=0→1, pageSize=99999→100) 由 ReviewServiceImpl.listByPublication 内部完成,
        // 不在本 controller 测试职责内(业务单测 Task 8.5 覆盖)。
        ArgumentCaptor<Integer> pageCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(reviewService).listByPublication(eq(5L), pageCaptor.capture(), sizeCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(0, pageCaptor.getValue(),
                "controller 原样转发 page=0 到 service —— clamp 由 service 实现(对账基线)");
        org.junit.jupiter.api.Assertions.assertEquals(99999, sizeCaptor.getValue(),
                "controller 原样转发 pageSize=99999 到 service —— clamp 由 service 实现(对账基线)");
    }
}
