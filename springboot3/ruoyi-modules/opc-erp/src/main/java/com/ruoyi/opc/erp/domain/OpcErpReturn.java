package com.ruoyi.opc.erp.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ERP 退货单
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcErpReturn {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    @JsonProperty("return_no")
    private String returnNo;
    @JsonProperty("return_type")
    private String returnType;
    @JsonProperty("ref_id")
    private Long refId;
    @JsonProperty("refund_amount")
    private BigDecimal refundAmount;
    private String status;
    @JsonProperty("operator_id")
    private Long operatorId;
    @JsonProperty("confirmed_by")
    private Long confirmedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("confirmed_at")
    private LocalDateTime confirmedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("completed_at")
    private LocalDateTime completedAt;
    private String reason;
    private String remark;
    @JsonProperty("created_by")
    private Long createdBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("create_time")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("update_time")
    private LocalDateTime updateTime;
}
