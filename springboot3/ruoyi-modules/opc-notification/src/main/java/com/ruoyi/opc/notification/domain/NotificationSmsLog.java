package com.ruoyi.opc.notification.domain;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class NotificationSmsLog implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String phone;
    private String templateCode;
    private String varsJson;
    private String content;
    private Integer status;
    private Integer retryCount;
    private String errorMsg;
    private String provider;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;

    private String createBy;
    private String updateBy;
    private LocalDateTime updateTime;
}
