package com.ruoyi.opc.common.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * OPC 业务编码生成工具
 *
 * @author OAC
 */
public final class OpcCodeGenerator {

    private OpcCodeGenerator() {}

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_FMT_DASH = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static String taskCode() {
        return "T" + LocalDate.now().format(DATE_FMT) + randomSuffix();
    }

    public static String instanceCode() {
        return "AI" + LocalDate.now().format(DATE_FMT) + randomSuffix();
    }

    public static String flowCode() {
        return "F" + LocalDate.now().format(DATE_FMT) + randomSuffix();
    }

    public static String voucherCode() {
        return "V" + LocalDate.now().format(DATE_FMT) + randomSuffix();
    }

    /**
     * 税务报表编号（TR + yyyyMMdd + 6 位随机）
     * 用于 opc_finance_tax_report.report_code 唯一键
     */
    public static String taxReportCode() {
        return "TR" + LocalDate.now().format(DATE_FMT) + randomSuffix();
    }

    public static String orderNo() {
        return "O" + LocalDate.now().format(DATE_FMT) + randomSuffix();
    }

    public static String txCode() {
        return "TX" + LocalDate.now().format(DATE_FMT) + randomSuffix();
    }

    public static String invoiceNo() {
        return "I" + LocalDate.now().format(DATE_FMT) + randomSuffix();
    }

    public static String workflowCode() {
        return "W" + LocalDate.now().format(DATE_FMT) + randomSuffix();
    }

    public static String runCode() {
        return "R" + LocalDate.now().format(DATE_FMT) + randomSuffix();
    }

    public static String usageCode() {
        return "U" + LocalDate.now().format(DATE_FMT) + randomSuffix();
    }

    public static String evalCode() {
        return "E" + LocalDate.now().format(DATE_FMT) + randomSuffix();
    }

    public static String recordCode() {
        return "H" + LocalDate.now().format(DATE_FMT) + randomSuffix();
    }

    public static String inviteCode() {
        char[] chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
        StringBuilder sb = new StringBuilder(8);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 8; i++) {
            sb.append(chars[r.nextInt(chars.length)]);
        }
        return sb.toString();
    }

    public static String bizDate() {
        return LocalDate.now().format(DATE_FMT_DASH);
    }

    private static String randomSuffix() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(1, 1000000));
    }

}
