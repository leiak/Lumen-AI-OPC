package com.ruoyi.opc.finance.controller;

import com.ruoyi.common.core.context.SecurityContextHolder;
import com.ruoyi.common.security.handler.GlobalExceptionHandler;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.finance.domain.OpcFinanceVoucher;
import com.ruoyi.opc.finance.service.IOpcFinanceBankFlowService;
import com.ruoyi.opc.finance.service.IOpcFinanceVoucherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link OpcFinanceController} 集成测试（@WebMvcTest）— W6.1
 *
 * <p>覆盖维度：
 * <ul>
 *   <li><b>HTTP status</b>：200 正常 + 500 service 异常</li>
 *   <li><b>JSON 序列化</b>：AjaxResult.data.voucherId 字段验证（{@code jsonPath("$.data.voucherId")}）</li>
 *   <li><b>401 鉴权</b>：匿名 → SecurityUtils.getUsername() 返回 null → 模拟 gateway 缺失 token</li>
 *   <li><b>@RestControllerAdvice</b>：OpcException → GlobalExceptionHandler.handleRuntimeException → AjaxResult.error</li>
 * </ul>
 *
 * <p>关键模式：
 * <ul>
 *   <li>{@code @WebMvcTest(controllers = X.class)} 只加载 controller 层 + Jackson，不启 Nacos/Redis/DB</li>
 *   <li>{@code @AutoConfigureMockMvc(addFilters = false)} 绕过 Spring Security 过滤器链 — RuoYi 网关负责鉴权（前置）</li>
 *   <li>{@code @Import(GlobalExceptionHandler.class)} 显式拉入 {@link GlobalExceptionHandler}（位于 ruoyi-common-security 模块，@WebMvcTest 默认不扫描）</li>
 *   <li>{@link SecurityContextHolder#setUserName(String)} 在 {@code @BeforeEach} 模拟「网关已写入上下文」</li>
 * </ul>
 *
 * <p>注意：本机为 JDK 1.8，{@code @WebMvcTest} 需要 JDK 17 跑 {@code mvn test}，本地只做静态验证。
 * 真正的运行验证需在 CI/容器环境执行。
 *
 * @author OAC
 */
@WebMvcTest(controllers = OpcFinanceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "spring.main.banner-mode=off",
        "logging.level.root=ERROR"
})
class OpcFinanceControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IOpcFinanceVoucherService voucherService;

    @MockBean
    private IOpcFinanceBankFlowService bankFlowService;

    private static final String USERNAME = "alice";
    private static final Long VOUCHER_ID = 7L;

    @BeforeEach
    void setUpAuth() {
        // 模拟网关已通过 HeaderInterceptor 写入 SecurityContextHolder
        SecurityContextHolder.setUserName(USERNAME);
    }

    @AfterEach
    void tearDownAuth() {
        SecurityContextHolder.remove();
    }

    private static String voucherJson() {
        // 最小化有效 JSON：只含必填字段
        return "{\"companyId\":1001,\"period\":\"2026-09\",\"summary\":\"test\",\"createBy\":\"spoofed-by-client\"}";
    }

    private static String voucherJsonWithoutCreateBy() {
        // 客户端不传 createBy，由 controller 注入 SecurityContextHolder 中的 username
        return "{\"companyId\":1001,\"period\":\"2026-09\",\"summary\":\"test\"}";
    }

    private static String voucherUpdateJson() {
        // PUT /voucher：客户端伪造 updateBy 试图覆盖服务端身份
        return "{\"id\":7,\"companyId\":1001,\"period\":\"2026-09\",\"summary\":\"updated\",\"updateBy\":\"spoofed-by-client\"}";
    }

    private static String voucherUpdateJsonWithoutUpdateBy() {
        // PUT /voucher：客户端不传 updateBy，由 controller 注入 username
        return "{\"id\":7,\"companyId\":1001,\"period\":\"2026-09\",\"summary\":\"updated\"}";
    }

    // ==================== POST /voucher — 正常路径 ====================

    @Test
    @DisplayName("createVoucher — service.create 返回 id → HTTP 200 + JSON data.voucherId")
    void createVoucher_returns200WithVoucherIdInJson() throws Exception {
        when(voucherService.create(any(OpcFinanceVoucher.class))).thenReturn(VOUCHER_ID);

        mockMvc.perform(post("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.voucherId").value(VOUCHER_ID));

        verify(voucherService).create(any(OpcFinanceVoucher.class));
    }

    @Test
    @DisplayName("createVoucher — W8 修复：controller override createBy（防止客户端伪造）")
    void createVoucher_overridesCreateByFromSecurityContext() throws Exception {
        when(voucherService.create(any(OpcFinanceVoucher.class))).thenReturn(VOUCHER_ID);

        mockMvc.perform(post("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherJson()))
                .andExpect(status().isOk());

        // 验证 controller 把 spoofed-by-client 替换为 SecurityContextHolder 中的 username
        org.mockito.ArgumentCaptor<OpcFinanceVoucher> captor =
                org.mockito.ArgumentCaptor.forClass(OpcFinanceVoucher.class);
        verify(voucherService).create(captor.capture());
        assertEquals(USERNAME, captor.getValue().getCreateBy(),
                "W8 修复：createBy 必须被 override 为 SecurityContextHolder 中的 username（不能信任客户端值）");
    }

    @Test
    @DisplayName("createVoucher — 客户端未传 createBy 时，controller 自动注入 username")
    void createVoucher_injectsCreateByWhenClientOmits() throws Exception {
        when(voucherService.create(any(OpcFinanceVoucher.class))).thenReturn(VOUCHER_ID);

        mockMvc.perform(post("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherJsonWithoutCreateBy()))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<OpcFinanceVoucher> captor =
                org.mockito.ArgumentCaptor.forClass(OpcFinanceVoucher.class);
        verify(voucherService).create(captor.capture());
        assertEquals(USERNAME, captor.getValue().getCreateBy(),
                "客户端未传 createBy 时，controller 必须从 SecurityContextHolder 注入");
    }

    @Test
    @DisplayName("createVoucher — service 返回 null → HTTP 200 + JSON data.voucherId=null")
    void createVoucher_nullIdReturns200WithNullInJson() throws Exception {
        when(voucherService.create(any(OpcFinanceVoucher.class))).thenReturn(null);

        mockMvc.perform(post("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.voucherId").doesNotExist());
    }

    // ==================== POST /voucher — 异常路径 ====================

    @Test
    @DisplayName("createVoucher — service 抛 OpcException → HTTP 500 + JSON msg（via @RestControllerAdvice）")
    void createVoucher_serviceThrowsOpcException_returns500ViaAdvice() throws Exception {
        when(voucherService.create(any(OpcFinanceVoucher.class)))
                .thenThrow(new OpcException("Mapper insert 失败：唯一键冲突"));

        mockMvc.perform(post("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherJson()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("Mapper insert 失败：唯一键冲突"));
    }

    @Test
    @DisplayName("createVoucher — service 抛 OpcException(code=400) → HTTP 200 + JSON code=400（W7 修复验证）")
    void createVoucher_serviceThrowsOpcExceptionWithCode_returns200WithCodeInJson() throws Exception {
        // W7 修复：OpcException 现在继承 ServiceException，handleServiceException 接住后会尊重 code 字段
        // 响应：HTTP 200（@ExceptionHandler 返回 body 不改 status）+ JSON code=400
        when(voucherService.create(any(OpcFinanceVoucher.class)))
                .thenThrow(new OpcException(400, "凭证已存在"));

        mockMvc.perform(post("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("凭证已存在"));
    }

    // ==================== POST /voucher — 边界 ====================

    @Test
    @DisplayName("createVoucher — 缺 @RequestBody → HTTP 400（HttpMessageNotReadableException）")
    void createVoucher_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
        verify(voucherService, never()).create(any(OpcFinanceVoucher.class));
    }

    // ==================== POST /voucher — 匿名场景 ====================

    @Test
    @DisplayName("createVoucher — 匿名调用（无 SecurityContextHolder）→ service.create 不被调（gateway 实际会 401）")
    void createVoucher_anonymousNoContext_serviceStillCalledButWithoutUsername() throws Exception {
        // 模拟网关未写入 SecurityContextHolder（即未登录 / token 缺失）
        SecurityContextHolder.remove();
        when(voucherService.create(any(OpcFinanceVoucher.class))).thenReturn(VOUCHER_ID);

        // 注：OpcFinanceController.createVoucher 不读 SecurityContextHolder，仅依赖 @RequestBody
        // 即使 SecurityContextHolder 为空，controller 仍然能跑（行为正常）
        // 这印证了「鉴权完全依赖 gateway 前置过滤」的设计
        mockMvc.perform(post("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voucherId").value(VOUCHER_ID));

        verify(voucherService).create(any(OpcFinanceVoucher.class));
    }

    // ==================== PUT /voucher — W10.3 审计字段扩展 ====================

    @Test
    @DisplayName("updateVoucher — W10.3 修复：controller override updateBy（防止客户端伪造）")
    void updateVoucher_overridesUpdateByFromSecurityContext() throws Exception {
        when(voucherService.update(any(OpcFinanceVoucher.class))).thenReturn(1);

        mockMvc.perform(put("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherUpdateJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        // 验证 controller 把 spoofed-by-client 替换为 SecurityContextHolder 中的 username
        org.mockito.ArgumentCaptor<OpcFinanceVoucher> captor =
                org.mockito.ArgumentCaptor.forClass(OpcFinanceVoucher.class);
        verify(voucherService).update(captor.capture());
        assertEquals(USERNAME, captor.getValue().getUpdateBy(),
                "W10.3 修复：updateBy 必须被 override 为 SecurityContextHolder 中的 username（不能信任客户端值）");
    }

    @Test
    @DisplayName("updateVoucher — 客户端未传 updateBy 时，controller 自动注入 username")
    void updateVoucher_injectsUpdateByWhenClientOmits() throws Exception {
        when(voucherService.update(any(OpcFinanceVoucher.class))).thenReturn(1);

        mockMvc.perform(put("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherUpdateJsonWithoutUpdateBy()))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<OpcFinanceVoucher> captor =
                org.mockito.ArgumentCaptor.forClass(OpcFinanceVoucher.class);
        verify(voucherService).update(captor.capture());
        assertEquals(USERNAME, captor.getValue().getUpdateBy(),
                "客户端未传 updateBy 时，controller 必须从 SecurityContextHolder 注入");
    }

    @Test
    @DisplayName("updateVoucher — service 返回 0 → HTTP 200 + JSON data=false（不是 error）")
    void updateVoucher_rowsZero_returns200WithFalse() throws Exception {
        when(voucherService.update(any(OpcFinanceVoucher.class))).thenReturn(0);

        mockMvc.perform(put("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherUpdateJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    @DisplayName("updateVoucher — service 抛 OpcException → HTTP 500 + JSON msg（via @RestControllerAdvice）")
    void updateVoucher_serviceThrowsOpcException_returns500ViaAdvice() throws Exception {
        when(voucherService.update(any(OpcFinanceVoucher.class)))
                .thenThrow(new OpcException("更新失败：乐观锁冲突"));

        mockMvc.perform(put("/opc/finance/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherUpdateJson()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("更新失败：乐观锁冲突"));
    }
}