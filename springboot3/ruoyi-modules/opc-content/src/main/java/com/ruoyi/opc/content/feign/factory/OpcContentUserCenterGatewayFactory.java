package com.ruoyi.opc.content.feign.factory;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.content.feign.OpcContentUserCenterGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * opc-user-center 降级工厂 (W74 Task 8)。
 *
 * <p>策略：用户档案 / 当前用户信息缺失不应阻塞内容主流程（创建草稿、查看列表等）；
 * 返回空 Map 让业务侧用默认占位（匿名作品）。服务恢复后下次调用即可拿到真实数据。
 *
 * @author OAC
 */
@Slf4j
@Component
public class OpcContentUserCenterGatewayFactory implements FallbackFactory<OpcContentUserCenterGateway> {

    @Override
    public OpcContentUserCenterGateway create(Throwable cause) {
        log.warn("[opc-content] user-center fallback triggered: {}", cause.getMessage());
        return () -> {
            log.warn("[opc-content] user-center me() fallback cause={}", cause.getMessage());
            return R.ok(Collections.emptyMap());
        };
    }
}
