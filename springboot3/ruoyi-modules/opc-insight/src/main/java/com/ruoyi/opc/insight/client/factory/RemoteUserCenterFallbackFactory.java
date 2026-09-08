package com.ruoyi.opc.insight.client.factory;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.insight.client.RemoteUserCenterService;
import com.ruoyi.opc.user.domain.OpcCompanyProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * opc-user-center 远程调用降级处理（M4 Task 5）。
 *
 * @author OAC
 */
@Component
public class RemoteUserCenterFallbackFactory implements FallbackFactory<RemoteUserCenterService> {

    private static final Logger log = LoggerFactory.getLogger(RemoteUserCenterFallbackFactory.class);

    @Override
    public RemoteUserCenterService create(Throwable throwable) {
        log.error("opc-user-center 调用降级: {}", throwable.getMessage());
        return companyId -> R.fail("getCompanyProfile 降级: " + throwable.getMessage());
    }
}
