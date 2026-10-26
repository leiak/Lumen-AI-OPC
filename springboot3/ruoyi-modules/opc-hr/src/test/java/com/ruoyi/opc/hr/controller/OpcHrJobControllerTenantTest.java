package com.ruoyi.opc.hr.controller;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.context.SecurityContextHolder;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.security.tenant.CompanyIdBodyAdvice;
import com.ruoyi.common.security.tenant.TenantGuard;
import com.ruoyi.common.security.tenant.TenantProperties;
import com.ruoyi.opc.hr.dto.OpcHrJobLlmRequest;
import com.ruoyi.opc.hr.service.IOpcHrJobService;
import com.ruoyi.system.api.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /opc/hr/job/generate-llm} 的租户归属 —— 3b 回归护栏
 *
 * <p>这个端点以前收 {@code Map<String,String>}，租户守卫在 body 上找不到
 * companyId <b>字段</b> → 整条 body 路线失效 → 客户端填的 companyId
 * 原样进业务层，端点还拿 {@code 1L} 兜底。改回 {@code Map} 就是把洞重开。</p>
 *
 * @author OAC
 */
class OpcHrJobControllerTenantTest
{
    private IOpcHrJobService jobService;
    private OpcHrJobController controller;
    private CompanyIdBodyAdvice advice;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        jobService = mock(IOpcHrJobService.class);
        when(jobService.generateLlm(any(), any(), any(), any())).thenReturn("生成的 JD");
        controller = new OpcHrJobController(jobService);
        advice = new CompanyIdBodyAdvice(new TenantGuard(new TenantProperties()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(advice)
                .build();
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    private void loginAs(long userId, Long companyId)
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(userId);
        loginUser.setCompanyId(companyId);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
        SecurityContextHolder.setUserId(String.valueOf(userId));
    }

    private MethodParameter generateLlmParameter() throws NoSuchMethodException
    {
        Method method = OpcHrJobController.class.getMethod("generateLlm", OpcHrJobLlmRequest.class);
        return new MethodParameter(method, 0);
    }

    @Test
    @DisplayName("body 类型被租户守卫认到 (Map 那种写法认不到)")
    void bodyTypeIsRecognisedByTenantGuard() throws Exception
    {
        assertTrue(advice.supports(generateLlmParameter(), OpcHrJobLlmRequest.class,
                MappingJackson2HttpMessageConverter.class),
                "守卫认不到这个 body 类型 → 客户端能自己定租户");
    }

    @Test
    @DisplayName("body 里塞 companyId=999 → 业务层拿到的是登录租户")
    void bodyCompanyIdIsOverriddenByLoginTenant() throws Exception
    {
        loginAs(42L, 7L);

        mockMvc.perform(post("/opc/hr/job/generate-llm")
                        .contentType("application/json")
                        .content("{\"companyId\":999,\"title\":\"后端工程师\",\"description\":\"写 Java\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(jobService).generateLlm(eq(7L), eq("后端工程师"), any(), eq("写 Java"));
    }

    @Test
    @DisplayName("body 里不带 companyId → 仍然落到登录租户")
    void missingBodyCompanyIdFallsBackToLoginTenant() throws Exception
    {
        loginAs(42L, 7L);

        mockMvc.perform(post("/opc/hr/job/generate-llm")
                        .contentType("application/json")
                        .content("{\"title\":\"后端工程师\",\"description\":\"写 Java\"}"))
                .andExpect(status().isOk());

        verify(jobService).generateLlm(eq(7L), eq("后端工程师"), any(), eq("写 Java"));
    }

    @Test
    @DisplayName("没有租户作用域 → 403, 不拿 1 兜底")
    void noTenantScopeIsRejected() throws Exception
    {
        // 登录了但没加入任何公司 → 守卫没有作用域可给, companyId 保持 null
        loginAs(42L, null);

        OpcHrJobLlmRequest req = new OpcHrJobLlmRequest();
        req.setTitle("后端工程师");
        req.setDescription("写 Java");

        ServiceException ex = assertThrows(ServiceException.class, () -> controller.generateLlm(req));
        assertEquals(403, ex.getCode());
        assertNotNull(ex.getMessage());
        verify(jobService, org.mockito.Mockito.never()).generateLlm(any(), any(), any(), any());
    }
}
