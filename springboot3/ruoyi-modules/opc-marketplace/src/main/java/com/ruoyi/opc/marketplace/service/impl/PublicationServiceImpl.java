package com.ruoyi.opc.marketplace.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpAuthor;
import com.ruoyi.opc.marketplace.domain.MpCategory;
import com.ruoyi.opc.marketplace.domain.MpPublication;
import com.ruoyi.opc.marketplace.dto.PublicationCreateRequest;
import com.ruoyi.opc.marketplace.dto.PublicationEditRequest;
import com.ruoyi.opc.marketplace.dto.PublicationListQuery;
import com.ruoyi.opc.marketplace.dto.PublicationVO;
import com.ruoyi.opc.marketplace.mapper.MpAuthorMapper;
import com.ruoyi.opc.marketplace.mapper.MpCategoryMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.service.PublicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 上架商品 Service 实现 — P1
 *
 * <p><b>NOT NULL 列显式补默认（W50 教训）</b>：{@code mp_publication} 的
 * {@code product_type/author_id/code/name/category_code/status/view_count/install_count/
 * subscription_count/avg_rating/rating_count} 都是 NOT NULL。mapper XML 的 INSERT
 * 显式列出 {@code status}（{@code COALESCE(#{status},'DRAFT')}）→ MySQL <b>不</b>应用
 * DDL 里的 {@code DEFAULT}，必须由 service 层给值。其余计数列当前不在 INSERT 列清单里，
 * 走 DDL DEFAULT；service 层仍然显式赋值，避免日后有人把这些列加进 INSERT 时炸掉。
 *
 * <p><b>{@code update_by} 只写操作者 userId，永不写 authorId</b>：{@code mp_publication}
 * 只有一列 {@code create_by/update_by}，而「归属」（authorId，取自登录租户的作者档案）与
 * 「操作」（operatorId，取自 {@code SecurityUtils.getUserId()}）是两个不同的 id。
 * 早期实现把 authorId 写进 {@code update_by}，与 {@code create} 写的 userId 混在同一列里，
 * 审计时无法判断是谁改的。现在全部写方法统一写 operatorId（与 RuoYi 其他模块一致），
 * authorId 只用于归属校验（{@link #requireOwnership}），不进 {@code create_by/update_by}。
 *
 * <p><b>ObjectMapper 是 {@code new} 出来的，不是 Spring bean</b>：本类只用它把
 * {@code List<String>} 序列化成 JSON 数组，不涉及 domain 对象的自定义模块，
 * 因此不需要 Spring 的全局 Jackson 配置。若日后要用它序列化带 {@code @JsonFormat}
 * 或 snake_case 约定的对象，必须改成注入容器里的 {@code ObjectMapper}
 * （否则时间格式 / 别名会静默走默认值）。
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicationServiceImpl implements PublicationService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_DEPRECATED = "DEPRECATED";

    /** {@link #online} 允许的上架来源状态（P1 的可达状态集合，其余一律 400 fail-closed） */
    private static final Set<String> ONLINE_FROM = Set.of(STATUS_DRAFT, STATUS_DEPRECATED);

    private final MpPublicationMapper publicationMapper;
    private final MpCategoryMapper categoryMapper;
    /** 注入以支持列表页一次性批量填充 authorName/authorAvatar (避免 N+1) */
    private final MpAuthorMapper authorMapper;

    /**
     * 手写实例，不用容器 bean —— 仅用于 {@code List<String>} → JSON 数组。
     * 注意 Lombok 的 {@code @RequiredArgsConstructor} 会跳过<b>已初始化</b>的 final 字段，
     * 因此本字段不进构造器，单元测试里 {@code new PublicationServiceImpl(pub, cat, auth)} 即可。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 写 ====================

    @Override
    public MpPublication create(PublicationCreateRequest req, Long authorId, Long operatorId) {
        // 1. 类型与挂载对象必须自洽
        if ("AGENT".equals(req.getProductType()) && req.getAgentDefinitionId() == null) {
            throw new OpcException(400, "AGENT 类型必须传 agentDefinitionId", "MARKETPLACE");
        }
        if ("MODULE".equals(req.getProductType()) && req.getModuleId() == null) {
            throw new OpcException(400, "MODULE 类型必须传 moduleId", "MARKETPLACE");
        }
        // 2. 类目必须是 (productType, code) 上真实存在的一行
        MpCategory cat = categoryMapper.selectByCode(req.getProductType(), req.getCategoryCode());
        if (cat == null) {
            throw new OpcException(400, "类目不存在: " + req.getCategoryCode(), "MARKETPLACE");
        }
        // 3. code 全局唯一(uk_pub_code 不含 deleted) —— 先查一次给出友好 409,
        //    真正 INSERT 时仍可能撞 1062(并发),由 SQL 兜底
        MpPublication existing = publicationMapper.selectByCode(req.getCode());
        if (existing != null) {
            throw new OpcException(409, "商品 code 已存在: " + req.getCode(), "MARKETPLACE");
        }

        MpPublication p = new MpPublication();
        p.setProductType(req.getProductType());
        p.setAgentDefinitionId(req.getAgentDefinitionId());
        p.setModuleId(req.getModuleId());
        p.setAuthorId(authorId);
        p.setCode(req.getCode());
        p.setName(req.getName());
        p.setCategoryCode(req.getCategoryCode());
        p.setSubCategoryCode(req.getSubCategoryCode());
        p.setSummary(req.getSummary());
        p.setLongDescription(req.getLongDescription());
        p.setIconUrl(req.getIconUrl());
        p.setTags(req.getTags());
        p.setStatus(STATUS_DRAFT);
        // 显式补 NOT NULL 默认(见类注释的 W50 教训)
        p.setViewCount(0);
        p.setInstallCount(0);
        p.setSubscriptionCount(0);
        p.setAvgRating(BigDecimal.ZERO);
        p.setRatingCount(0);
        p.setCreateBy(String.valueOf(operatorId));
        p.setUpdateBy(String.valueOf(operatorId));
        if (req.getScreenshots() != null) {
            p.setScreenshots(serializeScreenshots(req.getScreenshots()));
        }
        publicationMapper.insert(p);
        log.info("[Publication] created id={} code={} authorId={}", p.getId(), p.getCode(), authorId);
        return p;
    }

    @Override
    public MpPublication edit(Long id, PublicationEditRequest req, Long authorId, Long operatorId) {
        // 作者侧读路径:走 getById(不过滤 status),否则作者改不了自己的 DRAFT
        MpPublication p = getById(id);
        requireOwnership(p, authorId, "只有作者本人可编辑");
        // 局部更新:null = 不改(无法把字段显式置 null,这是本 API 的既定语义)
        if (req.getName() != null) p.setName(req.getName());
        if (req.getCategoryCode() != null) {
            // 与 create() 第 2 步同一条守卫。不校验就会落一个指向空行的 category_code:
            // 商品照常上架,列表里 categoryName 恒为 null,category= 过滤 join 到孤儿。
            // edit 请求体刻意不带 productType(不可改),所以用库里这一行自己的
            // product_type 去查 —— uk_cat_code 是 (product_type, code) 复合唯一键。
            MpCategory cat = categoryMapper.selectByCode(p.getProductType(), req.getCategoryCode());
            if (cat == null) {
                throw new OpcException(400, "类目不存在: " + req.getCategoryCode(), "MARKETPLACE");
            }
            p.setCategoryCode(req.getCategoryCode());
        }
        if (req.getSubCategoryCode() != null) p.setSubCategoryCode(req.getSubCategoryCode());
        if (req.getSummary() != null) p.setSummary(req.getSummary());
        if (req.getLongDescription() != null) p.setLongDescription(req.getLongDescription());
        if (req.getIconUrl() != null) p.setIconUrl(req.getIconUrl());
        if (req.getTags() != null) p.setTags(req.getTags());
        if (req.getScreenshots() != null) p.setScreenshots(serializeScreenshots(req.getScreenshots()));
        p.setUpdateBy(String.valueOf(operatorId));
        publicationMapper.updateById(p);
        return publicationMapper.selectById(id);
    }

    @Override
    public MpPublication submitAudit(Long id, Long authorId, Long operatorId) {
        MpPublication p = getById(id);
        requireOwnership(p, authorId, "只有作者本人可提交");
        if (!STATUS_DRAFT.equals(p.getStatus())) {
            throw new OpcException(400, "只有 DRAFT 状态可提交审核", "MARKETPLACE");
        }
        // P1: auto-approve(P3 接入真实审核流后,这里改为置 PENDING)
        return publish(p, authorId, operatorId);
    }

    @Override
    public MpPublication online(Long id, Long authorId, Long operatorId) {
        MpPublication p = getById(id);
        requireOwnership(p, authorId, "只有作者本人可上架");
        if (!ONLINE_FROM.contains(p.getStatus())) {
            throw new OpcException(400,
                    "当前状态不可上架: " + p.getStatus() + "（允许 " + ONLINE_FROM + "）", "MARKETPLACE");
        }
        return publish(p, authorId, operatorId);
    }

    @Override
    public MpPublication offline(Long id, Long authorId, Long operatorId) {
        MpPublication p = getById(id);
        requireOwnership(p, authorId, "只有作者本人可下架");
        p.setStatus(STATUS_DEPRECATED);
        p.setUpdateBy(String.valueOf(operatorId));
        publicationMapper.updateById(p);
        log.info("[Publication] offline id={} authorId={} operatorId={}", id, authorId, operatorId);
        return p;
    }

    // ==================== 读 ====================

    @Override
    public MpPublication getById(Long id) {
        MpPublication p = publicationMapper.selectById(id);
        if (p == null) {
            throw new OpcException(404, "商品不存在: " + id, "MARKETPLACE");
        }
        return p;
    }

    @Override
    public MpPublication getPublishedById(Long id) {
        // SQL 硬编码 status='PUBLISHED';非 PUBLISHED 与不存在返回同一个 null → 同一个 404,
        // 不泄露「这个 id 有个草稿」这一信息
        MpPublication p = publicationMapper.selectPublishedById(id);
        if (p == null) {
            throw new OpcException(404, "商品不存在: " + id, "MARKETPLACE");
        }
        return p;
    }

    // ==================== VO ====================

    @Override
    public PublicationVO toVO(MpPublication p) {
        PublicationVO vo = baseVO(p);
        // 单条路径:author + category 各 1 次 DB 命中
        if (p.getAuthorId() != null) {
            MpAuthor a = authorMapper.selectById(p.getAuthorId());
            if (a != null) {
                vo.setAuthorName(a.getAuthorName());
                vo.setAuthorAvatar(a.getAvatarUrl());
            }
        }
        if (p.getProductType() != null && p.getCategoryCode() != null) {
            MpCategory cat = categoryMapper.selectByCode(p.getProductType(), p.getCategoryCode());
            if (cat != null) {
                vo.setCategoryName(cat.getName());
            }
        }
        return vo;
    }

    /**
     * 两条 VO 路径的公共部分。
     *
     * <p>刻意抽出来而不是各写一份 20 行 setter：漏抄一个字段（比如新加的 {@code viewCount}）
     * 在两个重载之间不会有任何编译错误，只会让某个列表页悄悄少一个字段。
     */
    private PublicationVO baseVO(MpPublication p) {
        PublicationVO vo = new PublicationVO();
        vo.setId(p.getId());
        vo.setProductType(p.getProductType());
        vo.setAgentDefinitionId(p.getAgentDefinitionId());
        vo.setModuleId(p.getModuleId());
        vo.setAuthorId(p.getAuthorId());
        vo.setCode(p.getCode());
        vo.setName(p.getName());
        vo.setCategoryCode(p.getCategoryCode());
        vo.setSubCategoryCode(p.getSubCategoryCode());
        vo.setSummary(p.getSummary());
        vo.setLongDescription(p.getLongDescription());
        vo.setIconUrl(p.getIconUrl());
        vo.setScreenshots(p.getScreenshots());
        vo.setTags(p.getTags());
        vo.setStatus(p.getStatus());
        vo.setPublishedAt(p.getPublishedAt());
        vo.setViewCount(p.getViewCount());
        vo.setInstallCount(p.getInstallCount());
        vo.setSubscriptionCount(p.getSubscriptionCount());
        vo.setAvgRating(p.getAvgRating());
        vo.setRatingCount(p.getRatingCount());
        vo.setCreateTime(p.getCreateTime());
        return vo;
    }

    /**
     * 列表路径专用 VO 转换 — 作者 + 类目 都批量预加载 (2N → 2)。
     * {@code categoryName} 通过 {@code Map<productType|code, MpCategory>} 一次批量加载,
     * 替代单行 {@code selectByCode} 的 N 次查询。
     */
    private PublicationVO toVO(MpPublication p, Map<Long, MpAuthor> authors,
                               Map<String, MpCategory> categories) {
        PublicationVO vo = baseVO(p);
        MpAuthor a = authors.get(p.getAuthorId());
        if (a != null) {
            vo.setAuthorName(a.getAuthorName());
            vo.setAuthorAvatar(a.getAvatarUrl());
        }
        if (p.getProductType() != null && p.getCategoryCode() != null) {
            MpCategory cat = categories.get(categoryKey(p.getProductType(), p.getCategoryCode()));
            if (cat != null) {
                vo.setCategoryName(cat.getName());
            }
        }
        return vo;
    }

    // ==================== 列表 ====================

    @Override
    public Map<String, Object> list(PublicationListQuery query) {
        Map<String, Object> params = new HashMap<>();
        params.put("category", query.getCategory());
        params.put("productType", query.getProductType());
        params.put("keyword", query.getKeyword());
        // 公开端点(网关白名单)恒定只返回 PUBLISHED。
        // 若允许调用方传 status,匿名用户可枚举 DRAFT/PENDING/SUSPENDED —— 见 Task3 审查 Critical#1。
        // 作者查看自己的全部状态请走 /my/list (需登录),由 listByAuthorVOs 提供。
        params.put("status", STATUS_PUBLISHED);
        params.put("sortBy", query.getSortBy() != null ? query.getSortBy() : "newest");
        // ?page= 空串 / 负数会让 Spring 绑成 null,MySQL LIMIT offset 为负直接报错 —— 夹紧
        int page = (query.getPage() != null && query.getPage() > 0) ? query.getPage() : 1;
        int pageSize = (query.getPageSize() != null && query.getPageSize() > 0) ? query.getPageSize() : 12;
        params.put("offset", (page - 1) * pageSize);
        params.put("limit", pageSize);

        List<MpPublication> rows = publicationMapper.selectList(params);
        int total = publicationMapper.countList(params);

        // 一次性批量加载作者 + 类目,避免 2N+1 (N 行 × author/category 各 1 次单查)
        BatchedJoins joins = loadJoins(rows);

        Map<String, Object> data = new HashMap<>();
        data.put("rows", rows.stream().map(p -> toVO(p, joins.authors(), joins.categories()))
                .collect(Collectors.toList()));
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    @Override
    public List<MpPublication> listByAuthor(Long authorId) {
        return publicationMapper.selectByAuthorId(authorId);
    }

    @Override
    public List<PublicationVO> listByAuthorVOs(Long authorId) {
        List<MpPublication> rows = publicationMapper.selectByAuthorId(authorId);
        if (rows.isEmpty()) {
            // 空集合必须短路 —— 否则下面的 selectByIds / selectByCodes 会发出非法的 IN ()
            return List.of();
        }
        BatchedJoins joins = loadJoins(rows);
        return rows.stream().map(p -> toVO(p, joins.authors(), joins.categories()))
                .collect(Collectors.toList());
    }

    // ==================== 内部 ====================

    /**
     * 批量预取一批商品的作者 + 类目。
     *
     * <p>两个 mapper 的 javadoc 都写明：<b>传入空集合不要调用</b>（MyBatis 会发出 {@code IN ()}），
     * 因此每个桶都要判空。类目按 productType 分桶（{@code uk_cat_code} 是
     * {@code (product_type, code)} 复合唯一键，AGENT 与 MODULE 的 code 可以重名）。
     */
    private BatchedJoins loadJoins(List<MpPublication> rows) {
        if (rows == null || rows.isEmpty()) {
            return new BatchedJoins(Collections.emptyMap(), Collections.emptyMap());
        }
        Set<Long> authorIds = new HashSet<>();
        Map<String, Set<String>> codesByType = new HashMap<>();
        for (MpPublication p : rows) {
            if (p.getAuthorId() != null) {
                authorIds.add(p.getAuthorId());
            }
            if (p.getProductType() != null && p.getCategoryCode() != null) {
                codesByType.computeIfAbsent(p.getProductType(), k -> new HashSet<>())
                        .add(p.getCategoryCode());
            }
        }

        Map<Long, MpAuthor> authors = Collections.emptyMap();
        if (!authorIds.isEmpty()) {
            authors = authorMapper.selectByIds(authorIds).stream()
                    .collect(Collectors.toMap(MpAuthor::getId, a -> a, (x, y) -> x));
        }

        Map<String, MpCategory> categories = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : codesByType.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            for (MpCategory c : categoryMapper.selectByCodes(e.getKey(), e.getValue())) {
                categories.put(categoryKey(e.getKey(), c.getCode()), c);
            }
        }
        return new BatchedJoins(authors, categories);
    }

    private static String categoryKey(String productType, String code) {
        return productType + "|" + code;
    }

    private String serializeScreenshots(List<String> screenshots) {
        try {
            return objectMapper.writeValueAsString(screenshots);
        } catch (JsonProcessingException e) {
            log.warn("[Publication] screenshots 序列化失败: {}", e.getMessage());
            throw new OpcException(400, "screenshots JSON 序列化失败", "MARKETPLACE");
        }
    }

    /**
     * 归属校验。用 {@link Objects#equals} 而不是 {@code p.getAuthorId().equals(authorId)} ——
     * 后者在 authorId 为 null 时直接 NPE(500),前者返回 false → 403,fail-closed。
     */
    private void requireOwnership(MpPublication p, Long authorId, String message) {
        if (!Objects.equals(p.getAuthorId(), authorId)) {
            throw new OpcException(403, message, "MARKETPLACE");
        }
    }

    /** {@code publish} 之外的公共上架动作(P1 自动过审) */
    private MpPublication publish(MpPublication p, Long authorId, Long operatorId) {
        p.setStatus(STATUS_PUBLISHED);
        p.setPublishedAt(LocalDateTime.now());
        p.setUpdateBy(String.valueOf(operatorId));
        publicationMapper.updateById(p);
        log.info("[Publication] published id={} authorId={} operatorId={}", p.getId(), authorId, operatorId);
        return p;
    }

    /** 一次批量预取的作者 / 类目索引 */
    private record BatchedJoins(Map<Long, MpAuthor> authors, Map<String, MpCategory> categories) {
    }
}
