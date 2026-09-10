package com.ruoyi.opc.notification.domain;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class NotificationTemplate implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String channel;
    private String subject;
    private String body;
    private String varsSchema;
    private Integer version;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String createBy;
    private String updateBy;
    private LocalDateTime updateTime;
}
