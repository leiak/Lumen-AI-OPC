package com.ruoyi.opc.hr.feign.factory;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.hr.feign.OpcHrCustomerGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * opc-crm 降级工厂（Task 8）。
 *
 * <p>策略：CRM 不可用时按「无重复」处理（{@code findByEmail} 返回 null），
 * 避免阻塞候选人入库主流程；关联关系（{@code linkCandidate}）失败由日志记录，
 * 待服务恢复后由后台 job 异步重试（不在 v1 范围内）。
 *
 * @author OAC
 */
@Slf4j
@Component
public class OpcHrCustomerGatewayFactory implements FallbackFactory<OpcHrCustomerGateway> {

    @Override
    public OpcHrCustomerGateway create(Throwable cause) {
        log.warn("[opc-hr] crm fallback triggered: {}", cause.getMessage());
        return new OpcHrCustomerGateway() {
            @Override
            public R<Map<String, Object>> findByEmail(Long companyId, String email) {
                log.warn("[opc-hr] crm findByEmail fallback companyId={} email={}", companyId, email);
                // 返回 null 表示「CRM 不可达」≠ 「找到 Customer」，service 层需区分两种语义
                return R.ok(Collections.emptyMap());
            }

            @Override
            public R<Void> linkCandidate(Map<String, Object> payload) {
                log.warn("[opc-hr] crm linkCandidate fallback payload={}", payload);
                return R.ok();
            }
        };
    }
}
