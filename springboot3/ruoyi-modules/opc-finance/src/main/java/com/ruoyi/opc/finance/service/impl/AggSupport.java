package com.ruoyi.opc.finance.service.impl;

import com.ruoyi.opc.common.exception.OpcException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 聚合查询的入参校验与结果归一化助手（M4 Task 1，voucher / flow / token 三个聚合共用）。
 *
 * <p>为什么需要归一化：MyBatis 的 {@code resultType="java.util.Map"} 把 {@code SUM()} /
 * {@code COUNT()} 的 Java 类型交给 JDBC driver 决定（BigDecimal / Long / Integer / null，
 * 且不同 driver 的 scale 不一致，见 W1.4.2 经验）。这里统一：金额 scale=2、计数 long、
 * null → 0，保证 Feign 序列化给 opc-insight 的数字稳定可比。
 *
 * @author OAC
 */
final class AggSupport {

    /** 期间格式 YYYY-MM，月份限 01..12 */
    private static final String PERIOD_REGEX = "^\\d{4}-(0[1-9]|1[0-2])$";

    private AggSupport() {
    }

    /** 校验聚合端点通用入参 */
    static void validate(Long companyId, String period) {
        if (companyId == null) {
            throw new OpcException("companyId 不能为空");
        }
        if (period == null || !period.matches(PERIOD_REGEX)) {
            throw new OpcException("period 格式错误，应为 YYYY-MM");
        }
    }

    /** 空 Map 兜底（mapper 在无数据时可能返回 null） */
    static Map<String, Object> orEmpty(Map<String, Object> agg) {
        return agg == null ? Map.of() : agg;
    }

    /** 金额：null → 0.00，统一 scale=2（HALF_UP） */
    static BigDecimal amount(Object o) {
        return toBigDecimal(o).setScale(2, RoundingMode.HALF_UP);
    }

    /** 金额（保留原 scale）：用于 opc_agent_token_usage.cost 这类 scale=4 的列 */
    static BigDecimal decimal(Object o) {
        return toBigDecimal(o);
    }

    /** 计数：null → 0 */
    static long count(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(o.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

}
