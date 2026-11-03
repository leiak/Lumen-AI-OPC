package com.ruoyi.opc.finance.service.impl;

import com.ruoyi.opc.common.exception.OpcException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * 区间查询的共用助手 —— 银行流水与凭证共用<b>同一套</b>日期口径。
 *
 * <p>为什么必须是同一套：区间查询的产物要能被下游逐行对账。流水与凭证各抄一份
 * 日期换算代码，就等于给「口径一致」这个保证开了个后门 —— 改了一边忘了另一边，
 * 两边的「昨天」就会差一天，而且没有任何测试会红。所以共用逻辑只写这一份，
 * 两个 service impl 只保留各自真正不同的求和口径（direction vs 借贷方）。</p>
 *
 * <p><b>时区口径（踩过的坑）</b>：容器 JVM 是 UTC，而 JDBC URL 里
 * {@code serverTimezone=Asia/Shanghai} —— 驱动把库里的北京时间当成上海时间解读，
 * 得到的瞬间比墙上时间早 8 小时。用 JVM 默认时区去取日期，凌晨 0~8 点的记录
 * 会被算到前一天。所以「今天/昨天」和「库里存的那一天」一律回业务时区换算。</p>
 *
 * @author OAC
 */
final class RangeSupport {

    /**
     * 业务时区 —— 「今天/昨天」与「库里存的那一天」都按它算。
     */
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private RangeSupport() {
    }

    /**
     * 「今天」（北京时间）。
     *
     * <p>注意：service impl 上还有一个 {@code protected LocalDate today()} 委托到这里，
     * 供测试用 Mockito spy 覆盖 —— 那个方法是实例方法，不能变成静态方法，
     * 否则测试盖不住「今天」，测试就会随日期漂移。</p>
     */
    static LocalDate businessToday() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    /**
     * 解析 yyyy-MM-dd；null/空串用缺省值；格式非法抛 {@link OpcException}。
     *
     * @param field 出错时用的字段名（写给调用方看）
     */
    static LocalDate parseDate(String value, String field, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return parseRequiredDate(value, field);
    }

    /**
     * 解析必有的 yyyy-MM-dd；格式非法抛 {@link OpcException}。
     *
     * @param field 出错时用的标签 —— <b>不要写用户字段名</b>：这个值也可能来自
     *              数据库（比如「最早记录的日期」），报错时要说清楚是谁的日期不合法
     */
    static LocalDate parseRequiredDate(String value, String field) {
        try {
            return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new OpcException(field + " 格式非法，应为 yyyy-MM-dd: " + value);
        }
    }

    /**
     * 把 DB 列读出来的 {@link Date} 换算回<b>库里存的那个日期</b>。
     *
     * <p>两个坑：</p>
     * <ul>
     *   <li>用 {@code getTime()} 而不是 {@code toInstant()}：MyBatis 的 DateTypeHandler
     *       走 {@code rs.getTimestamp()}；而 DATE 列可能读出 {@code java.sql.Date}，
     *       它重写了 {@code toInstant()}，直接抛 {@code UnsupportedOperationException}。</li>
     *   <li>换算回<b>业务时区</b>而不是 JVM 默认时区：驱动按
     *       {@code serverTimezone=Asia/Shanghai} 解读库里的北京时间，
     *       得到的瞬间比墙上时间早 8 小时 —— 用 UTC 取日期会差一天。</li>
     * </ul>
     */
    static LocalDate wallDate(Date date) {
        if (date == null) {
            return null;
        }
        return Instant.ofEpochMilli(date.getTime()).atZone(BUSINESS_ZONE).toLocalDate();
    }

}
