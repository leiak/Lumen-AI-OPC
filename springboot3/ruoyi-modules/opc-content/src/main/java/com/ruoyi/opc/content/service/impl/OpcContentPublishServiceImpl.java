package com.ruoyi.opc.content.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.common.utils.SnowflakeIdGenerator;
import com.ruoyi.opc.content.domain.OpcContentPublish;
import com.ruoyi.opc.content.domain.OpcContentScript;
import com.ruoyi.opc.content.dto.OpcContentListResponse;
import com.ruoyi.opc.content.dto.OpcContentPublishRequest;
import com.ruoyi.opc.content.enums.ContentPublishStatus;
import com.ruoyi.opc.content.enums.ContentScriptStatus;
import com.ruoyi.opc.content.mapper.OpcContentPublishMapper;
import com.ruoyi.opc.content.service.IOpcContentPlatformAccountService;
import com.ruoyi.opc.content.service.IOpcContentPublishService;
import com.ruoyi.opc.content.service.IOpcContentScriptService;
import com.ruoyi.opc.content.service.platform.PlatformClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布 Service 实现 — 脚本 → 抖音视频上传/创建 + 状态机 (PENDING → SUCCESS / FAILED → PENDING retry)。
 *
 * <p>W74 Task 4: 实现 + 跨租户隔离 + PlatformClient 占位(Task 7 真实接入)。
 *
 * <p>关键设计:
 * <ul>
 *   <li>{@code publish()} 守卫 script.status = READY(状态机)</li>
 *   <li>tags(String[]) 在 Service 层 {@code JSON.toJSONString} 序列化,VARCHAR(1024) 落库</li>
 *   <li>成功发布后 script.status = PUBLISHED</li>
 *   <li>{@code retry} 用 {@link ContentPublishStatus#canTransitionTo} 守卫 FAILED → PENDING</li>
 *   <li>通知 NotificationGateway 在 Task 8 接入</li>
 * </ul>
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcContentPublishServiceImpl implements IOpcContentPublishService {

    private final OpcContentPublishMapper publishMapper;
    /** 跨 Service 调用,用 @Lazy 避免循环依赖 */
    @Lazy
    private final IOpcContentScriptService scriptService;
    @Lazy
    private final IOpcContentPlatformAccountService accountService;
    /** W74 Task 4 占位,Task 7 真实接入 */
    private final PlatformClient platformClient;

    // ============================================================
    // publish
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publish(OpcContentPublishRequest req) {
        // 1) 入参校验
        if (req.getCompanyId() == null) {
            throw new ServiceException("companyId 不能为空");
        }
        if (req.getScriptId() == null) {
            throw new ServiceException("scriptId 不能为空");
        }
        if (req.getPlatformAccountId() == null) {
            throw new ServiceException("platformAccountId 不能为空");
        }
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new ServiceException("title 不能为空");
        }

        // 2) 校验 script 存在 + status = READY
        OpcContentScript script = scriptService.detail(req.getScriptId(), req.getCompanyId());
        if (!ContentScriptStatus.READY.getCode().equals(script.getStatus())) {
            throw new ServiceException(
                    "仅 READY 状态可发布 (当前: " + script.getStatus() + ")");
        }

        // 3) 校验 platform account 存在 + 跨公司
        accountService.detail(req.getPlatformAccountId(), req.getCompanyId());

        // 4) 调 PlatformClient 上传 + 创建(Task 4 占位,Task 7 真实)
        //    Task 4 阶段: videoBytes 暂为空数组(占位),真实视频字节流由 Task 7/8 接入
        byte[] videoBytes = new byte[0];
        String uploadFilename = "script_" + script.getId() + ".mp4";
        // TODO Task 7: 取真实 accessToken(decrypt) + 真实 video bytes
        String accessToken = "STUB_ACCESS_TOKEN";
        String externalVideoId = platformClient.uploadVideo(accessToken, videoBytes, uploadFilename);
        PlatformClient.PublishResult publishResult =
                platformClient.createVideo(accessToken, externalVideoId, req.getTitle(), req.getTags());

        // 5) 落 opc_content_publish(SUCCESS)
        LocalDateTime now = LocalDateTime.now();
        String tagsJson = req.getTags() == null ? "[]" : JSON.toJSONString(req.getTags());
        OpcContentPublish publish = OpcContentPublish.builder()
                .id(SnowflakeIdGenerator.nextId())
                .companyId(req.getCompanyId())
                .scriptId(script.getId())
                .platformAccountId(req.getPlatformAccountId())
                .platform(platformClient.platformName())
                .title(req.getTitle())
                .tags(tagsJson)
                .externalVideoId(externalVideoId)
                .externalPostId(publishResult == null ? null : publishResult.externalPostId())
                .externalUrl(publishResult == null ? null : publishResult.externalUrl())
                .status(ContentPublishStatus.SUCCESS.getCode())
                .errorCode(null)
                .errorMessage(null)
                .publishedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        publishMapper.insert(publish);

        // 6) 更新 script.status = PUBLISHED
        script.setStatus(ContentScriptStatus.PUBLISHED.getCode());
        script.setUpdatedAt(now);

        // 7) TODO Task 8: NotificationGateway.send(...) 发通知
        log.info("发布成功 publishId={} scriptId={} externalPostId={}",
                publish.getId(), script.getId(), publish.getExternalPostId());

        return publish.getId();
    }

    // ============================================================
    // listByCompany / detail / retry
    // ============================================================

    @Override
    public OpcContentListResponse<OpcContentPublish> listByCompany(Long companyId, String status,
                                                                    int page, int size) {
        if (companyId == null) {
            throw new ServiceException("companyId 不能为空");
        }
        if (page <= 0) page = 1;
        if (size <= 0) size = 20;
        int offset = (page - 1) * size;
        List<OpcContentPublish> rows = publishMapper.selectList(companyId, status, offset, size);
        int total = publishMapper.countList(companyId, status);
        return OpcContentListResponse.<OpcContentPublish>builder()
                .rows(rows)
                .total(total)
                .build();
    }

    @Override
    public OpcContentPublish detail(Long id, Long companyId) {
        if (id == null || companyId == null) {
            throw new ServiceException("id/companyId 不能为空");
        }
        OpcContentPublish p = publishMapper.selectById(id, companyId);
        if (p == null) {
            throw new ServiceException("发布记录不存在或无权访问 id=" + id);
        }
        return p;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retry(Long id, Long companyId) {
        OpcContentPublish existing = detail(id, companyId);
        ContentPublishStatus current;
        try {
            current = ContentPublishStatus.of(existing.getStatus());
        } catch (IllegalArgumentException e) {
            throw new ServiceException("发布状态非法: " + existing.getStatus());
        }
        // 状态机守卫: FAILED → PENDING 合法,其他状态抛错
        if (!current.canTransitionTo(ContentPublishStatus.PENDING)) {
            throw new ServiceException(
                    "发布状态非法: " + current.getCode() + " → PENDING 不允许");
        }
        existing.setStatus(ContentPublishStatus.PENDING.getCode());
        existing.setErrorCode(null);
        existing.setErrorMessage(null);
        existing.setUpdatedAt(LocalDateTime.now());
        publishMapper.updateById(existing);
        log.info("重试发布 id={} (FAILED → PENDING)", id);

        // TODO Task 7/8: 重新走完整 publish 流程(简化:此处只置 PENDING,worker 后续扫描重跑)
    }
}