package com.ruoyi.opc.hr.feign;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.hr.feign.factory.OpcHrCustomerGatewayFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * opc-hr → opc-crm 远程调用（Task 8）。
 *
 * <p>用途：候选人入库时与已有 Customer 去重 / 关联。对端 controller 在 opc-crm 模块（9312），
 * 走 {@code @InnerAuth}。
 *
 * <p>降级策略：{@link OpcHrCustomerGatewayFactory} —— CRM 不可用时按「无重复」处理，
 * 让候选人入库主流程不阻塞；关联关系留给异步重试。
 *
 * @author OAC
 */
@FeignClient(contextId = "opcHrCustomer", name = "opc-crm",
        fallbackFactory = OpcHrCustomerGatewayFactory.class)
public interface OpcHrCustomerGateway {

    /**
     * 按 email 在某 company 范围内查 Customer（候选人去重用）。
     *
     * @param companyId 公司 ID
     * @param email     候选人邮箱
     */
    @GetMapping("/opc/crm/customer/findByEmail")
    R<Map<String, Object>> findByEmail(@RequestParam("companyId") Long companyId,
                                       @RequestParam("email") String email);

    /**
     * 把候选人关联到已有 Customer（写侧）。
     *
     * <p>payload: {@code { customerId, candidateId, linkType }}
     */
    @PostMapping("/opc/crm/customer/linkCandidate")
    R<Void> linkCandidate(@RequestBody Map<String, Object> payload);
}
