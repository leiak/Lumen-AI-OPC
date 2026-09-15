package com.ruoyi.opc.content.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.utils.SnowflakeIdGenerator;
import com.ruoyi.opc.content.domain.OpcContentPublish;
import com.ruoyi.opc.content.domain.OpcContentScript;
import com.ruoyi.opc.content.dto.OpcContentDashboardDto;
import com.ruoyi.opc.content.dto.OpcContentGenerateRequest;
import com.ruoyi.opc.content.dto.OpcContentListResponse;
import com.ruoyi.opc.content.dto.OpcContentScriptDto;
import com.ruoyi.opc.content.enums.ContentPublishStatus;
import com.ruoyi.opc.content.enums.ContentScriptStatus;
import com.ruoyi.opc.content.enums.ContentScriptType;
import com.ruoyi.opc.content.mapper.OpcContentPublishMapper;
import com.ruoyi.opc.content.mapper.OpcContentScriptMapper;
import com.ruoyi.opc.content.service.IOpcContentScriptService;
import com.ruoyi.opc.content.service.llm.ContentLlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 内容脚本 Service 实现 — 短剧 / 视频 / 图文 / 适配。
 *
 * <p>W74 Task 4: 实现 + 状态机守卫 + 跨租户隔离。LLM 调用走 ContentLlmClient(Task 6 真实接入,Task 4 占位)。
 *
 * <p>关键设计:
 * <ul>
 *   <li>{@link ContentScriptStatus#DRAFT} 是 update/delete/markReady 的唯一合法前置态</li>
 *   <li>{@code create()} 强制非空校验 companyId/type/promptInput</li>
 *   <li>{@code @Transactional(rollbackFor = Exception.class)} 保证 mapper 失败回滚</li>
 *   <li>Dashboard 失败率公式: {@code countFailedLast7Days / countList * 100}(避免除 0)</li>
 * </ul>
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcContentScriptServiceImpl implements IOpcContentScriptService {

    private final OpcContentScriptMapper scriptMapper;
    private final OpcContentPublishMapper publishMapper;
    /** W74 Task 4 占位,Task 6 真实接入 opc-ai-core */
    private final ContentLlmClient contentLlmClient;

    // ============================================================
    // create
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OpcContentGenerateRequest req) {
        // 1) 入参校验
        if (req.getCompanyId() == null) {
            throw new ServiceException("companyId 不能为空");
        }
        if (req.getType() == null || req.getType().isBlank()) {
            throw new ServiceException("type 不能为空");
        }
        if (req.getPromptInput() == null || req.getPromptInput().isBlank()) {
            throw new ServiceException("promptInput 不能为空");
        }
        ContentScriptType type = ContentScriptType.of(req.getType());
        if (type == ContentScriptType.ADAPTER) {
            throw new ServiceException("ADAPTER 类型请走 /adapt 端点");
        }

        // 2) 调 LLM(Task 4 占位,Task 6 真实接入)
        String contentJson = "";
        String contentMd = "";
        switch (type) {
            case DRAMA:
                contentJson = contentLlmClient.generateDrama(req.getPromptInput());
                break;
            case VIDEO:
                contentJson = contentLlmClient.generateVideo(req.getPromptInput());
                break;
            case ARTICLE:
                contentMd = contentLlmClient.generateArticle(req.getPromptInput());
                break;
            default:
                throw new ServiceException("未实现的 type: " + type);
        }

        // 3) 入库(DRAFT)
        Long operatorId = getCurrentUserId();
        // W75-C: user_id NOT NULL,防止 SecurityUtils 失败时 userId=0L 被 mapper XML 跳过
        if (operatorId == null || operatorId == 0L) {
            operatorId = 1L; // dev fallback: admin user_id=1
        }
        OpcContentScript script = OpcContentScript.builder()
                .id(SnowflakeIdGenerator.nextId())
                .companyId(req.getCompanyId())
                .userId(operatorId)
                .type(type.getCode())
                .title(req.getTitle() == null || req.getTitle().isBlank() ? "未命名脚本" : req.getTitle())
                .promptInput(req.getPromptInput())
                .contentJson(contentJson == null ? "" : contentJson)
                .contentMd(contentMd == null ? "" : contentMd)
                .wordCount(contentMd == null ? 0 : contentMd.length())
                .status(ContentScriptStatus.DRAFT.getCode())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        scriptMapper.insert(script);
        log.info("创建脚本 id={} type={} title='{}' by user={}",
                script.getId(), type, script.getTitle(), operatorId);
        return script.getId();
    }

    // ============================================================
    // detail / list / update / delete
    // ============================================================

    @Override
    public OpcContentScript detail(Long id, Long companyId) {
        return validateAndGet(id, companyId);
    }

    @Override
    public OpcContentListResponse<OpcContentScript> list(Long companyId, String type, String status,
                                                          int page, int size) {
        if (companyId == null) {
            throw new ServiceException("companyId 不能为空");
        }
        if (page <= 0) page = 1;
        if (size <= 0) size = 20;
        int offset = (page - 1) * size;
        List<OpcContentScript> rows = scriptMapper.selectList(companyId, type, status, offset, size);
        int total = scriptMapper.countList(companyId, type, status);
        return OpcContentListResponse.<OpcContentScript>builder()
                .rows(rows)
                .total(total)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(Long id, Long companyId, OpcContentScriptDto dto) {
        OpcContentScript existing = validateAndGet(id, companyId);
        if (!ContentScriptStatus.DRAFT.getCode().equals(existing.getStatus())) {
            throw new ServiceException("仅 DRAFT 状态可修改 (当前: " + existing.getStatus() + ")");
        }
        if (dto != null) {
            if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
                existing.setTitle(dto.getTitle());
            }
            if (dto.getContentMd() != null) {
                existing.setContentMd(dto.getContentMd());
                existing.setWordCount(dto.getContentMd().length());
            }
        }
        existing.setUpdatedAt(LocalDateTime.now());
        int affected = scriptMapper.updateById(existing);
        log.info("更新脚本 id={} title='{}' affected={}", id, existing.getTitle(), affected);
        return affected;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id, Long companyId) {
        OpcContentScript existing = validateAndGet(id, companyId);
        if (!ContentScriptStatus.DRAFT.getCode().equals(existing.getStatus())) {
            throw new ServiceException("仅 DRAFT 状态可删除 (当前: " + existing.getStatus() + ")");
        }
        int affected = scriptMapper.softDeleteById(id, companyId);
        log.info("删除脚本 id={} (软删) affected={}", id, affected);
        return affected;
    }

    // ============================================================
    // regenerate / refine / markReady
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long regenerate(Long id, Long companyId, String promptInput) {
        if (promptInput == null || promptInput.isBlank()) {
            throw new ServiceException("promptInput 不能为空");
        }
        OpcContentScript existing = validateAndGet(id, companyId);
        ContentScriptType type = ContentScriptType.of(existing.getType());
        if (type == ContentScriptType.ADAPTER) {
            throw new ServiceException("ADAPTER 类型不支持 regenerate,请走 /adapt 端点");
        }
        doRegenerate(existing, promptInput, "regenerate");
        return id;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refine(Long id, Long companyId, Integer lineNo, String instruction) {
        if (lineNo == null || lineNo < 1) {
            throw new ServiceException("lineNo 必须 >= 1");
        }
        if (instruction == null || instruction.isBlank()) {
            throw new ServiceException("instruction 不能为空");
        }
        OpcContentScript existing = validateAndGet(id, companyId);
        // 简化实现:把指令注入到 prompt,重新生成(精修功能是 LLM 后续增强项)
        String newPrompt = existing.getPromptInput()
                + "\n\n[精修] 第 " + lineNo + " 行: " + instruction;
        doRegenerate(existing, newPrompt, "refine lineNo=" + lineNo);
        log.info("精修脚本 id={} lineNo={} instructionLen={}", id, lineNo, instruction.length());
    }

    /**
     * 私有生成逻辑:调 LLM + 持久化。无 @Transactional,由调用方(public regenerate/refine)的事务包裹。
     * 这样 {@code refine() → regenerate()} 的自调用绕过 @Transactional 代理问题被规避,
     * 所有调用都走同一个 transaction(由外层 public 方法启动)。
     */
    private void doRegenerate(OpcContentScript existing, String promptInput, String trigger) {
        ContentScriptType type = ContentScriptType.of(existing.getType());
        String contentJson = "";
        String contentMd = "";
        switch (type) {
            case DRAMA:
                contentJson = contentLlmClient.generateDrama(promptInput);
                break;
            case VIDEO:
                contentJson = contentLlmClient.generateVideo(promptInput);
                break;
            case ARTICLE:
                contentMd = contentLlmClient.generateArticle(promptInput);
                break;
            default:
                throw new ServiceException("不支持重新生成的类型: " + type);
        }
        existing.setPromptInput(promptInput);
        existing.setContentJson(contentJson == null ? "" : contentJson);
        existing.setContentMd(contentMd == null ? "" : contentMd);
        existing.setWordCount(contentMd == null ? 0 : contentMd.length());
        existing.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(existing);
        log.info("重新生成脚本 id={} type={} trigger={} newPromptLen={}",
                existing.getId(), type, trigger, promptInput.length());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markReady(Long id, Long companyId) {
        OpcContentScript existing = validateAndGet(id, companyId);
        if (!ContentScriptStatus.DRAFT.getCode().equals(existing.getStatus())) {
            throw new ServiceException("仅 DRAFT 状态可标记为 READY (当前: " + existing.getStatus() + ")");
        }
        existing.setStatus(ContentScriptStatus.READY.getCode());
        existing.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(existing);
        log.info("脚本标记为就绪 id={}", id);
    }

    // ============================================================
    // publishHistory
    // ============================================================

    @Override
    public List<OpcContentPublish> publishHistory(Long scriptId, Long companyId) {
        if (scriptId == null || companyId == null) {
            throw new ServiceException("scriptId/companyId 不能为空");
        }
        return publishMapper.selectByScript(scriptId, companyId);
    }

    // ============================================================
    // dashboard
    // ============================================================

    @Override
    public OpcContentDashboardDto dashboard(Long companyId) {
        if (companyId == null) {
            throw new ServiceException("companyId 不能为空");
        }
        int todayGenerate = scriptMapper.countTodayByCompany(companyId);
        int pendingPublish = publishMapper.countByStatus(companyId, "PENDING");
        int published = publishMapper.countByStatus(companyId, "SUCCESS");

        // 失败率 = 发布失败数 / 发布总数 * 100 (口径对齐:分子分母都是 publish 记录)
        long publishTotal = publishMapper.countList(companyId, null);
        long publishFailed = publishMapper.countByStatus(companyId, ContentPublishStatus.FAILED.getCode());
        BigDecimal failedRate = publishTotal == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(publishFailed * 10000L / publishTotal)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // 7 天趋势(Task 9 后续迭代,目前占位 7 个 0)
        List<Integer> sevenDayTrend = List.of(0, 0, 0, 0, 0, 0, 0);

        // 最近 5 脚本
        List<OpcContentScript> recent = scriptMapper.selectRecent(companyId, 5);
        List<OpcContentScriptDto> recentDtos = recent.stream().map(this::toDto).toList();

        return OpcContentDashboardDto.builder()
                .todayGenerate(todayGenerate)
                .pendingPublish(pendingPublish)
                .published(published)
                .failedRate(failedRate)
                .sevenDayTrend(sevenDayTrend)
                .recentScripts(recentDtos)
                .build();
    }

    // ============================================================
    // 内部辅助
    // ============================================================

    private OpcContentScript validateAndGet(Long id, Long companyId) {
        if (id == null || companyId == null) {
            throw new ServiceException("id/companyId 不能为空");
        }
        OpcContentScript s = scriptMapper.selectById(id, companyId);
        if (s == null) {
            throw new ServiceException("脚本不存在或无权访问 id=" + id);
        }
        return s;
    }

    private OpcContentScriptDto toDto(OpcContentScript s) {
        return OpcContentScriptDto.builder()
                .id(s.getId())
                .companyId(s.getCompanyId())
                .userId(s.getUserId())
                .type(s.getType())
                .title(s.getTitle())
                .promptInput(s.getPromptInput())
                .contentJson(s.getContentJson())
                .contentMd(s.getContentMd())
                .wordCount(s.getWordCount())
                .status(s.getStatus())
                .sourceScriptId(s.getSourceScriptId())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    /**
     * 获取当前登录用户 ID;若未登录(单元测试场景)返回 0L。
     */
    private Long getCurrentUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            return 0L;
        }
    }
}