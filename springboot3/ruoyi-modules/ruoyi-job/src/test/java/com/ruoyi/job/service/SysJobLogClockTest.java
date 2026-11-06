package com.ruoyi.job.service;

import com.ruoyi.job.domain.SysJobLog;
import com.ruoyi.job.mapper.SysJobLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * 坑 #15 —— {@code sys_job_log} 同一行里两个时钟。
 *
 * <p>实测（2026-11-06 09:00 那次自动触发）:
 * <pre>
 *   start_time  = 2026-11-06 09:00:00   ← Java 的 Date 走 JDBC(serverTimezone=Asia/Shanghai) → 北京
 *   create_time = 2026-11-06 01:00:17   ← mapper 里写死的 sysdate() 走 MySQL 的钟 → UTC
 * </pre>
 * 同一行差 8 小时。RuoYi 自带的定时任务日志页按 {@code create_time} 过滤日期区间,
 * 于是「按北京时间挑今天」会把 00:00~08:00 的执行算到昨天。
 *
 * <p>修法: {@code create_time} 也从 Java 传（{@code #{createTime}}），
 * 让整行一个钟 —— 跟 {@code start_time} / {@code end_time} 一样是北京时间。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
class SysJobLogClockTest
{
    @Mock
    private SysJobLogMapper jobLogMapper;

    @InjectMocks
    private SysJobLogServiceImpl sysJobLogService;

    @Test
    @DisplayName("addJobLog: 没给 createTime 也要补上 —— 否则这一列会写成 NULL")
    void addJobLog_fillsCreateTime()
    {
        SysJobLog jobLog = new SysJobLog();
        jobLog.setJobName("财务日报");
        jobLog.setStartTime(new java.util.Date());
        jobLog.setEndTime(new java.util.Date());

        sysJobLogService.addJobLog(jobLog);

        ArgumentCaptor<SysJobLog> captor = ArgumentCaptor.forClass(SysJobLog.class);
        verify(jobLogMapper).insertJobLog(captor.capture());
        assertThat(captor.getValue().getCreateTime())
                .as("create_time 改由 Java 传, 就必须保证它一定有值")
                .isNotNull();
    }

    @Test
    @DisplayName("insert 的 create_time 不能再由 MySQL 的钟写 —— 那是 UTC, 和 start_time 差 8 小时")
    void mapper_createTimeIsNotWrittenByDatabaseClock() throws Exception
    {
        String xml;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("mapper/job/SysJobLogMapper.xml"))
        {
            assertThat(in).as("SysJobLogMapper.xml 必须在 classpath 上").isNotNull();
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(xml)
                .as("sysdate() 走 MySQL 服务器的钟(UTC), 和 start_time 的 JDBC 钟(北京)不是同一个")
                .doesNotContain("sysdate(");
        assertThat(xml)
                .as("create_time 必须从 Java 传")
                .contains("#{createTime}");
    }
}
