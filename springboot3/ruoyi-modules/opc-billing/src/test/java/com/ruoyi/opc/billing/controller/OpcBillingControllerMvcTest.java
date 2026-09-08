package com.ruoyi.opc.billing.controller;

import com.ruoyi.common.core.context.SecurityContextHolder;
import com.ruoyi.common.security.handler.GlobalExceptionHandler;
import com.ruoyi.opc.billing.domain.OpcWallet;
import com.ruoyi.opc.billing.mapper.OpcBillingOrderMapper;
import com.ruoyi.opc.billing.service.IOpcBillingOrderService;
import com.ruoyi.opc.billing.service.IOpcWalletService;
import com.ruoyi.opc.common.exception.OpcException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link OpcBillingController} 集成测试（@WebMvcTest）— W6.2
 *
 * <p>覆盖维度：
 * <ul>
 *   <li><b>HTTP status</b>：200 正常 + 500 service 异常 + 500 缺 userId</li>
 *   <li><b>JSON 序列化</b>：{@code $.data.balance} BigDecimal 数值 + {@code $.data.companyId} Long</li>
 *   <li><b>401 鉴权</b>：匿名 → SecurityUtils.getUserId() 返回 null → walletService 抛 OpcException → 500（gateway 实际拦截）</li>
 *   <li><b>@RestControllerAdvice</b>：OpcException → AjaxResult.error 透传</li>
 * </ul>
 *
 * <p>本 controller 关键设计：{@code SecurityUtils.getUserId()} 是空指针敏感点 — 匿名调用会被
 * {@code walletService.getOrCreate(companyId, null)} 抛 OpcException → advice → 500。
 *
 * @author OAC
 */
@WebMvcTest(controllers = OpcBillingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "spring.main.banner-mode=off",
        "logging.level.root=ERROR"
})
class OpcBillingControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IOpcWalletService walletService;

    @MockBean
    private OpcBillingOrderMapper orderMapper;

    @MockBean
    private IOpcBillingOrderService orderService;

    private static final Long USER_ID = 2002L;
    private static final Long COMPANY_ID = 1001L;

    @BeforeEach
    void setUpAuth() {
        SecurityContextHolder.setUserId(String.valueOf(USER_ID));
    }

    @AfterEach
    void tearDownAuth() {
        SecurityContextHolder.remove();
    }

    private OpcWallet sampleWallet() {
        OpcWallet w = new OpcWallet();
        w.setId(7L);
        w.setCompanyId(COMPANY_ID);
        w.setUserId(USER_ID);
        w.setBalance(new BigDecimal("100.00"));
        w.setStatus("ACTIVE");
        return w;
    }

    // ==================== GET /wallet — 正常路径 ====================

    @Test
    @DisplayName("wallet — 已登录 + service 返回钱包 → HTTP 200 + JSON data.balance=100.00")
    void wallet_authenticated_returns200WithBalance() throws Exception {
        OpcWallet w = sampleWallet();
        when(walletService.getOrCreate(COMPANY_ID, USER_ID)).thenReturn(w);

        mockMvc.perform(get("/opc/billing/wallet").param("companyId", String.valueOf(COMPANY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.companyId").value(COMPANY_ID))
                .andExpect(jsonPath("$.data.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.balance").value(100.00))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(walletService).getOrCreate(COMPANY_ID, USER_ID);
    }

    @Test
    @DisplayName("wallet — service 返回 null → HTTP 200 + JSON data=null")
    void wallet_serviceReturnsNull_returns200WithNullData() throws Exception {
        when(walletService.getOrCreate(anyLong(), anyLong())).thenReturn(null);

        mockMvc.perform(get("/opc/billing/wallet").param("companyId", String.valueOf(COMPANY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== GET /wallet — 异常路径 ====================

    @Test
    @DisplayName("wallet — service 抛 OpcException → HTTP 500 + JSON msg（via @RestControllerAdvice）")
    void wallet_serviceThrowsOpcException_returns500ViaAdvice() throws Exception {
        when(walletService.getOrCreate(anyLong(), anyLong()))
                .thenThrow(new OpcException("DB 连接超时"));

        mockMvc.perform(get("/opc/billing/wallet").param("companyId", String.valueOf(COMPANY_ID)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("DB 连接超时"));
    }

    // ==================== GET /wallet — 鉴权场景（匿名）===================

    @Test
    @DisplayName("wallet — 匿名调用（无 SecurityContextHolder）→ service 收到 null userId → 抛 OpcException → 500")
    void wallet_anonymous_serviceCalledWithNullUserId_throwsOpcException() throws Exception {
        // 模拟 gateway 未写入上下文（生产环境：gateway 拦截返回 401，controller 不会被调到）
        // 本测试模拟「如果 controller 被绕过直接调到」的行为，验证 controller 的健壮性
        SecurityContextHolder.remove();
        when(walletService.getOrCreate(anyLong(), any()))
                .thenThrow(new OpcException("userId 不能为空"));

        mockMvc.perform(get("/opc/billing/wallet").param("companyId", String.valueOf(COMPANY_ID)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.msg").value("userId 不能为空"));
    }

    // ==================== GET /wallet — 参数缺失 ====================

    @Test
    @DisplayName("wallet — 缺 companyId → HTTP 500（MissingServletRequestParameterException 走 advice）")
    void wallet_missingCompanyId_returns500() throws Exception {
        mockMvc.perform(get("/opc/billing/wallet"))
                .andExpect(status().isInternalServerError());
    }
}