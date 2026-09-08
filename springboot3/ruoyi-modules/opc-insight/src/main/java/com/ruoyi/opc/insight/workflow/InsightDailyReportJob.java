package com.ruoyi.opc.insight.workflow;

import com.ruoyi.opc.insight.domain.OpcInsightAnomaly;
import com.ruoyi.opc.insight.enums.AnomalyLevel;
import com.ruoyi.opc.insight.mapper.OpcInsightAnomalyMapper;
import com.ruoyi.opc.insight.service.IDailyReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * INSIGHT daily report cron delegate.
 *
 * <p><b>NOT Quartz-runnable directly.</b> This bean lives outside the
 * {@code com.ruoyi.job.task} whitelist enforced by
 * {@code Constants.JOB_WHITELIST_STR} + {@code ScheduleUtils.whiteList()}.
 * To invoke via Quartz, wrap it in a {@code WorkflowCronJob → RemoteInsightService (Feign) → this class}
 * chain (see ruoyi-job WorkflowCronJob for reference).
 *
 * <p>Direct invocation is supported from the Spring container (tests, manual triggers).
 */
@Component("dailyReportJobDelegate")
@Slf4j
public class InsightDailyReportJob {

    private final IDailyReportService dailyReportService;
    private final IActiveCompanyQuery companyQuery;
    private final OpcInsightAnomalyMapper anomalyMapper;

    public InsightDailyReportJob(IDailyReportService dailyReportService,
                                  IActiveCompanyQuery companyQuery,
                                  OpcInsightAnomalyMapper anomalyMapper) {
        this.dailyReportService = dailyReportService;
        this.companyQuery = companyQuery;
        this.anomalyMapper = anomalyMapper;
    }

    /**
     * 为所有活跃公司生成当天日报。
     */
    public void trigger() {
        String today = LocalDate.now().toString();
        List<Long> activeCompanyIds = companyQuery.activeCompanyIds();
        if (activeCompanyIds == null || activeCompanyIds.isEmpty()) {
            log.info("[InsightDailyReportJob] triggered for 0 companies on {}", today);
            return;
        }

        log.info("[InsightDailyReportJob] triggered for {} companies on {}",
                activeCompanyIds.size(), today);

        for (Long companyId : activeCompanyIds) {
            try {
                dailyReportService.generate(companyId, today);
            } catch (DataIntegrityViolationException dup) {
                log.warn("[InsightDailyReportJob] daily report already exists for company={} date={}",
                        companyId, today);
                anomalyMapper.insert(OpcInsightAnomaly.builder()
                        .companyId(companyId)
                        .period(LocalDate.parse(today))
                        .level(AnomalyLevel.LOW.name())
                        .ruleCode("DAILY_REPORT_DUPLICATE")
                        .description("日报已存在（UNIQUE KEY uk_company_period）")
                        .status("ACK")
                        .createBy("")
                        .updateBy("")
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build());
            } catch (Exception e) {
                log.error("[InsightDailyReportJob] daily report failed for company={}", companyId, e);
                anomalyMapper.insert(OpcInsightAnomaly.builder()
                        .companyId(companyId)
                        .period(LocalDate.parse(today))
                        .level(AnomalyLevel.MEDIUM.name())
                        .ruleCode("DAILY_REPORT_FAILED")
                        .description("日报生成失败: " + e.getMessage())
                        .status("OPEN")
                        .createBy("")
                        .updateBy("")
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build());
            }
        }
    }
}
