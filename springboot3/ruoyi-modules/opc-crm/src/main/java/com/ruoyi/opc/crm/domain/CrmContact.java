package com.ruoyi.opc.crm.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CrmContact implements Serializable {
    private Long id;
    private Long customerId;
    private String name;
    private String phone;
    private String email;
    private String position;
    private Integer isPrimary;
    private String remark;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
    private Integer deleted;
}