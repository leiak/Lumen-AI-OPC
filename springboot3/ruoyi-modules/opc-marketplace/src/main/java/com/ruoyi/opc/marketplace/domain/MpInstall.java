package com.ruoyi.opc.marketplace.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * mp_install — 安装记录
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MpInstall implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long publicationId;
    private Long tenantCompanyId;
    private Long userId;
    /** AGENT 安装时由 agent-hub 返回的实例 id */
    private Long agentInstanceId;
    /** MARKET / API */
    private String installSource;
    /** ACTIVE / UNINSTALLED */
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime installedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime uninstalledAt;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}