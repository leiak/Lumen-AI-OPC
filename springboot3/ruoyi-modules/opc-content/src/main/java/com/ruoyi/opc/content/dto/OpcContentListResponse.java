package com.ruoyi.opc.content.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页列表响应 DTO — 复用 script / adapt / publish / platform-account 列表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcContentListResponse<T> {
    private List<T> rows;
    private int total;
}