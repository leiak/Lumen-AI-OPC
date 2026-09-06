package com.ruoyi.opc.user.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * OPC 用户画像
 *
 * @author OAC
 */
@Data
public class OpcUserProfile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String userType;
    private String realName;
    private String idCardNo;
    private String mobile;
    private String avatarUrl;
    private String bio;
    private String industry;
    private String city;
    private String invitationCode;
    private Long inviterId;
    private Integer verified;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date verifiedTime;
    private String status;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    private String remark;

}
