package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.security.handler.GlobalExceptionHandler;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpAuthor;
import com.ruoyi.opc.marketplace.dto.AuthorApplyRequest;
import com.ruoyi.opc.marketplace.dto.AuthorListQuery;
import com.ruoyi.opc.marketplace.dto.AuthorVO;
import com.ruoyi.opc.marketplace.feign.AgentHubClient;
import com.ruoyi.opc.marketplace.mapper.MpAuthorMapper;
import com.ruoyi.opc.marketplace.mapper.MpCategoryMapper;
import com.ruoyi.opc.marketplace.mapper.MpInstallMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.mapper.MpReviewMapper;
import com.ruoyi.opc.marketplace.mapper.MpTagMapper;
import com.ruoyi.opc.marketplace.service.AuthorService;
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

import java.math.BigDecimal;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AuthorController} 集成测试（{@code @WebMvcTest}）— P1 Task 4
 *
 * <p>覆盖 13 个场景：
 * <ul>
 *   <li>{@code apply_returnsAuthor} — POST /apply 正常路径（200 + JSON code=200）</li>
 *   <li>{@code apply_ignoresBodyAuthorCompanyId_usesLoginTenant} — 越权回归：请求体冒名别家公司无效，只按登录态申请</li>
 *   <li>{@code apply_failsClosed_whenNoCompany} — 无公司上下文 → code=400，且不写入任何公司</li>
 *   <li>{@code get_returnsAuthor}   — GET /{id} 正常路径，且必须走 {@code toPublicVO}</li>
 *   <li>{@code get_publicProfile_redactsContactFields} — 公开端点脱敏：邮箱/电话/营收的值不出现在响应体任意位置</li>
 *   <li>{@code me_ignoresRequestParamCompanyId_usesLoginTenant} — IDOR 回归：请求里带 {@code ?companyId=} 无效，只用登录态</li>
 *   <li>{@code me_failsClosed_whenNoCompany} — 无公司上下文 → code=400 fail-closed</li>
 *   <li>{@code me_returnsNull_whenNotAuthorYet} — 已登录但未入驻 → code=200 + data 缺失</li>
 *   <li>{@code me_returnsAuthor_whenExists} — 已入驻 → code=200 + 档案有内容</li>
 *   <li>{@code apply_409_whenAlreadyAuthor} — service 抛 OpcException(409) → GlobalExceptionHandler → HTTP 200 + JSON code=409（W7 修复：OpcException 继承 ServiceException 后尊重 code 字段）</li>
 *   <li>{@code list_returnsPage} — GET /list query 绑定并透传 rows/total 结构</li>
 *   <li>{@code stats_returnsMap} — GET /{id}/stats 透出 author + publicationCount</li>
 *   <li>{@code get_404_whenNotFound} — 作者不存在 → HTTP 200 + JSON code=404</li>
 * </ul>
 *
 * <p>关键模式：
 * <ul>
 *   <li>{@code @WebMvcTest(controllers = AuthorController.class)} 只加载 controller 层</li>
 *   <li>{@code @AutoConfigureMockMvc(addFilters = false)} 绕过 Spring Security 过滤器链 — RuoYi 网关负责鉴权（前置）</li>
 *   <li>{@code @Import(GlobalExceptionHandler.class)} 显式拉入 {@link GlobalExceptionHandler}（位于 ruoyi-common-security 模块，@WebMvcTest 默认不扫描）</li>
 *   <li>{@code excludeFilters} 用 REGEX 排除整个 mapper 包 — 避免 {@code @MapperScan("com.ruoyi.**.mapper")} 在没有 SqlSessionFactory 时启动失败</li>
 *   <li>6 个 mapper 接口全部 {@code @MockBean} — {@code @EnableCustomConfig} → {@code @MapperScan} 把它们注册成 bean，{@code @WebMvcTest} 不启 MyBatis，需要 mock 占位</li>
 *   <li>{@link SecurityUtils} 的 {@code getUserId/getCompanyId} 是静态方法 → {@code mockStatic(SecurityUtils.class)}；
 *       {@code getCompanyId()} 未打桩时返回 null，正好代表「账号未关联企业」</li>
 * </ul>
 *
 * @author OAC
 */
@WebMvcTest(controllers = AuthorController.class,
        // 排除整个 mapper 包,避免 @MapperScan 把 mapper 注册成 bean 后 @WebMvcTest 启动失败
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.ruoyi\\.opc\\.marketplace\\.mapper\\..*"))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "spring.main.banner-mode=off",
        "logging.level.root=ERROR",
        // bootstrap.yml 在 application.yml 之前处理,以下两个值必须在 @TestPropertySource
        // 显式注入,否则 Nacos config import 的 ${spring.profiles.active} / ${spring.application.name}
        // 都会以字面量形式拼出非法 URI(详见 W80 P1 部署固化:application.yml 在 bootstrap 之后加载)
        "spring.profiles.active=test",
        "spring.application.name=opc-marketplace"
})
class AuthorControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthorService authorService;

    // 6 个 mapper 接口 @MockBean 占位 —— EnableCustomConfig → @MapperScan 把它们注册成 bean
    @MockBean private MpAuthorMapper authorMapper;
    @MockBean private MpPublicationMapper publicationMapper;
    @MockBean private MpInstallMapper installMapper;
    @MockBean private MpReviewMapper reviewMapper;
    @MockBean private MpCategoryMapper categoryMapper;
    @MockBean private MpTagMapper tagMapper;
    // W80 P1 Task 6: InstallServiceImpl 依赖 AgentHubClient Feign;
    // @WebMvcTest 默认不初始化 FeignClientsRegistrar → 必须 @MockBean 占位
    @MockBean private AgentHubClient agentHubClient;

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

    private MpAuthor sampleAuthor() {
        return MpAuthor.builder()
                .id(10L).authorCompanyId(100L).authorName("TestAuthor")
                .level("NORMAL").totalAgents(0).totalSubscribers(0)
                .totalRevenue(BigDecimal.ZERO).status("ACTIVE").build();
    }

    // ==================== POST /apply ====================

    @Test
    @DisplayName("apply — 正常路径: service.apply + toVO 都被调 → HTTP 200 + JSON code=200")
    void apply_returnsAuthor() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(100L);
        MpAuthor a = sampleAuthor();
        AuthorVO vo = new AuthorVO();
        vo.setId(10L);
        vo.setAuthorCompanyId(100L);
        vo.setAuthorName("TestAuthor");

        when(authorService.apply(any(AuthorApplyRequest.class), anyLong(), anyLong())).thenReturn(a);
        when(authorService.toVO(a)).thenReturn(vo);

        String body = """
            {
              "authorName": "TestAuthor",
              "contactEmail": "a@b.com"
            }
            """;

        mvc.perform(post("/opc/marketplace/author/apply")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // 验证 VO 字段被透传 (而非只断言 200)
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.authorCompanyId").value(100))
                .andExpect(jsonPath("$.data.authorName").value("TestAuthor"));

        // companyId 来自登录态 100,operatorId 来自登录态 userId=1
        verify(authorService).apply(any(AuthorApplyRequest.class), eq(100L), eq(1L));
    }

    // ==================== POST /apply — 越权回归(请求体不得指定入驻公司) ====================

    @Test
    @DisplayName("apply — 越权回归: 请求体带 authorCompanyId=999 也只按登录态那家(100)申请,DTO 已无该字段")
    void apply_ignoresBodyAuthorCompanyId_usesLoginTenant() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(100L);
        MpAuthor a = sampleAuthor();
        AuthorVO vo = new AuthorVO();
        vo.setId(10L);
        vo.setAuthorCompanyId(100L);

        when(authorService.apply(any(AuthorApplyRequest.class), anyLong(), anyLong())).thenReturn(a);
        when(authorService.toVO(a)).thenReturn(vo);

        // 请求体故意冒名别家公司(authorCompanyId=999)。AuthorApplyRequest 已删除该字段,
        // Spring Boot 默认关闭 FAIL_ON_UNKNOWN_PROPERTIES → 该键被静默忽略,
        // 若有人「顺手把字段加回去」,下面的 verify 会立刻红。
        String body = """
            {
              "authorCompanyId": 999,
              "authorName": "冒名申请",
              "contactEmail": "attacker@evil.com"
            }
            """;

        mvc.perform(post("/opc/marketplace/author/apply")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 直接证据:service 收到的 companyId 是登录态的 100,绝不是请求体里的 999
        verify(authorService).apply(any(AuthorApplyRequest.class), eq(100L), eq(1L));
        verify(authorService, never()).apply(any(AuthorApplyRequest.class), eq(999L), anyLong());

        // 断言 DTO 上根本没有承载别家公司 id 的字段(编译期 + 反射双保险)
        assertThat(AuthorApplyRequest.class.getDeclaredFields())
                .as("AuthorApplyRequest 不得再有任何 companyId 类字段")
                .noneMatch(f -> f.getName().toLowerCase().contains("company"));
    }

    @Test
    @DisplayName("apply — 无公司上下文: fail-closed 返回 code=400,不落到任何公司名下")
    void apply_failsClosed_whenNoCompany() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(null);

        String body = """
            {"authorName": "无租户用户", "contactEmail": "a@b.com"}
            """;

        mvc.perform(post("/opc/marketplace/author/apply")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("当前账号未关联企业，请先加入或创建企业"))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 禁止用 companyId=0 兜底继续写入
        verify(authorService, never()).apply(any(AuthorApplyRequest.class), any(), any());
    }

    // ==================== GET /{id} ====================

    @Test
    @DisplayName("get — 正常路径: service.getById + toPublicVO 都被调 → HTTP 200 + JSON code=200")
    void get_returnsAuthor() throws Exception {
        MpAuthor a = sampleAuthor();
        AuthorVO vo = new AuthorVO();
        vo.setId(10L);
        vo.setAuthorCompanyId(100L);

        when(authorService.getById(10L)).thenReturn(a);
        when(authorService.toPublicVO(a)).thenReturn(vo);

        mvc.perform(get("/opc/marketplace/author/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.authorCompanyId").value(100));
        // 公开端点必须走脱敏闸门,不得回退到 toVO
        verify(authorService, never()).toVO(a);
    }

    // ==================== GET /{id} — 公开脱敏(回归: contactEmail/totalRevenue 曾可匿名读取) ====================

    @Test
    @DisplayName("get — 公开资料脱敏: 响应体(任意嵌套层级)都不含 contactEmail / contactPhone / totalRevenue 的值")
    void get_publicProfile_redactsContactFields() throws Exception {
        MpAuthor a = MpAuthor.builder()
                .id(10L).authorCompanyId(100L).authorName("TestAuthor")
                .contactEmail("secret-boss@example.com")
                .contactPhone("13800001111")
                .intro("hi")
                .level("NORMAL").status("ACTIVE")
                .totalRevenue(new BigDecimal("9876543.21"))
                .build();
        // 模拟 service 脱敏闸门已生效
        AuthorVO redacted = new AuthorVO();
        redacted.setId(10L);
        redacted.setAuthorCompanyId(100L);
        redacted.setAuthorName("TestAuthor");
        redacted.setIntro("hi");
        redacted.setLevel("NORMAL");
        redacted.setStatus("ACTIVE");

        when(authorService.getById(10L)).thenReturn(a);
        when(authorService.toPublicVO(a)).thenReturn(redacted);

        String body = mvc.perform(get("/opc/marketplace/author/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // 安全字段必须还在,否则「全 null」也算通过
                .andExpect(jsonPath("$.data.authorName").value("TestAuthor"))
                .andExpect(jsonPath("$.data.id").value(10))
                // 敏感字段为空(无论被序列化成 null 还是直接被 NON_NULL 省略)
                .andExpect(jsonPath("$.data.contactEmail").doesNotExist())
                .andExpect(jsonPath("$.data.totalRevenue").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // 断言「泄露的值不在响应里」而非只断言「安全字段在场」—— 只查 $.data.authorName
        // 的用例在泄露发生时依然会通过。
        // 上面的 jsonPath(...).doesNotExist() 同时覆盖两种序列化结果：本模块
        // application.yml:50 开了 default-property-inclusion: non_null，脱敏后的字段会被
        // **整个键省略**；万一将来那个开关被关掉，它又会变成 "contactEmail":null ——
        // 两种形状 doesNotExist() 都判通过，所以这个断言不会因为配置漂移而误报。
        assertThat(body)
                .as("公开作者资料不得包含企业邮箱")
                .doesNotContain("secret-boss@example.com")
                .as("公开作者资料不得包含联系电话")
                .doesNotContain("13800001111")
                .as("公开作者资料不得包含营收金额")
                .doesNotContain("9876543.21");
    }

    // ==================== GET /me — 租户身份取自登录态(IDOR 回归) ====================

    @Test
    @DisplayName("me — companyId 取自 SecurityUtils.getCompanyId(): 请求里带 ?companyId=999 也只查登录态那家")
    void me_ignoresRequestParamCompanyId_usesLoginTenant() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(100L);
        MpAuthor a = sampleAuthor();
        AuthorVO vo = new AuthorVO();
        vo.setId(10L);
        vo.setAuthorCompanyId(100L);
        when(authorService.getByCompanyId(100L)).thenReturn(a);
        when(authorService.toVO(a)).thenReturn(vo);

        mvc.perform(get("/opc/marketplace/author/me").param("companyId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(10));

        // 只用登录态的 100,绝不用请求里的 999(IDOR 关闭的直接证据)
        verify(authorService).getByCompanyId(100L);
        verify(authorService, never()).getByCompanyId(999L);
    }

    @Test
    @DisplayName("me — 无公司上下文: fail-closed 返回 code=400,不返回任何作者资料")
    void me_failsClosed_whenNoCompany() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(null);

        String body = mvc.perform(get("/opc/marketplace/author/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("当前账号未关联企业，请先加入或创建企业"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // 未关联企业的账号不得回落到 companyId=0 / 任意 company,也不得泄露他人档案
        verify(authorService, never()).getByCompanyId(any());
        verify(authorService, never()).toVO(any());
        assertThat(body).doesNotContain("authorName").doesNotContain("contactEmail");
    }

    @Test
    @DisplayName("me — 已登录但未入驻: R.ok(null)(code=200,data 缺失),与 400 区分")
    void me_returnsNull_whenNotAuthorYet() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(100L);
        when(authorService.getByCompanyId(100L)).thenReturn(null);

        mvc.perform(get("/opc/marketplace/author/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ==================== POST /apply — 409 异常路径 ====================

    @Test
    @DisplayName("apply — service 抛 OpcException(409) → advice → HTTP 200 + JSON code=409 (W7 修复语义)")
    void apply_409_whenAlreadyAuthor() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(100L);
        when(authorService.apply(any(AuthorApplyRequest.class), anyLong(), anyLong()))
                .thenThrow(new OpcException(409, "该企业已是作者，不可重复申请", "MARKETPLACE"));

        String body = """
            {"authorName": "X", "contactEmail": "a@b.com"}
            """;

        mvc.perform(post("/opc/marketplace/author/apply")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value("该企业已是作者，不可重复申请"));
    }

    // ==================== GET /me — 已入驻的正常路径 ====================

    @Test
    @DisplayName("me — 已入驻: 返回该公司的作者档案(code=200 + data 有内容),companyId 取自登录态")
    void me_returnsAuthor_whenExists() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(100L);
        MpAuthor a = sampleAuthor();
        AuthorVO vo = new AuthorVO();
        vo.setId(10L);
        vo.setAuthorCompanyId(100L);
        vo.setAuthorName("TestAuthor");
        when(authorService.getByCompanyId(100L)).thenReturn(a);
        when(authorService.toVO(a)).thenReturn(vo);

        mvc.perform(get("/opc/marketplace/author/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.authorCompanyId").value(100));

        verify(authorService).getByCompanyId(100L);
    }

    // ==================== GET /list ====================

    @Test
    @DisplayName("list — 作者分页列表: query 绑定成功并原样透传给 service,响应为 rows/total 结构")
    void list_returnsPage() throws Exception {
        when(authorService.list(any())).thenReturn(Map.of("rows", List.of(), "total", 0));

        mvc.perform(get("/opc/marketplace/author/list").param("page", "2").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));

        var captor = org.mockito.ArgumentCaptor.forClass(AuthorListQuery.class);
        verify(authorService).list(captor.capture());
        assertThat(captor.getValue().getPage()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    // ==================== GET /{id}/stats ====================

    @Test
    @DisplayName("stats — 作者统计: 档案 + PUBLISHED 商品数原样透出")
    void stats_returnsMap() throws Exception {
        when(authorService.stats(10L)).thenReturn(Map.of("author", new AuthorVO(), "publicationCount", 5));

        mvc.perform(get("/opc/marketplace/author/10/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.publicationCount").value(5));

        verify(authorService).stats(10L);
    }

    // ==================== GET /{id} — 404 ====================

    @Test
    @DisplayName("get — 作者不存在: service 抛 OpcException(404) → advice → HTTP 200 + JSON code=404")
    void get_404_whenNotFound() throws Exception {
        when(authorService.getById(999L))
                .thenThrow(new OpcException(404, "作者不存在: 999", "MARKETPLACE"));

        mvc.perform(get("/opc/marketplace/author/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(authorService, never()).toPublicVO(any());
    }
}
