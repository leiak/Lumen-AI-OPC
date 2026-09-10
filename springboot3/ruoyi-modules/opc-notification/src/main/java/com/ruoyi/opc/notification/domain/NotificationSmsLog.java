package com.ruoyi.opc.notification.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime sentAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    private String createBy;
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
