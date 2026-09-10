package com.ruoyi.opc.notification.domain;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class NotificationEmailLog implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String recipient;
    private String subject;
    private String body;
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
