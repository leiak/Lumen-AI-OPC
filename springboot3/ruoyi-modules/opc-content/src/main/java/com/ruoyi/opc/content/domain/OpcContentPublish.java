package com.ruoyi.opc.content.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 发布记录 — 脚本 → 抖音视频创建的全过程状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcContentPublish {

    private Long id;

    @JsonProperty("company_id")
    private Long companyId;

    @JsonProperty("script_id")
    private Long scriptId;

    @JsonProperty("platform_account_id")
    private Long platformAccountId;

    /** 平台代码 (冗余便于查询): DOUYIN */
    private String platform;

    /** 发布标题 */
    private String title;

    /** 标签数组 */
    private String[] tags;

    /** 抖音 video_id (可空) */
    @JsonProperty("external_video_id")
    private String externalVideoId;

    /** 抖音 item_id (发布成功后回填) */
    @JsonProperty("external_post_id")
    private String externalPostId;

    /** 发布链接 */
    @JsonProperty("external_url")
    private String externalUrl;

    /** 状态: PENDING/SUCCESS/FAILED */
    private String status;

    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("error_message")
    private String errorMessage;

    /** 成功发布时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("published_at")
    private LocalDateTime publishedAt;

    /** 更新时间 */
    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}