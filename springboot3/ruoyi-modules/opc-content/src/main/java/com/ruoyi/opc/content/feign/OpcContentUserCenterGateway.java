package com.ruoyi.opc.content.feign;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.content.feign.factory.OpcContentUserCenterGatewayFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * opc-content → opc-user-center 远程调用 (W74 Task 8)。
 *
 * <p>用途：内容归属校验（创作者是否归属当前租户 / 公司）、获取当前登录用户、
 * 拉取作者画像用于内容推荐标记等。
 *
 * <p>对端 controller 在 opc-user-center 模块 (9302)，通过 {@code @InnerAuth} 走内部 Feign 通道，
 * 由 {@code @EnableRyFeignClients} 自动注入 {@code from-source: inner} header 绕过 gateway
 * AuthFilter。
 *
 * <p>降级策略：{@link OpcContentUserCenterGatewayFactory} —— 用户档案缺失时返回空 Map，
 * 主流程容忍缺失（按匿名作品处理），服务恢复后下次调用即可拿到真实数据。
 *
 * @author OAC
 */
@FeignClient(contextId = "opcContentUserCenter", name = "opc-user-center",
        path = "/opc/user-center", fallbackFactory = OpcContentUserCenterGatewayFactory.class)
public interface OpcContentUserCenterGateway {

    /**
     * 获取当前登录用户信息（无需传 userId，由网关从 token 解析）。
     *
     * <p>response data schema:
     * <pre>
     * {
     *   "userId":    12345,
     *   "username":  "alice",
     *   "nickName":  "Alice Wang",
     *   "companyId": 67890,
     *   "deptId":    100,
     *   "roles":     ["content_creator", "content_reviewer"]
     * }
     * </pre>
     */
    @GetMapping("/me")
    R<Map<String, Object>> me();
}
