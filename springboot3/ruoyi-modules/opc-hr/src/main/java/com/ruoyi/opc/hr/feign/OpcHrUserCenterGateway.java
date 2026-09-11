package com.ruoyi.opc.hr.feign;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.hr.feign.factory.OpcHrUserCenterGatewayFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * opc-hr → opc-user-center 远程调用（Task 8）。
 *
 * <p>用途：创建 Offer 时拉取员工档案、查部门。对端 controller 在 opc-user-center 模块（9302），
 * 走 {@code @InnerAuth}。
 *
 * <p>降级策略：{@link OpcHrUserCenterGatewayFactory} —— 返回空 Map，主流程容忍缺失。
 *
 * @author OAC
 */
@FeignClient(contextId = "opcHrUserCenter", name = "opc-user-center",
        fallbackFactory = OpcHrUserCenterGatewayFactory.class)
public interface OpcHrUserCenterGateway {

    /**
     * 查员工画像（userId → profile）。
     *
     * @param userId 用户 ID
     */
    @GetMapping("/opc/user/profile/{userId}")
    R<Map<String, Object>> getProfile(@PathVariable("userId") Long userId);

    /**
     * 查公司档案（companyId → company profile）。
     *
     * @param companyId 公司 ID
     */
    @GetMapping("/opc/user/companies/profile")
    R<Map<String, Object>> getCompanyProfile(@RequestParam("companyId") Long companyId);
}
