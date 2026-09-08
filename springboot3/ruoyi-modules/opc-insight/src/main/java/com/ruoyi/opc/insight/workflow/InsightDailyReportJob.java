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
 * INSIGHT 日报定时任务。
 *
 * <p>每个公司独立执行：日报唯一键冲突只记录低级 ACK 异常，其他错误记录为
 * OPEN 中级异常；任何单个公司的失败都不会阻止后续公司执行。</p>
 */
@Component("insightDailyReportJob")
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
