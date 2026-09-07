package com.ruoyi.opc.finance.service.impl;

import com.ruoyi.opc.finance.domain.OpcFinanceBankFlow;
import com.ruoyi.opc.finance.mapper.OpcFinanceBankFlowMapper;
import com.ruoyi.opc.finance.service.IOpcFinanceBankFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * {@link IOpcFinanceBankFlowService} 实现。
 *
 * <p>业务规则：
 * <ul>
 *   <li>uploadBatch 空/空集合 → 返回 0，不调 mapper</li>
 *   <li>uploadBatch 每条 flow 自动生成 flowCode（毫秒时间戳 + hashCode 后 4 位）+ extracted=0 +
 *       status=IMPORTED + createBy=operator</li>
 *   <li>listPending 强制 extracted=0，limit=null 时默认 20</li>
 *   <li>markExtracted 强制 extracted=1 + status=EXTRACTED；voucherId 可为 null（mapper XML 用
 *       {@code <if>} 跳过字段）</li>
 * </ul>
 *
 * <p>flowCode 时间戳在同一批次内可能相同（System.currentTimeMillis 精度 1ms），
 * 实际生产建议改为 {@code OpcCodeGenerator.flowCode()}，但当前需求保持与历史 controller 行为一致。
 *
 * @author OAC
 */
@Service
@RequiredArgsConstructor
public class OpcFinanceBankFlowServiceImpl implements IOpcFinanceBankFlowService {

    /** 默认 listPending 条数 */
    private static final int DEFAULT_LIMIT = 20;

    /** AI 提取后状态 */
    private static final String STATUS_EXTRACTED = "EXTRACTED";

    /** 上传后初始状态 */
    private static final String STATUS_IMPORTED = "IMPORTED";

    private final OpcFinanceBankFlowMapper flowMapper;

    @Override
    public OpcFinanceBankFlow getById(Long id) {
        return flowMapper.selectById(id);
    }

    @Override
    public List<OpcFinanceBankFlow> listPending(Long companyId, Integer limit) {
        return flowMapper.selectByCompany(companyId, 0, limit == null ? DEFAULT_LIMIT : limit);
    }

    @Override
    public int uploadBatch(List<OpcFinanceBankFlow> flows, String operator) {
        if (flows == null || flows.isEmpty()) {
            return 0;
        }
        long stamp = System.currentTimeMillis();
        for (int i = 0; i < flows.size(); i++) {
            OpcFinanceBankFlow flow = flows.get(i);
            // 加 i 防止同批次 hashCode 碰撞
            flow.setFlowCode("F" + (stamp + i) + Math.abs((flow.hashCode() + i) % 10000));
            flow.setExtracted(0);
            flow.setStatus(STATUS_IMPORTED);
            flow.setCreateBy(operator);
        }
        return flowMapper.insertBatch(flows);
    }

    @Override
    public int markExtracted(Long id, Long voucherId, String operator) {
        OpcFinanceBankFlow flow = new OpcFinanceBankFlow();
        flow.setId(id);
        flow.setExtracted(1);
        flow.setVoucherId(voucherId);
        flow.setStatus(STATUS_EXTRACTED);
        flow.setUpdateBy(operator);
        return flowMapper.update(flow);
    }

}
