package com.ruoyi.job.api;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.constant.ServiceNameConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.job.api.factory.RemoteWorkflowFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * OPC 工作流触发服务（远程调用 opc-agent-hub）。
 *
 * <p>定时任务进程不直接依赖 opc-agent-hub，工作流的执行与持久化全部发生在 agent-hub 侧。
 *
 * @author OAC
 */
@FeignClient(contextId = "remoteWorkflowService", value = ServiceNameConstants.AGENT_HUB_SERVICE,
        fallbackFactory = RemoteWorkflowFallbackFactory.class)
public interface RemoteWorkflowService {

    /**
     * 触发一次工作流执行
     *
     * @param workflowCode  工作流编码
     * @param triggerSource 触发来源
     * @param source        请求来源，固定传 {@link SecurityConstants#INNER}
     * @return runCode / status / durationMs / errorMessage
     */
    @PostMapping("/opc/agent/workflows/{workflowCode}/trigger")
    R<Map<String, Object>> trigger(@PathVariable("workflowCode") String workflowCode,
                                   @RequestParam("triggerSource") String triggerSource,
                                   @RequestHeader(SecurityConstants.FROM_SOURCE) String source);

}
