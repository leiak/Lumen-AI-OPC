package com.ruoyi.opc.insight.client;

import com.ruoyi.common.core.constant.ServiceNameConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.insight.client.factory.RemoteUserCenterFallbackFactory;
import com.ruoyi.opc.user.domain.OpcCompanyProfile;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * INSIGHT → opc-user-center 远程调用（M4 Task 5）。
 *
 * <p>目前 1 个端点：{@code /opc/user/companies/profile} —— 由 OpcUserController.companyProfile
 * 在 opc-user-center 提供（{@code @InnerAuth}）。INSIGHT 拿到 OpcCompanyProfile 后自己用
 * {@code createTime} 算 {@code companyActiveDays}（避免在对端多塞一个 VO）。
 *
 * @author OAC
 */
@FeignClient(contextId = "remoteUserCenterService", value = ServiceNameConstants.USER_CENTER_SERVICE,
        fallbackFactory = RemoteUserCenterFallbackFactory.class)
public interface RemoteUserCenterService {

    @GetMapping("/opc/user/companies/profile")
    R<OpcCompanyProfile> getCompanyProfile(@RequestParam Long companyId);

}
