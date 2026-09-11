package com.ruoyi.opc.hr.dto;

import lombok.Data;

@Data
public class HrSearchRequest {
    /** 搜索关键词,可以是 JD title 或 description */
    private String query;
    /** top-K 数量,默认 10 */
    private Integer topK;
}
