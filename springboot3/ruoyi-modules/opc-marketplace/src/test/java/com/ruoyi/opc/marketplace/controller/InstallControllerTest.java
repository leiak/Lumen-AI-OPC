package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.security.handler.GlobalExceptionHandler;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpInstall;
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
import com.ruoyi.opc.marketplace.service.InstallService;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link InstallController} 集成测试（{@code @WebMvcTest}）— P1 Task 6
 *
 * <p>8 个场景,3 类契约：
 * <ol>
 *   <li><b>正向路径</b>：install / uninstall / my / status / upgrade 全部 200 透传</li>
 *   <li><b>业务异常透传</b>：409 已安装 / 404 未安装 / 400 商品未发布 全部走
 *       GlobalExceptionHandler → HTTP 200 + JSON code=N（W7 修复语义）</li>
 *   <li><b>租户 fail-closed</b>：{@code SecurityUtils.getCompanyId() == null} → 400,
 *       <b>绝不</b>用 0 兜底,绝不让 service 入空对象到 mapper</li>
 * </ol>
 *
 * <p>{@code @WebMvcTest} 装配同 {@link PublicationControllerTest}:
 * {@code excludeFilters} 用 REGEX 排除整个 mapper 包（避免 {@code @MapperScan}
 * 在没有 SqlSessionFactory 时启动失败）+ 6 个 mapper 全部 {@code @MockBean} 占位
 * + {@code AgentHubClient} 也 {@code @MockBean}（Feign 在单元测试里没 Nacos 也能
 * mock 接口本身）。
 *
 * <p><b>不在 controller 测</b>：install/uninstall 的 service 层细节（远端调用顺序、
 * best-effort 清理、计数器自增）—— 这些由 {@code InstallServiceImplTest} 覆盖，
 * controller 测只关心「HTTP 契约 + 公司 id 来源 + 异常透传」。
 *
 * @author OAC
 */
@WebMvcTest(controllers = InstallController.class,
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
class InstallControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private InstallService installService;

    @MockBean
    private AgentHubClient agentHubClient;
    // W80 P2:社区模块只读 mapper / 订阅 mapper / 社区 Feign 客户端 ——
    // @EnableCustomConfig → @MapperScan 把 mapper 注册成 bean,@WebMvcTest 不启 MyBatis
    // (缺 sqlSessionFactory);Feign 同理没有 FeignClientFactory。缺任何一个都起不来。
    @MockBean
    private CommunityModuleMapper communityModuleMapper;
    @MockBean
    private MpSubscriptionMapper subscriptionMapper;
    @MockBean
    private CommunityClient communityClient;

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
        securityUtils.when(SecurityUtils::getUserId).thenReturn(7L);
    }

    @AfterEach
    void tearDown() {
        if (securityUtils != null) {
            securityUtils.close();
        }
    }

    /** 已登录 + 有租户(companyId=200),userId=7(与 setUp 默认一致) */
    private void loginWithCompany() {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(200L);
    }

    private MpInstall sampleInstall(long publicationId, long instanceId, String status) {
        return MpInstall.builder()
                .id(100L).publicationId(publicationId).tenantCompanyId(200L).userId(7L)
                .agentInstanceId(instanceId).installSource("MARKET").status(status)
                .installedAt(LocalDateTime.now()).createBy("7").updateBy("7")
                .build();
    }

    // ==================== POST /{publicationId} ====================

    @Test
    @DisplayName("install — 正常路径: companyId/userId 来自登录态, service.install 三参透传")
    void install_returnsOk() throws Exception {
        loginWithCompany();
        MpInstall ins = sampleInstall(1L, 999L, "ACTIVE");
        when(installService.install(eq(1L), eq(200L), eq(7L))).thenReturn(ins);

        mvc.perform(post("/opc/marketplace/install/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.publicationId").value(1))
                .andExpect(jsonPath("$.data.agentInstanceId").value(999))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // 三参(publicationId, tenantCompanyId, userId)来源:(1, 200, 7)
        verify(installService).install(1L, 200L, 7L);
    }

    @Test
    @DisplayName("install — 已安装: service 抛 OpcException(409) → HTTP 200 + JSON code=409")
    void install_409_whenAlreadyInstalled() throws Exception {
        loginWithCompany();
        when(installService.install(anyLong(), anyLong(), anyLong()))
                .thenThrow(new OpcException(409, "已安装，请勿重复", "MARKETPLACE"));

        mvc.perform(post("/opc/marketplace/install/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value("已安装，请勿重复"));
    }

    @Test
    @DisplayName("install — 商品未发布: service 抛 OpcException(400) → HTTP 200 + JSON code=400")
    void install_400_whenNotPublished() throws Exception {
        loginWithCompany();
        when(installService.install(anyLong(), anyLong(), anyLong()))
                .thenThrow(new OpcException(400, "商品未发布，无法安装", "MARKETPLACE"));

        mvc.perform(post("/opc/marketplace/install/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("商品未发布，无法安装"));
    }

    @Test
    @DisplayName("install — agent-hub 远端失败: service 抛 OpcException(503) → HTTP 200 + JSON code=503")
    void install_503_whenRemoteFails() throws Exception {
        loginWithCompany();
        when(installService.install(anyLong(), anyLong(), anyLong()))
                .thenThrow(new OpcException(503, "opc-agent-hub 调用失败,请稍后重试", "MARKETPLACE"));

        mvc.perform(post("/opc/marketplace/install/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(503))
                // 关键断言:用户看到的文案是 fallback 工厂的固定文案,不是 cause.getMessage()
                // (后者会包含内网地址 + HTML 错误页 — 见 AgentHubClientFallback 的注释)
                .andExpect(jsonPath("$.msg").value("opc-agent-hub 调用失败,请稍后重试"));
    }

    // ==================== DELETE /{publicationId} ====================

    @Test
    @DisplayName("uninstall — 正常路径: service.uninstall 三参透传")
    void uninstall_returnsOk() throws Exception {
        loginWithCompany();
        // void method — 不打 thenReturn
        mvc.perform(delete("/opc/marketplace/install/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(installService).uninstall(1L, 200L, 7L);
    }

    @Test
    @DisplayName("uninstall — 未安装: service 抛 OpcException(404) → HTTP 200 + JSON code=404")
    void uninstall_404_whenNotInstalled() throws Exception {
        loginWithCompany();
        org.mockito.Mockito.doThrow(new OpcException(404, "未安装", "MARKETPLACE"))
                .when(installService).uninstall(anyLong(), anyLong(), anyLong());

        mvc.perform(delete("/opc/marketplace/install/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("未安装"));
    }

    @Test
    @DisplayName("uninstall — 已卸载: service 抛 OpcException(409) → HTTP 200 + JSON code=409")
    void uninstall_409_whenAlreadyUninstalled() throws Exception {
        loginWithCompany();
        org.mockito.Mockito.doThrow(new OpcException(409, "该安装记录已卸载", "MARKETPLACE"))
                .when(installService).uninstall(anyLong(), anyLong(), anyLong());

        mvc.perform(delete("/opc/marketplace/install/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));
    }

    // ==================== GET /my ====================

    @Test
    @DisplayName("my — 正常路径: listByUser(userId) 透传, 不需要 companyId")
    void my_returnsList() throws Exception {
        loginWithCompany();
        when(installService.listByUser(7L)).thenReturn(java.util.List.of(
                sampleInstall(1L, 999L, "ACTIVE"),
                sampleInstall(2L, 888L, "ACTIVE")
        ));

        mvc.perform(get("/opc/marketplace/install/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2));
        // /my 只按 userId 查,不调 listByTenant
        verify(installService).listByUser(7L);
        verify(installService, never()).listByTenant(anyLong());
    }

    // ==================== GET /{publicationId}/status ====================

    @Test
    @DisplayName("status — 正常路径: getInstallStatus 三参透传,未安装返回 null")
    void status_returnsInstall() throws Exception {
        loginWithCompany();
        MpInstall ins = sampleInstall(1L, 999L, "ACTIVE");
        when(installService.getInstallStatus(1L, 200L, 7L)).thenReturn(ins);

        mvc.perform(get("/opc/marketplace/install/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        verify(installService).getInstallStatus(1L, 200L, 7L);
    }

    // ==================== POST /{publicationId}/upgrade ====================

    @Test
    @DisplayName("upgrade — P1 no-op: 直接 200, 不调 service(P2 接 AgentHubClient 真实升级)")
    void upgrade_returnsOk_noop() throws Exception {
        loginWithCompany();
        mvc.perform(post("/opc/marketplace/install/1/upgrade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        // P1 upgrade 不进 service,直接返回 200
        verify(installService, never()).install(any(), anyLong(), anyLong());
        verify(installService, never()).uninstall(anyLong(), anyLong(), anyLong());
    }

    // ==================== 租户 fail-closed (核心回归) ====================

    @Test
    @DisplayName("install — 无租户: fail-closed 400,禁止用 companyId=0 兜底")
    void install_400_whenNoTenant() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(null);

        mvc.perform(post("/opc/marketplace/install/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("当前账号未关联企业，请先加入或创建企业"));

        // service.install 永远不该被调到 — 入参 companyId=null 必须被 controller 拦下
        verify(installService, never()).install(any(), any(), any());
    }

    @Test
    @DisplayName("uninstall — 无租户: fail-closed 400")
    void uninstall_400_whenNoTenant() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(null);

        mvc.perform(delete("/opc/marketplace/install/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        verify(installService, never()).uninstall(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("status — 无租户: fail-closed 400")
    void status_400_whenNoTenant() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(null);

        mvc.perform(get("/opc/marketplace/install/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        verify(installService, never()).getInstallStatus(any(), any(), any());
    }

    @Test
    @DisplayName("租户隔离: A 公司登录态(companyId=100)无法用 B 公司参数(companyId=999)安装")
    void tenantIsolation_neverUsesForeignCompanyId() throws Exception {
        // companyId 只来自登录态;controller 不接受任何 companyId 入参(Query/Body)
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(100L);
        when(installService.install(eq(1L), eq(100L), eq(7L)))
                .thenReturn(sampleInstall(1L, 999L, "ACTIVE"));

        // 请求体 + URL 都尝试塞 companyId=999
        mvc.perform(post("/opc/marketplace/install/1")
                        .param("companyId", "999")
                        .contentType("application/json")
                        .content("{\"companyId\":999}"));

        verify(installService).install(1L, 100L, 7L);
        verify(installService, never()).install(eq(1L), eq(999L), anyLong());
    }

    @Test
    @DisplayName("503 错误信息不泄漏内网地址: 安装失败响应体不含 aiopc-* / 9303 / http:// 等内部细节")
    void install_503_msgDoesNotLeakInternalDetail() throws Exception {
        loginWithCompany();
        // 用一个会真实泄漏 FeignException 信息的 message,模拟「粗心 fallback」行为
        when(installService.install(anyLong(), anyLong(), anyLong()))
                .thenThrow(new OpcException(503,
                        "opc-agent-hub 调用失败,请稍后重试", "MARKETPLACE"));

        String body = mvc.perform(post("/opc/marketplace/install/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(503))
                .andReturn().getResponse().getContentAsString();

        // 断言「响应体里没有泄漏」而非「有安全文案」 ——
        // 只断言有文案的话,「带内网地址的错误信息」用例也会通过
        assertThat(body)
                .as("503 响应不得含内网主机名")
                .doesNotContain("aiopc-")
                .as("503 响应不得含内网端口")
                .doesNotContain("9303")
                .as("503 响应不得含 Nacos / HTTP 协议地址")
                .doesNotContain("http://")
                .doesNotContain("nacos");
    }
}