package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.security.handler.GlobalExceptionHandler;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpAuthor;
import com.ruoyi.opc.marketplace.domain.MpPublication;
import com.ruoyi.opc.marketplace.dto.PublicationCreateRequest;
import com.ruoyi.opc.marketplace.dto.PublicationEditRequest;
import com.ruoyi.opc.marketplace.dto.PublicationVO;
import com.ruoyi.opc.marketplace.mapper.MpAuthorMapper;
import com.ruoyi.opc.marketplace.mapper.MpCategoryMapper;
import com.ruoyi.opc.marketplace.mapper.MpInstallMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.mapper.MpReviewMapper;
import com.ruoyi.opc.marketplace.mapper.MpTagMapper;
import com.ruoyi.opc.marketplace.service.AuthorService;
import com.ruoyi.opc.marketplace.service.PublicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link PublicationController} 集成测试（{@code @WebMvcTest}）— P1 Task 5
 *
 * <p>{@code @WebMvcTest} 的装配照抄 {@link AuthorControllerTest}（该模块特有）：
 * {@code @AutoConfigureMockMvc(addFilters=false)} + {@code @Import(GlobalExceptionHandler.class)}
 * + 6 个 mapper 全部 {@code @MockBean} + {@code @TestPropertySource} 显式给
 * {@code spring.profiles.active} / {@code spring.application.name}（bootstrap.yml 先于 application.yml 处理）。
 *
 * <p>本类重点证明 4 条安全/正确性契约：
 * <ul>
 *   <li>公开详情走 {@code getPublishedById}，<b>绝不</b>走不过滤 status 的 {@code getById}</li>
 *   <li>作者读自己的 DRAFT 走 {@code /my/{id}}，且非本人 403</li>
 *   <li>所有租户维度入口在 {@code companyId == null} 时 fail-closed（400），不用 0 兜底</li>
 *   <li>未入驻（非作者）时一律 403，绝不让 author 为 null 变成 500</li>
 *   <li>写操作把 <b>authorId（=10，来自 companyId 解析）</b>与 <b>operatorId（=1，来自
 *       {@code SecurityUtils.getUserId()}）</b>分两个参数传下去 —— 混用会让审计列失真</li>
 * </ul>
 *
 * @author OAC
 */
@WebMvcTest(controllers = PublicationController.class,
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
class PublicationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PublicationService publicationService;
    @MockBean
    private AuthorService authorService;

    @MockBean private MpAuthorMapper authorMapper;
    @MockBean private MpPublicationMapper publicationMapper;
    @MockBean private MpInstallMapper installMapper;
    @MockBean private MpReviewMapper reviewMapper;
    @MockBean private MpCategoryMapper categoryMapper;
    @MockBean private MpTagMapper tagMapper;

    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getUserId).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        if (securityUtils != null) {
            securityUtils.close();
        }
    }

    /** 已登录 + 有租户 + 已入驻（companyId=100 → authorId=10） */
    private void loginAsAuthor() {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(100L);
        when(authorService.getByCompanyId(100L)).thenReturn(MpAuthor.builder().id(10L).build());
    }

    private MpPublication pub(long id, long authorId, String status) {
        return MpPublication.builder().id(id).authorId(authorId).code("c" + id)
                .name("商品" + id).productType("AGENT").categoryCode("office").status(status).build();
    }

    private PublicationVO vo(long id, String status) {
        PublicationVO vo = new PublicationVO();
        vo.setId(id);
        vo.setName("商品" + id);
        vo.setStatus(status);
        return vo;
    }

    private static final String CREATE_BODY = """
        {"productType":"AGENT","agentDefinitionId":100,"code":"agent-1",
         "name":"智能体一","categoryCode":"office"}
        """;

    // ==================== 公开读 ====================

    @Test
    @DisplayName("list — 正常路径: 透传 service.list 的分页信封")
    void list_returnsPage() throws Exception {
        when(publicationService.list(any())).thenReturn(Map.of(
                "rows", List.of(Map.of("id", 1)), "total", 1, "page", 1, "pageSize", 12));

        mvc.perform(get("/opc/marketplace/publication/list").param("page", "1").param("pageSize", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.rows[0].id").value(1));
    }

    @Test
    @DisplayName("search — 与 list 同一条路径(同一个 service.list)")
    void search_delegatesToList() throws Exception {
        when(publicationService.list(any())).thenReturn(Map.of("rows", List.of(), "total", 0));

        mvc.perform(get("/opc/marketplace/publication/search").param("keyword", "客服"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(publicationService).list(any());
    }

    @Test
    @DisplayName("get — 公开详情必须走 getPublishedById(不过滤 status 的 getById 会泄露草稿)")
    void get_usesPublishedOnlyLookup() throws Exception {
        MpPublication p = pub(1L, 10L, "PUBLISHED");
        when(publicationService.getPublishedById(1L)).thenReturn(p);
        when(publicationService.toVO(p)).thenReturn(vo(1L, "PUBLISHED"));

        mvc.perform(get("/opc/marketplace/publication/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        verify(publicationService, never()).getById(1L);
    }

    @Test
    @DisplayName("get — id 不存在/未发布 → code=404(RuoYi 错误也返回 HTTP 200,必须断言 body.code)")
    void get_404_whenNotFound() throws Exception {
        when(publicationService.getPublishedById(999L))
                .thenThrow(new OpcException(404, "商品不存在: 999", "MARKETPLACE"));

        mvc.perform(get("/opc/marketplace/publication/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ==================== 作者读 ====================

    @Test
    @DisplayName("myGet — 作者读自己的 DRAFT: 走 getById(不受 PUBLISHED 过滤),编辑表单才填得满")
    void myGet_returnsVO_forOwnDraft() throws Exception {
        loginAsAuthor();
        MpPublication p = pub(1L, 10L, "DRAFT");
        when(publicationService.getById(1L)).thenReturn(p);
        when(publicationService.toVO(p)).thenReturn(vo(1L, "DRAFT"));

        mvc.perform(get("/opc/marketplace/publication/my/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(publicationService).getById(1L);
        verify(publicationService, never()).getPublishedById(any());
    }

    @Test
    @DisplayName("myGet — 别人的 DRAFT → 403,且响应体不含商品名(不泄露存在性)")
    void myGet_403_whenNotOwner() throws Exception {
        loginAsAuthor();
        when(publicationService.getById(2L)).thenReturn(pub(2L, 999L, "DRAFT"));

        String body = mvc.perform(get("/opc/marketplace/publication/my/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("商品2");
        // 403 时不得再装配 VO
        verify(publicationService, never()).toVO(any());
    }

    @Test
    @DisplayName("myList — 作者列表走 listByAuthorVOs(批量装配)")
    void myList_returnsVOs() throws Exception {
        loginAsAuthor();
        when(publicationService.listByAuthorVOs(10L)).thenReturn(List.of(vo(1L, "DRAFT"), vo(2L, "PUBLISHED")));

        mvc.perform(get("/opc/marketplace/publication/my/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.data[1].status").value("PUBLISHED"));

        verify(publicationService).listByAuthorVOs(10L);
    }

    @Test
    @DisplayName("myList — 已登录但未入驻 → 200 空列表(不是错误),不查商品表")
    void myList_empty_whenNotAuthor() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(100L);
        when(authorService.getByCompanyId(100L)).thenReturn(null);

        mvc.perform(get("/opc/marketplace/publication/my/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(publicationService, never()).listByAuthorVOs(any());
    }

    // ==================== 写 ====================

    @Test
    @DisplayName("create — 正常路径: authorId 来自登录租户解析出的作者档案, operatorId 来自登录用户")
    void create_returnsVO() throws Exception {
        loginAsAuthor();
        MpPublication p = pub(5L, 10L, "DRAFT");
        when(publicationService.create(any(PublicationCreateRequest.class), eq(10L), eq(1L))).thenReturn(p);
        when(publicationService.toVO(p)).thenReturn(vo(5L, "DRAFT"));

        mvc.perform(post("/opc/marketplace/publication")
                        .contentType("application/json").content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(publicationService).create(any(PublicationCreateRequest.class), eq(10L), eq(1L));
    }

    @Test
    @DisplayName("create — 未入驻 → 403「请先申请成为作者」,不落到任何 author 下")
    void create_403_whenNotAuthor() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(100L);
        when(authorService.getByCompanyId(100L)).thenReturn(null);

        mvc.perform(post("/opc/marketplace/publication")
                        .contentType("application/json").content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("请先申请成为作者"));

        verify(publicationService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("create — 无租户: fail-closed 400,禁止用 companyId=0 兜底")
    void create_400_whenNoTenant() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(null);

        mvc.perform(post("/opc/marketplace/publication")
                        .contentType("application/json").content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("当前账号未关联企业，请先加入或创建企业"));

        verify(authorService, never()).getByCompanyId(any());
        verify(publicationService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("create — code 撞唯一键 → code=409")
    void create_409_whenCodeExists() throws Exception {
        loginAsAuthor();
        when(publicationService.create(any(), any(), any()))
                .thenThrow(new OpcException(409, "商品 code 已存在: agent-1", "MARKETPLACE"));

        mvc.perform(post("/opc/marketplace/publication")
                        .contentType("application/json").content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value("商品 code 已存在: agent-1"));
    }

    @Test
    @DisplayName("create — JSR-303 校验失败(缺 code): service 一次都不被调用")
    void create_400_whenValidationFails() throws Exception {
        loginAsAuthor();
        String body = """
            {"productType":"AGENT","agentDefinitionId":100,"name":"无 code","categoryCode":"office"}
            """;

        // 注:GlobalExceptionHandler.handleMethodArgumentNotValidException 返回 AjaxResult.error(msg),
        // 仓库全局惯例是 HTTP 200 + code=500(非 400)。此处只断言「校验消息透出 + service 未被调用」,
        // 不锁死 500 这个仓库级 wart(改动它要动全部 OPC 模块,超出 Task 5 范围)。
        mvc.perform(post("/opc/marketplace/publication")
                        .contentType("application/json").content(body))
                .andExpect(jsonPath("$.msg").value("code 必填"));

        verify(publicationService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("edit — 正常路径: authorId 来自登录租户解析, operatorId 来自登录用户(两者不同源)")
    void edit_returnsVO() throws Exception {
        loginAsAuthor();
        MpPublication p = pub(1L, 10L, "DRAFT");
        PublicationVO edited = vo(1L, "DRAFT");
        edited.setName("新名字");
        when(publicationService.edit(eq(1L), any(PublicationEditRequest.class), eq(10L), eq(1L))).thenReturn(p);
        when(publicationService.toVO(p)).thenReturn(edited);

        mvc.perform(put("/opc/marketplace/publication/1")
                        .contentType("application/json").content("{\"name\":\"新名字\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("新名字"));

        verify(publicationService).edit(eq(1L), any(PublicationEditRequest.class), eq(10L), eq(1L));
    }

    @Test
    @DisplayName("edit — 非本人 → 403(service 归属校验透传到响应)")
    void edit_403_whenOtherAuthorsPublication() throws Exception {
        loginAsAuthor();
        when(publicationService.edit(eq(2L), any(PublicationEditRequest.class), eq(10L), eq(1L)))
                .thenThrow(new OpcException(403, "只有作者本人可编辑", "MARKETPLACE"));

        mvc.perform(put("/opc/marketplace/publication/2")
                        .contentType("application/json").content("{\"name\":\"我要改别人的\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("只有作者本人可编辑"));
    }

    @Test
    @DisplayName("edit — 无租户: fail-closed 400,不进入 service")
    void edit_400_whenNoTenant() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(null);

        mvc.perform(put("/opc/marketplace/publication/1")
                        .contentType("application/json").content("{\"name\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(publicationService, never()).edit(any(), any(), any(), any());
    }

    @Test
    @DisplayName("submitAudit — DRAFT → PUBLISHED")
    void submitAudit_returnsPublished() throws Exception {
        loginAsAuthor();
        MpPublication p = pub(1L, 10L, "PUBLISHED");
        when(publicationService.submitAudit(eq(1L), eq(10L), eq(1L))).thenReturn(p);
        when(publicationService.toVO(p)).thenReturn(vo(1L, "PUBLISHED"));

        mvc.perform(post("/opc/marketplace/publication/1/submit-audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("submitAudit — 未入驻 → 403(计划原文在此处会 NPE→500,本实现 fail-closed)")
    void submitAudit_403_whenNotAuthor() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(100L);
        when(authorService.getByCompanyId(100L)).thenReturn(null);

        mvc.perform(post("/opc/marketplace/publication/1/submit-audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("请先申请成为作者"));

        verify(publicationService, never()).submitAudit(any(), any(), any());
    }

    @Test
    @DisplayName("submitAudit — 无租户: fail-closed 400")
    void submitAudit_400_whenNoTenant() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(null);

        mvc.perform(post("/opc/marketplace/publication/1/submit-audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(publicationService, never()).submitAudit(any(), any(), any());
    }

    @Test
    @DisplayName("online — 重新上架: DEPRECATED → PUBLISHED")
    void online_republishes() throws Exception {
        loginAsAuthor();
        MpPublication p = pub(1L, 10L, "PUBLISHED");
        when(publicationService.online(eq(1L), eq(10L), eq(1L))).thenReturn(p);
        when(publicationService.toVO(p)).thenReturn(vo(1L, "PUBLISHED"));

        mvc.perform(post("/opc/marketplace/publication/1/online"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("online — 未入驻 → 403")
    void online_403_whenNotAuthor() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(100L);
        when(authorService.getByCompanyId(100L)).thenReturn(null);

        mvc.perform(post("/opc/marketplace/publication/1/online"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(publicationService, never()).online(any(), any(), any());
    }

    @Test
    @DisplayName("offline — PUBLISHED → DEPRECATED")
    void offline_returnsDeprecated() throws Exception {
        loginAsAuthor();
        MpPublication p = pub(1L, 10L, "DEPRECATED");
        when(publicationService.offline(eq(1L), eq(10L), eq(1L))).thenReturn(p);
        when(publicationService.toVO(p)).thenReturn(vo(1L, "DEPRECATED"));

        mvc.perform(post("/opc/marketplace/publication/1/offline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("DEPRECATED"));
    }

    @Test
    @DisplayName("offline — 无租户: fail-closed 400")
    void offline_400_whenNoTenant() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(null);

        mvc.perform(post("/opc/marketplace/publication/1/offline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(publicationService, never()).offline(any(), any(), any());
    }

    @Test
    @DisplayName("下架后公开详情立刻 404(service 层 selectPublishedById 过滤 DEPRECATED)")
    void offline_then_publicDetail_404() throws Exception {
        // 端到端语义:offline 只写 DEPRECATED,公开端点的 SQL 层过滤自然把它挡掉
        when(publicationService.offline(eq(1L), eq(10L), eq(1L))).thenReturn(pub(1L, 10L, "DEPRECATED"));
        when(publicationService.getPublishedById(1L))
                .thenThrow(new OpcException(404, "商品不存在: 1", "MARKETPLACE"));
        when(publicationService.toVO(any())).thenReturn(vo(1L, "DEPRECATED"));
        loginAsAuthor();

        mvc.perform(post("/opc/marketplace/publication/1/offline"))
                .andExpect(jsonPath("$.data.status").value("DEPRECATED"));
        mvc.perform(get("/opc/marketplace/publication/1"))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("租户隔离: A 公司的作者拿不到 B 公司作者的 authorId —— 全部写路径只认登录态解析出的档案")
    void tenantIsolation_neverUsesForeignAuthorId() throws Exception {
        // companyId=100 → authorId=10;companyId=999 也可能存在作者档案(authorId=20)
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(100L);
        when(authorService.getByCompanyId(100L)).thenReturn(MpAuthor.builder().id(10L).build());
        when(authorService.getByCompanyId(999L)).thenReturn(MpAuthor.builder().id(20L).build());

        MpPublication p = pub(1L, 10L, "DRAFT");
        when(publicationService.create(any(), any(), any())).thenReturn(p);
        when(publicationService.toVO(p)).thenReturn(vo(1L, "DRAFT"));

        mvc.perform(post("/opc/marketplace/publication")
                .contentType("application/json").content(CREATE_BODY));

        // 请求体即便带 authorId/authorCompanyId,也只会落到自己的 authorId=10
        verify(publicationService).create(any(), eq(10L), eq(1L));
        verify(publicationService, never()).create(any(), eq(20L), anyLong());
        verify(authorService, never()).getByCompanyId(eq(999L));
    }
}
