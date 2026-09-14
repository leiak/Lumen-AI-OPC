package com.ruoyi.opc.content.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 平台账号绑定表 — 抖音开放平台 OAuth token 持久化 (ENC 加密)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcContentPlatformAccount {

    private Long id;

    @JsonProperty("company_id")
    private Long companyId;

    /** 平台代码: DOUYIN */
    private String platform;

    /** 抖音昵称,DB 列 account_name */
    private String nickname;

    /** 抖音 open_id,DB 列 account_id */
    @JsonProperty("open_id")
    private String openId;

    /** 抖音 union_id (可选) */
    @JsonProperty("union_id")
    private String unionId;

    /** access_token (ENC 加密) */
    @JsonProperty("access_token_enc")
    private String accessTokenEnc;

    /** refresh_token (ENC 加密) */
    @JsonProperty("refresh_token_enc")
    private String refreshTokenEnc;

    /** access_token 过期时间 (epoch seconds),DB 列 expires_at */
    @JsonProperty("access_token_expires_at")
    private Long accessTokenExpiresAt;

    /** refresh_token 过期时间 (epoch seconds),DB 列 refresh_at */
    @JsonProperty("refresh_token_expires_at")
    private Long refreshTokenExpiresAt;

    /** OAuth scope */
    private String scope;

    /** 头像 URL */
    @JsonProperty("avatar_url")
    private String avatarUrl;

    /** 状态: ACTIVE/EXPIRED/REVOKED */
    private String status;

    /** 创建时间 */
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("bound_at")
    private LocalDateTime boundAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}