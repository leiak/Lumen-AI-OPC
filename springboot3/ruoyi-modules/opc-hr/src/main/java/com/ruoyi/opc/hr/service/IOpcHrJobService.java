package com.ruoyi.opc.hr.service;

import com.ruoyi.opc.hr.domain.OpcHrJob;
import com.ruoyi.opc.hr.dto.OpcHrJobDto;

import java.util.List;

public interface IOpcHrJobService {

    /**
     * 创建 JD(默认 DRAFT 状态)
     *
     * @param dto 客户端传入的 JD 字段
     * @return 新 JD 的雪花 ID
     */
    Long create(OpcHrJobDto dto);

    /**
     * 更新 JD(仅 DRAFT 状态)
     *
     * @return 受影响行数(0 或 1)
     */
    int update(Long id, Long companyId, OpcHrJobDto dto);

    /**
     * 删除 JD(仅 DRAFT 状态,mapper SQL 内置 status=DRAFT 守卫)
     */
    int delete(Long id, Long companyId);

    /**
     * 查询 JD 详情
     */
    OpcHrJob detail(Long id, Long companyId);

    /**
     * 分页 + 状态过滤列表(status 可空)
     */
    List<OpcHrJob> list(Long companyId, String status);

    /**
     * 发布 JD(DRAFT/PAUSED → OPEN,设置 publish_at)
     */
    void publish(Long id, Long companyId);

    /**
     * 关闭 JD(OPEN → CLOSED,设置 close_at)
     */
    void close(Long id, Long companyId);

    /**
     * LLM 生成 JD — Task 4 实现(占位返回 null)
     */
    String generateLlm(Long companyId, String title, String category, String description);
}
