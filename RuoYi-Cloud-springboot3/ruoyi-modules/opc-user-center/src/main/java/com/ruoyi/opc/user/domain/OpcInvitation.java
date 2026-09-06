package com.ruoyi.opc.user.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * OPC 邀请码
 *
 * @author OAC
 */
@Data
public class OpcInvitation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String inviteCode;
    private Long inviterId;
    private Long inviteeId;
    private String inviteeMobile;
    private Integer maxUses;
    private Integer usedCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date usedTime;
    private String status;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

}
