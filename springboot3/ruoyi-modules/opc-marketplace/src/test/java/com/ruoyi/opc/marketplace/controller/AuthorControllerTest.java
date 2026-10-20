package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.security.handler.GlobalExceptionHandler;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpAuthor;
import com.ruoyi.opc.marketplace.dto.AuthorApplyRequest;
import com.ruoyi.opc.marketplace.dto.AuthorVO;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AuthorController} 集成测试（{@code @WebMvcTest}）— P1 Task 4
 *
 * <p>覆盖 3 个场景：
 * <ul>
 *   <li>{@code apply_returnsAuthor} — POST /apply 正常路径（200 + JSON code=200）</li>
 *   <li>{@code get_returnsAuthor}   — GET /{id} 正常路径</li>
 *   <li>{@code apply_409_whenAlreadyAuthor} — service 抛 OpcException(409) → GlobalExceptionHandler → HTTP 200 + JSON code=409（W7 修复：OpcException 继承 ServiceException 后尊重 code 字段）</li>
 * </ul>
 *
 * <p>关键模式：
 * <ul>
 *   <li>{@code @WebMvcTest(controllers = AuthorController.class)} 只加载 controller 层</li>
 *   <li>{@code @AutoConfigureMockMvc(addFilters = false)} 绕过 Spring Security 过滤器链 — RuoYi 网关负责鉴权（前置）</li>
 *   <li>{@code @Import(GlobalExceptionHandler.class)} 显式拉入 {@link GlobalExceptionHandler}（位于 ruoyi-common-security 模块，@WebMvcTest 默认不扫描）</li>
 *   <li>{@code excludeFilters} 用 REGEX 排除整个 mapper 包 — 避免 {@code @MapperScan("com.ruoyi.**.mapper")} 在没有 SqlSessionFactory 时启动失败</li>
 *   <li>6 个 mapper 接口全部 {@code @MockBean} — {@code @EnableCustomConfig} → {@code @MapperScan} 把它们注册成 bean，{@code @WebMvcTest} 不启 MyBatis，需要 mock 占位</li>
 *   <li>{@link SecurityUtils#getUserId} 是静态方法 → {@code mockStatic(SecurityUtils.class)}</li>
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
        MpAuthor a = sampleAuthor();
        AuthorVO vo = new AuthorVO();
        vo.setId(10L);
        vo.setAuthorCompanyId(100L);
        vo.setAuthorName("TestAuthor");

        when(authorService.apply(any(AuthorApplyRequest.class), anyLong())).thenReturn(a);
        when(authorService.toVO(a)).thenReturn(vo);

        String body = """
            {
              "authorCompanyId": 100,
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
    }

    // ==================== GET /{id} ====================

    @Test
    @DisplayName("get — 正常路径: service.getById + toVO 都被调 → HTTP 200 + JSON code=200")
    void get_returnsAuthor() throws Exception {
        MpAuthor a = sampleAuthor();
        AuthorVO vo = new AuthorVO();
        vo.setId(10L);
        vo.setAuthorCompanyId(100L);

        when(authorService.getById(10L)).thenReturn(a);
        when(authorService.toVO(a)).thenReturn(vo);

        mvc.perform(get("/opc/marketplace/author/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.authorCompanyId").value(100));
    }

    // ==================== POST /apply — 409 异常路径 ====================

    @Test
    @DisplayName("apply — service 抛 OpcException(409) → advice → HTTP 200 + JSON code=409 (W7 修复语义)")
    void apply_409_whenAlreadyAuthor() throws Exception {
        when(authorService.apply(any(AuthorApplyRequest.class), anyLong()))
                .thenThrow(new OpcException(409, "该企业已是作者，不可重复申请", "MARKETPLACE"));

        String body = """
            {"authorCompanyId": 100, "authorName": "X", "contactEmail": "a@b.com"}
            """;

        mvc.perform(post("/opc/marketplace/author/apply")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value("该企业已是作者，不可重复申请"));
    }
}
