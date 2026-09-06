package com.ruoyi.opc.user.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 一人公司档案
 *
 * @author OAC
 */
@Data
public class OpcCompanyProfile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String companyCode;
    private Long ownerUserId;
    private String companyName;
    private String companyType;
    private String businessLicense;
    private String licensePicUrl;
    private String taxNo;
    private String industryCode;
    private String industryName;
    private BigDecimal registeredCapital;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date establishDate;
    private String province;
    private String city;
    private String district;
    private String address;
    private String legalPerson;
    private String phone;
    private String email;
    private String logoUrl;
    private String introduction;
    private String scale;
    private Integer verified;
    private String status;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    private String remark;

}
