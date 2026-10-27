package com.ruoyi.opc.marketplace.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * mp_subscription — 订阅记录 — W80 P2
 *
 * <p>存在的理由：{@code mp_publication.subscription_count} 是商品卡上唯一
 * <b>完全没有写入路径</b>的数字 —— {@code MpPublicationMapper#incrementSubscriptionCount}
 * 写好了却零调用方，订阅这个产品动作从来没被实现过。
 *
 * <p><b>口径</b>：{@code subscription_count == COUNT(mp_subscription WHERE status='ACTIVE')}。
 * 订阅成功 +1，退订 -1，重新订阅 +1。健康检查第 19 节断言这个不变量。
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MpSubscription implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long publicationId;
    private Long tenantCompanyId;
    private Long userId;
    /** AGENT / MODULE —— 冗余存一份，统计时不必回表 join mp_publication。 */
    private String productType;
    /** 目前只有 FREE —— marketplace 与 opc-billing 之间没有写单路径，刻意不造付费套餐。 */
    private String plan;
    /** ACTIVE / CANCELLED */
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
