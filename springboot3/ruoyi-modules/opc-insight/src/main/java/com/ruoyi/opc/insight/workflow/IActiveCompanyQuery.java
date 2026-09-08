package com.ruoyi.opc.insight.workflow;

import java.util.List;

/**
 * 查询当前需要生成日报的活跃公司。
 *
 * <p>该接口隔离 INSIGHT 与公司数据的来源。生产环境可由 Feign 适配器实现，
 * 业务流程本身无需依赖 opc-user-center 的具体 API。</p>
 */
public interface IActiveCompanyQuery {

    /**
     * 返回活跃公司 ID。
     *
     * @return 活跃公司 ID 列表
     */
    List<Long> activeCompanyIds();
}
