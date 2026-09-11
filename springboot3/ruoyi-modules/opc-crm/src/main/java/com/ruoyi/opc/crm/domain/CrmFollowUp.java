package com.ruoyi.opc.crm.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CrmFollowUp implements Serializable {
    private Long id;
    private Long customerId;
    private Long contactId;
    private String type;
    private String content;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime nextAt;
    private Long ownerId;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
    private Integer deleted;
}