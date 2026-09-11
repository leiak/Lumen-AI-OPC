package com.ruoyi.opc.crm.dto;

import lombok.Data;

/**
 * 商机阶段变更请求 DTO
 */
@Data
public class StageChangeRequest {
    private String stage;
    private String reason;
}