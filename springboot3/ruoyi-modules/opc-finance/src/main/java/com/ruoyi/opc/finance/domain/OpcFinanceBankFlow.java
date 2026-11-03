package com.ruoyi.opc.finance.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 银行流水 opc_finance_bank_flow
 *
 * @author OAC
 */
@Data
public class OpcFinanceBankFlow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String flowCode;
    private Long companyId;
    private String bankAccount;
    private String bankName;
    /**
     * 交易时间。
     *
     * <p>为什么单独指定 {@code timezone = "Asia/Shanghai"}：这一列是业务发生时间，
     * 库里存的就是<b>北京时间墙上时间</b>；而 JDBC URL 里
     * {@code serverTimezone=Asia/Shanghai} 意味着驱动把库里那串字面量按上海时间解读，
     * 得到的瞬间比墙上时间早 8 小时。容器 JVM 是 UTC，Jackson 默认按 UTC 渲染，
     * 于是接口吐出的是「比库里早 8 小时」的时间，凌晨 0~8 点的记录连日期都会差一天 ——
     * 响应里的 {@code items[].tradeTime} 会和 {@code fromDate/toDate} 互相矛盾。
     * 加上时区后渲染出来的就是库里存的那串墙上时间。</p>
     *
     * <p><b>不要</b>照抄这个时区给 {@code createTime/updateTime}：那两列由 SQL 的
     * {@code NOW()} 写入，而 MySQL 服务器时钟是 UTC —— 它们存的就是 UTC 墙上时间，
     * 保持默认渲染才对。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date tradeTime;
    private String direction;
    private BigDecimal amount;
    private String currency;
    private String counterParty;
    private String memo;
    private String rawText;
    private Integer extracted;
    private Long voucherId;
    private Long agentTaskId;
    private String status;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

}
