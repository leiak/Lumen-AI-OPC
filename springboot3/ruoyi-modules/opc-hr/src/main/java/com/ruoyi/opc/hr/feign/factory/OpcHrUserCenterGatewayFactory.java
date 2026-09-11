package com.ruoyi.opc.hr.feign.factory;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.hr.feign.OpcHrUserCenterGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * opc-user-center 降级工厂（Task 8）。
 *
 * <p>策略：拉取员工档案 / 公司档案缺失不应阻塞 Offer 创建；返回空 Map 让业务侧用默认占位。
 * 服务恢复后下次调用即可拿到真实数据。
 *
 * @author OAC
 */
@Slf4j
@Component
public class OpcHrUserCenterGatewayFactory implements FallbackFactory<OpcHrUserCenterGateway> {

    @Override
    public OpcHrUserCenterGateway create(Throwable cause) {
        log.warn("[opc-hr] user-center fallback triggered: {}", cause.getMessage());
        return new OpcHrUserCenterGateway() {
            @Override
            public R<Map<String, Object>> getProfile(Long userId) {
                log.warn("[opc-hr] user-center getProfile fallback for userId={}", userId);
                return R.ok(Collections.emptyMap());
            }

            @Override
            public R<Map<String, Object>> getCompanyProfile(Long companyId) {
                log.warn("[opc-hr] user-center getCompanyProfile fallback for companyId={}", companyId);
                return R.ok(Collections.emptyMap());
            }
        };
    }
}
