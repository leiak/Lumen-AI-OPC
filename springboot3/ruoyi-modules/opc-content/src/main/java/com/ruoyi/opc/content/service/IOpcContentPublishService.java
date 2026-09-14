package com.ruoyi.opc.content.service;

import com.ruoyi.opc.content.domain.OpcContentPublish;
import com.ruoyi.opc.content.dto.OpcContentListResponse;
import com.ruoyi.opc.content.dto.OpcContentPublishRequest;

/**
 * 发布 Service 接口 — 脚本 → 抖音视频上传/创建,记录发布历史 + 状态机 (PENDING → SUCCESS / FAILED → PENDING retry)。
 *
 * @author OAC
 */
public interface IOpcContentPublishService {

    /**
     * 发布脚本到指定平台账号:
     * 1. 校验 script.status = READY
     * 2. 调 PlatformClient.uploadVideo + createVideo
     * 3. 落 opc_content_publish(PENDING → SUCCESS)
     * 4. 更新 script.status = PUBLISHED
     * 5. (Task 8) 发通知 NotificationGateway.send(...)
     *
     * @return 新发布记录 ID
     */
    Long publish(OpcContentPublishRequest req);

    /**
     * 公司下发布记录分页列表(status 可空)
     */
    OpcContentListResponse<OpcContentPublish> listByCompany(Long companyId, String status,
                                                             int page, int size);

    /**
     * 发布详情
     */
    OpcContentPublish detail(Long id, Long companyId);

    /**
     * 重试失败的发布: FAILED → PENDING(状态机守卫)
     */
    void retry(Long id, Long companyId);
}