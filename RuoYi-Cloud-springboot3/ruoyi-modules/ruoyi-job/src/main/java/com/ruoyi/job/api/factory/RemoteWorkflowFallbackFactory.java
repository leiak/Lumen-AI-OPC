package com.ruoyi.job.api.factory;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.job.api.RemoteWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 工作流触发服务降级处理
 *
 * @author OAC
 */
@Component
public class RemoteWorkflowFallbackFactory implements FallbackFactory<RemoteWorkflowService> {

    private static final Logger log = LoggerFactory.getLogger(RemoteWorkflowFallbackFactory.class);

    @Override
    public RemoteWorkflowService create(Throwable throwable) {
        log.error("工作流服务调用失败:{}", throwable.getMessage());
        return (workflowCode, triggerSource, source) ->
                R.fail("触发工作流失败[" + workflowCode + "]:" + throwable.getMessage());
    }

}
