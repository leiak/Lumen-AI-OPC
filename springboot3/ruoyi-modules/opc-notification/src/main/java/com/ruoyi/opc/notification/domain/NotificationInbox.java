package com.ruoyi.opc.notification.domain;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class NotificationInbox implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String type;
    private String title;
    private String body;
    private String link;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    private String createBy;
    private String updateBy;
    private LocalDateTime updateTime;
}
