package com.ruoyi.opc.insight.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.context.SecurityContextHolder;
import com.ruoyi.common.security.handler.GlobalExceptionHandler;
import com.ruoyi.opc.insight.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 4 个 INSIGHT Controller 的 MockMvc 单测（M4 Task 10）。
 * 使用 Standalone MockMvc 模式绕开 @WebMvcTest 的 @MapperScan 引导。
 */
@ExtendWith(MockitoExtension.class)
class OpcInsightControllerMvcTest {
    private MockMvc mvc;
    @Mock private IKpiService kpiService;
    @Mock private IAnomalyService anomalyService;
    @Mock private IDailyReportService dailyReportService;
    @Mock private IAdviceService adviceService;
    @InjectMocks private DashboardController dashboardController;
    @InjectMocks private AlertController alertController;
    @InjectMocks private DailyReportController dailyReportController;
    @InjectMocks private AdviceController adviceController;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(dashboardController, alertController,
                dailyReportController, adviceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
        SecurityContextHolder.setUserId("1001");
    }
    @AfterEach void teardown() { SecurityContextHolder.remove(); }

    @Test void dashboard_returns200() throws Exception {
        when(anomalyService.listOpen(1001L, 5)).thenReturn(List.of());
        when(adviceService.listByCompany(1001L, 3)).thenReturn(List.of());
        mvc.perform(get("/opc/insight/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
    @Test void dashboard_noAuth_returns500() throws Exception {
        SecurityContextHolder.remove();
        mvc.perform(get("/opc/insight/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }
    @Test void dashboard_serviceError_returns500() throws Exception {
        when(kpiService.snapshot(anyLong(), isNull())).thenThrow(new RuntimeException("failure"));
        mvc.perform(get("/opc/insight/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test void alerts_list() throws Exception {
        when(anomalyService.listOpen(1001L, null)).thenReturn(List.of());
        mvc.perform(get("/opc/insight/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
    @Test void alerts_detail() throws Exception {
        when(anomalyService.listOpen(1001L, null)).thenReturn(List.of());
        mvc.perform(get("/opc/insight/alerts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }
    @Test void alerts_ack() throws Exception {
        mvc.perform(post("/opc/insight/alerts/1/ack")).andExpect(status().isOk());
        verify(anomalyService).acknowledge(1L);
    }

    @Test void daily_list() throws Exception {
        when(dailyReportService.listByDateRange(eq(1001L), any(), any(), isNull())).thenReturn(List.of());
        mvc.perform(get("/opc/insight/daily").param("from", "2026-09-01").param("to", "2026-09-30"))
                .andExpect(status().isOk());
    }
    @Test void daily_detail() throws Exception {
        mvc.perform(get("/opc/insight/daily/1")).andExpect(status().isOk());
        verify(dailyReportService).getById(1L);
    }
    @Test void daily_generate() throws Exception {
        mvc.perform(post("/opc/insight/daily/generate").param("date", "2026-09-08")).andExpect(status().isOk());
        verify(dailyReportService).generate(1001L, "2026-09-08");
    }

    @Test void advice_list() throws Exception {
        when(adviceService.listByCompany(1001L, null)).thenReturn(List.of());
        mvc.perform(get("/opc/insight/advice")).andExpect(status().isOk());
    }
    @Test void advice_detail() throws Exception {
        mvc.perform(get("/opc/insight/advice/1")).andExpect(status().isOk());
        verify(adviceService).getById(1L);
    }
    @Test void advice_regenerate() throws Exception {
        mvc.perform(post("/opc/insight/advice/1/regenerate")).andExpect(status().isOk());
        verify(adviceService).regenerate(1L);
    }
}
