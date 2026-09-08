package com.ruoyi.opc.insight.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private KpiSnapshot kpi;
    private List<AnomalyVo> alerts;
    private List<AdviceVo> advice;
}
