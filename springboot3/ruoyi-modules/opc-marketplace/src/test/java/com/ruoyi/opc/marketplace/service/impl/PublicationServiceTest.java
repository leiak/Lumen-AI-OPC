package com.ruoyi.opc.marketplace.service.impl;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PublicationServiceImpl} 单元测试 — P1 Task 5
 *
 * <p>覆盖四类契约：
 * <ol>
 *   <li><b>状态机</b>：create→DRAFT / submitAudit DRAFT→PUBLISHED / online DEPRECATED→PUBLISHED /
 *       offline→DEPRECATED，非法来源状态 400，非本人 403</li>
 *   <li><b>两条读路径的语义差异</b>：{@code getById} 不过滤 status（作者能改自己的 DRAFT），
 *       {@code getPublishedById} 非 PUBLISHED 一律 404（公开端点不泄露草稿存在性）</li>
 *   <li><b>list 恒定 PUBLISHED + 批量装配</b>：status 硬编码、作者一次 {@code selectByIds}、
 *       类目按 productType 各一次 {@code selectByCodes}、空结果集不发 {@code IN ()}</li>
 *   <li><b>NOT NULL 补默认（W50 教训）</b>：INSERT 显式列名时不应用 DDL DEFAULT，
 *       service 必须自己给值</li>
 *   <li><b>类目外键在 create 与 edit 两侧都被守卫</b>：未知 categoryCode 一律 400，
 *       不落指向空 {@code mp_category} 行的外键</li>
 *   <li><b>{@code update_by} 只记 operatorId</b>：authorId(档案 id) 与 operatorId(用户 id)
 *       是两个值，审计列里出现 authorId 就是回归</li>
 * </ol>
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicationServiceTest {

    @Mock
    MpPublicationMapper publicationMapper;
    @Mock
    MpCategoryMapper categoryMapper;
    @Mock
    MpAuthorMapper authorMapper;

    @InjectMocks
    PublicationServiceImpl service;

    // ==================== fixtures ====================

    private static MpPublication pub(long id, long authorId, String status, String type, String category) {
        return MpPublication.builder()
                .id(id).authorId(authorId).code("code-" + id).name("商品" + id)
                .productType(type).categoryCode(category).status(status)
                .viewCount(1).installCount(2).subscriptionCount(3)
                .avgRating(new BigDecimal("4.50")).ratingCount(6)
                .createTime(LocalDateTime.now())
                .build();
    }

    private static MpCategory cat(String type, String code, String name) {
        return MpCategory.builder().id(1L).productType(type).code(code).name(name).build();
    }

    private static MpAuthor author(long id, String name) {
        return MpAuthor.builder().id(id).authorName(name).avatarUrl("a" + id + ".png").build();
    }

    private static PublicationCreateRequest createReq(String type) {
        PublicationCreateRequest req = new PublicationCreateRequest();
        req.setProductType(type);
        req.setCode("agent-1");
        req.setName("智能体一");
        req.setCategoryCode("office");
        if ("AGENT".equals(type)) {
            req.setAgentDefinitionId(100L);
        } else {
            req.setModuleId(200L);
        }
        return req;
    }

    // ==================== create ====================

    @Test
    @DisplayName("create — AGENT: 落库 DRAFT + 补齐 NOT NULL 列 + screenshots 序列化成 JSON 数组")
    void create_success_AGENT() {
        when(categoryMapper.selectByCode("AGENT", "office")).thenReturn(cat("AGENT", "office", "办公"));
        when(publicationMapper.selectByCode("agent-1")).thenReturn(null);

        PublicationCreateRequest req = createReq("AGENT");
        req.setScreenshots(List.of("a.png", "b.png"));
        MpPublication result = service.create(req, 10L, 7L);

        assertThat(result.getStatus()).as("新商品必须是 DRAFT").isEqualTo("DRAFT");
        assertThat(result.getAuthorId()).isEqualTo(10L);
        assertThat(result.getAgentDefinitionId()).isEqualTo(100L);
        assertThat(result.getScreenshots()).isEqualTo("[\"a.png\",\"b.png\"]");

        var captor = ArgumentCaptor.forClass(MpPublication.class);
        verify(publicationMapper).insert(captor.capture());
        MpPublication inserted = captor.getValue();
        // W50 教训:mapper XML 显式列出这些列 → MySQL 不应用 DDL DEFAULT,必须 service 给值
        assertThat(inserted.getViewCount()).isZero();
        assertThat(inserted.getInstallCount()).isZero();
        assertThat(inserted.getSubscriptionCount()).isZero();
        assertThat(inserted.getRatingCount()).isZero();
        assertThat(inserted.getAvgRating()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(inserted.getCreateBy()).isEqualTo("7");
        assertThat(inserted.getUpdateBy()).isEqualTo("7");
    }

    @Test
    @DisplayName("create — MODULE: 挂 moduleId,不带 agentDefinitionId")
    void create_success_MODULE() {
        when(categoryMapper.selectByCode("MODULE", "office")).thenReturn(cat("MODULE", "office", "办公"));
        when(publicationMapper.selectByCode("agent-1")).thenReturn(null);

        MpPublication result = service.create(createReq("MODULE"), 10L, 7L);

        assertThat(result.getModuleId()).isEqualTo(200L);
        assertThat(result.getAgentDefinitionId()).isNull();
        assertThat(result.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("create — AGENT 缺 agentDefinitionId → 400,不入库")
    void create_400_whenAgentMissingId() {
        PublicationCreateRequest req = createReq("AGENT");
        req.setAgentDefinitionId(null);

        assertThatThrownBy(() -> service.create(req, 10L, 7L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("agentDefinitionId");
        verify(publicationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("create — 类目不存在 → 400,不入库")
    void create_400_whenCategoryMissing() {
        when(categoryMapper.selectByCode("AGENT", "office")).thenReturn(null);

        assertThatThrownBy(() -> service.create(createReq("AGENT"), 10L, 7L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("类目不存在");
        verify(publicationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("create — code 已存在 → 409(uk_pub_code 不含 deleted,重名即永久占位)")
    void create_409_whenCodeExists() {
        when(categoryMapper.selectByCode("AGENT", "office")).thenReturn(cat("AGENT", "office", "办公"));
        when(publicationMapper.selectByCode("agent-1")).thenReturn(pub(1L, 10L, "PUBLISHED", "AGENT", "office"));

        assertThatThrownBy(() -> service.create(createReq("AGENT"), 10L, 7L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("code 已存在");
        verify(publicationMapper, never()).insert(any());
    }

    // ==================== edit ====================

    @Test
    @DisplayName("edit — 本人 DRAFT 可编辑:走 getById(不过滤 status),局部更新后返回重读行")
    void edit_success_onOwnDraft() {
        MpPublication draft = pub(1L, 10L, "DRAFT", "AGENT", "office");
        MpPublication refreshed = pub(1L, 10L, "DRAFT", "AGENT", "office");
        refreshed.setName("新名字");
        refreshed.setScreenshots("[\"x.png\"]");
        when(publicationMapper.selectById(1L)).thenReturn(draft, refreshed);
        // 本用例不传 categoryCode → 不触发类目守卫,mock 保持未命中即可

        PublicationEditRequest req = new PublicationEditRequest();
        req.setName("新名字");
        req.setScreenshots(List.of("x.png"));
        MpPublication result = service.edit(1L, req, 10L, 7L);

        assertThat(result.getName()).as("返回的是写库后的重读行").isEqualTo("新名字");
        assertThat(result.getScreenshots()).isEqualTo("[\"x.png\"]");
        assertThat(result.getSummary()).as("未传的字段保持原值(不写库)").isNull();

        var captor = ArgumentCaptor.forClass(MpPublication.class);
        verify(publicationMapper).updateById(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("新名字");
        // update_by 必须是 operatorId(7),绝不是 authorId(10)
        assertThat(captor.getValue().getUpdateBy()).isEqualTo("7");

        // 关键回归:编辑路径绝不能走公开详情(否则作者改不了自己的 DRAFT)
        verify(publicationMapper, never()).selectPublishedById(any());
    }

    @Test
    @DisplayName("edit — 改类目:按该商品自己的 productType 校验,合法类目才写库")
    void edit_success_whenCategoryExists() {
        MpPublication draft = pub(1L, 10L, "DRAFT", "AGENT", "office");
        when(publicationMapper.selectById(1L)).thenReturn(draft);
        when(categoryMapper.selectByCode("AGENT", "finance")).thenReturn(cat("AGENT", "finance", "财务"));

        PublicationEditRequest req = new PublicationEditRequest();
        req.setCategoryCode("finance");
        service.edit(1L, req, 10L, 7L);

        // uk_cat_code 是 (product_type, code) 复合键 —— 必须带上 AGENT 去查
        verify(categoryMapper).selectByCode("AGENT", "finance");
        var captor = ArgumentCaptor.forClass(MpPublication.class);
        verify(publicationMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCategoryCode()).isEqualTo("finance");
    }

    @Test
    @DisplayName("edit — 类目不存在 → 400 且不写库(否则 category_code 指向空 mp_category 行)")
    void edit_400_whenCategoryNotFound() {
        when(publicationMapper.selectById(1L)).thenReturn(pub(1L, 10L, "DRAFT", "AGENT", "office"));
        when(categoryMapper.selectByCode("AGENT", "not-exist")).thenReturn(null);

        PublicationEditRequest req = new PublicationEditRequest();
        req.setCategoryCode("not-exist");

        Throwable e = catchThrowable(() -> service.edit(1L, req, 10L, 7L));

        assertThat(e).isInstanceOf(OpcException.class)
                .hasMessageContaining("类目不存在");
        assertThat(((OpcException) e).getCode())
                .as("与 create() 同一条守卫:必须是 400,不能是 404/500").isEqualTo(400);
        verify(publicationMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("edit — 非本人 → 403,且不写库")
    void edit_403_whenNotAuthor() {
        when(publicationMapper.selectById(1L)).thenReturn(pub(1L, 10L, "DRAFT", "AGENT", "office"));

        assertThatThrownBy(() -> service.edit(1L, new PublicationEditRequest(), 999L, 7L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("只有作者本人可编辑");
        verify(publicationMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("edit — 商品不存在 → 404")
    void edit_404_whenMissing() {
        when(publicationMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.edit(999L, new PublicationEditRequest(), 10L, 7L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("商品不存在");
        verify(publicationMapper, never()).updateById(any());
    }

    // ==================== 状态机 ====================

    @Test
    @DisplayName("submitAudit — DRAFT → PUBLISHED,写 publishedAt / updateBy")
    void submitAudit_success() {
        MpPublication draft = pub(1L, 10L, "DRAFT", "AGENT", "office");
        when(publicationMapper.selectById(1L)).thenReturn(draft);

        MpPublication result = service.submitAudit(1L, 10L, 7L);

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        assertThat(result.getPublishedAt()).isNotNull();
        // update_by 是 operatorId(7),不是 authorId(10) —— 审计列只认操作用户 id
        assertThat(result.getUpdateBy()).isEqualTo("7");
        var captor = ArgumentCaptor.forClass(MpPublication.class);
        verify(publicationMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
        assertThat(captor.getValue().getUpdateBy()).isEqualTo("7");
    }

    @Test
    @DisplayName("submitAudit — 非 DRAFT(已 PUBLISHED)→ 400,不改状态")
    void submitAudit_400_whenNotDraft() {
        when(publicationMapper.selectById(1L)).thenReturn(pub(1L, 10L, "PUBLISHED", "AGENT", "office"));

        assertThatThrownBy(() -> service.submitAudit(1L, 10L, 7L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("只有 DRAFT 状态可提交审核");
        verify(publicationMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("submitAudit — 非本人 → 403")
    void submitAudit_403_whenNotAuthor() {
        when(publicationMapper.selectById(1L)).thenReturn(pub(1L, 10L, "DRAFT", "AGENT", "office"));

        assertThatThrownBy(() -> service.submitAudit(1L, 999L, 7L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("只有作者本人可提交");
        verify(publicationMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("offline — PUBLISHED → DEPRECATED")
    void offline_returnsDeprecated() {
        when(publicationMapper.selectById(1L)).thenReturn(pub(1L, 10L, "PUBLISHED", "AGENT", "office"));

        MpPublication result = service.offline(1L, 10L, 7L);

        assertThat(result.getStatus()).isEqualTo("DEPRECATED");
        var captor = ArgumentCaptor.forClass(MpPublication.class);
        verify(publicationMapper).updateById(captor.capture());
        assertThat(captor.getValue().getUpdateBy())
                .as("update_by 记操作用户 id,不记 authorId").isEqualTo("7");
    }

    @Test
    @DisplayName("offline — 非本人 → 403")
    void offline_403_whenNotAuthor() {
        when(publicationMapper.selectById(1L)).thenReturn(pub(1L, 10L, "PUBLISHED", "AGENT", "office"));

        assertThatThrownBy(() -> service.offline(1L, 999L, 7L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("只有作者本人可下架");
        verify(publicationMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("online — 往返回归:offline(→DEPRECATED) 后能重新 online(→PUBLISHED)")
    void online_republish_afterOffline() {
        // 照搬计划里的 online=submitAudit 会让这一步 400(「只有 DRAFT 可提交」),
        // 商品一旦下架就永远回不了市场 —— 本用例锁住往返能力
        when(publicationMapper.selectById(1L)).thenReturn(pub(1L, 10L, "DEPRECATED", "AGENT", "office"));

        MpPublication result = service.online(1L, 10L, 7L);

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        assertThat(result.getPublishedAt()).isNotNull();
        var captor = ArgumentCaptor.forClass(MpPublication.class);
        verify(publicationMapper).updateById(captor.capture());
        assertThat(captor.getValue().getUpdateBy()).isEqualTo("7");
    }

    @Test
    @DisplayName("online — DRAFT 首次上架 → PUBLISHED")
    void online_fromDraft() {
        when(publicationMapper.selectById(1L)).thenReturn(pub(1L, 10L, "DRAFT", "AGENT", "office"));

        assertThat(service.online(1L, 10L, 7L).getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("online — 已经 PUBLISHED → 400(幂等保护,不重复写 publishedAt)")
    void online_400_whenAlreadyPublished() {
        when(publicationMapper.selectById(1L)).thenReturn(pub(1L, 10L, "PUBLISHED", "AGENT", "office"));

        assertThatThrownBy(() -> service.online(1L, 10L, 7L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("当前状态不可上架");
        verify(publicationMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("online — 非本人 → 403")
    void online_403_whenNotAuthor() {
        when(publicationMapper.selectById(1L)).thenReturn(pub(1L, 10L, "DRAFT", "AGENT", "office"));

        assertThatThrownBy(() -> service.online(1L, 999L, 7L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("只有作者本人可上架");
        verify(publicationMapper, never()).updateById(any());
    }

    // ==================== 两条读路径 ====================

    @Test
    @DisplayName("getById — 不过滤 status:DRAFT 也能读到(作者侧路径)")
    void getById_returnsDraft() {
        when(publicationMapper.selectById(1L)).thenReturn(pub(1L, 10L, "DRAFT", "AGENT", "office"));

        assertThat(service.getById(1L).getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("getById — 不存在 → 404")
    void getById_404_whenMissing() {
        when(publicationMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("商品不存在");
    }

    @Test
    @DisplayName("getPublishedById — 非 PUBLISHED/不存在 → 404,且用的是 selectPublishedById(公开端点不得枚举草稿)")
    void getPublishedById_404_whenMissing() {
        when(publicationMapper.selectPublishedById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.getPublishedById(999L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("商品不存在");

        // 公开端点必须走带 status 过滤的那个 SQL
        verify(publicationMapper).selectPublishedById(999L);
        verify(publicationMapper, never()).selectById(999L);
    }

    @Test
    @DisplayName("getPublishedById — PUBLISHED 正常返回")
    void getPublishedById_success() {
        when(publicationMapper.selectPublishedById(1L)).thenReturn(pub(1L, 10L, "PUBLISHED", "AGENT", "office"));

        assertThat(service.getPublishedById(1L).getStatus()).isEqualTo("PUBLISHED");
    }

    // ==================== VO 装配 ====================

    @Test
    @DisplayName("toVO — 单条路径:字段全量拷贝 + author/category 各 1 次单查并填充冗余名")
    void toVO_copiesFieldsAndJoins() {
        MpPublication p = pub(1L, 10L, "PUBLISHED", "AGENT", "office");
        when(authorMapper.selectById(10L)).thenReturn(author(10L, "某企业"));
        when(categoryMapper.selectByCode("AGENT", "office")).thenReturn(cat("AGENT", "office", "办公"));

        PublicationVO vo = service.toVO(p);

        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getName()).isEqualTo("商品1");
        assertThat(vo.getViewCount()).isEqualTo(1);
        assertThat(vo.getInstallCount()).isEqualTo(2);
        assertThat(vo.getSubscriptionCount()).isEqualTo(3);
        assertThat(vo.getRatingCount()).isEqualTo(6);
        assertThat(vo.getAvgRating()).isEqualByComparingTo("4.50");
        assertThat(vo.getAuthorName()).isEqualTo("某企业");
        assertThat(vo.getAuthorAvatar()).isEqualTo("a10.png");
        assertThat(vo.getCategoryName()).isEqualTo("办公");
    }

    @Test
    @DisplayName("toVO — 作者/类目查不到时留空,不影响其余字段")
    void toVO_missingJoins_leaveNull() {
        when(authorMapper.selectById(any())).thenReturn(null);
        when(categoryMapper.selectByCode(anyString(), anyString())).thenReturn(null);

        PublicationVO vo = service.toVO(pub(1L, 10L, "PUBLISHED", "AGENT", "office"));

        assertThat(vo.getAuthorName()).isNull();
        assertThat(vo.getCategoryName()).isNull();
        assertThat(vo.getName()).as("基础字段仍在").isEqualTo("商品1");
    }

    // ==================== list ====================

    @Test
    @DisplayName("list — status 恒定硬编码 PUBLISHED(即使 query 里没有 status 字段),并算好分页参数")
    void list_hardcodesPublishedStatus() {
        when(publicationMapper.selectList(any())).thenReturn(List.of());
        when(publicationMapper.countList(any())).thenReturn(0);

        PublicationListQuery query = new PublicationListQuery();
        query.setPage(3);
        query.setPageSize(20);
        Map<String, Object> data = service.list(query);

        var captor = ArgumentCaptor.forClass(Map.class);
        verify(publicationMapper).selectList(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("status")).isEqualTo("PUBLISHED");
        assertThat(params.get("offset")).isEqualTo(40);
        assertThat(params.get("limit")).isEqualTo(20);
        assertThat(params).doesNotContainKey("authorId");
        assertThat(data.get("page")).isEqualTo(3);
        assertThat(data.get("total")).isEqualTo(0);
    }

    @Test
    @DisplayName("list — ?page=/?pageSize= 空串或负数被夹紧,不把非法 offset 传给 MySQL LIMIT")
    void list_clampsBadPaging() {
        when(publicationMapper.selectList(any())).thenReturn(List.of());
        when(publicationMapper.countList(any())).thenReturn(0);

        PublicationListQuery query = new PublicationListQuery();
        query.setPage(null);
        query.setPageSize(-5);
        service.list(query);

        var captor = ArgumentCaptor.forClass(Map.class);
        verify(publicationMapper).selectList(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("offset")).isEqualTo(0);
        assertThat(params.get("limit")).isEqualTo(12);
    }

    @Test
    @DisplayName("list — 3 行商品只发 1 次 selectByIds + 每个 productType 1 次 selectByCodes,不做 N+1 单查")
    void list_batchesAuthorAndCategory() {
        when(publicationMapper.selectList(any())).thenReturn(List.of(
                pub(1L, 10L, "PUBLISHED", "AGENT", "office"),
                pub(2L, 10L, "PUBLISHED", "AGENT", "office"),
                pub(3L, 11L, "PUBLISHED", "MODULE", "office")));
        when(publicationMapper.countList(any())).thenReturn(3);
        when(authorMapper.selectByIds(anyCollection()))
                .thenReturn(List.of(author(10L, "企业A"), author(11L, "企业B")));
        when(categoryMapper.selectByCodes(eq("AGENT"), anyCollection()))
                .thenReturn(List.of(cat("AGENT", "office", "办公")));
        when(categoryMapper.selectByCodes(eq("MODULE"), anyCollection()))
                .thenReturn(List.of(cat("MODULE", "office", "功能")));

        Map<String, Object> data = service.list(new PublicationListQuery());
        @SuppressWarnings("unchecked")
        List<PublicationVO> rows = (List<PublicationVO>) data.get("rows");

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getAuthorName()).isEqualTo("企业A");
        assertThat(rows.get(1).getCategoryName()).isEqualTo("办公");
        assertThat(rows.get(2).getCategoryName())
                .as("AGENT/MODULE 同名 code 必须各查各的,不能串").isEqualTo("功能");

        verify(authorMapper, times(1)).selectByIds(anyCollection());
        verify(categoryMapper, times(1)).selectByCodes(eq("AGENT"), anyCollection());
        verify(categoryMapper, times(1)).selectByCodes(eq("MODULE"), anyCollection());
        // 逐行单查一旦被引入,列表页立刻退化成 2N+1
        verify(authorMapper, never()).selectById(any());
        verify(categoryMapper, never()).selectByCode(anyString(), anyString());
    }

    @Test
    @DisplayName("list — 空结果集不发 IN (),批量 mapper 一次都不调")
    void list_emptyRows_neverEmitsInClause() {
        when(publicationMapper.selectList(any())).thenReturn(List.of());
        when(publicationMapper.countList(any())).thenReturn(0);

        Map<String, Object> data = service.list(new PublicationListQuery());

        assertThat((List<?>) data.get("rows")).isEmpty();
        verify(authorMapper, never()).selectByIds(anyCollection());
        verify(categoryMapper, never()).selectByCodes(anyString(), anyCollection());
    }

    // ==================== listByAuthor(VOs) ====================

    @Test
    @DisplayName("listByAuthor — 不过滤 status,返回原始行")
    void listByAuthor_returnsAllStatuses() {
        when(publicationMapper.selectByAuthorId(10L)).thenReturn(List.of(
                pub(1L, 10L, "DRAFT", "AGENT", "office"),
                pub(2L, 10L, "PUBLISHED", "AGENT", "office")));

        List<MpPublication> rows = service.listByAuthor(10L);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(MpPublication::getStatus).containsExactly("DRAFT", "PUBLISHED");
    }

    @Test
    @DisplayName("listByAuthorVOs — 批量装配:selectByIds 1 次 + 每 productType selectByCodes 1 次")
    void listByAuthorVOs_batchesAuthorAndCategory() {
        when(publicationMapper.selectByAuthorId(10L)).thenReturn(List.of(
                pub(1L, 10L, "DRAFT", "AGENT", "office"),
                pub(2L, 10L, "PUBLISHED", "AGENT", "office"),
                pub(3L, 10L, "DEPRECATED", "AGENT", "office")));
        when(authorMapper.selectByIds(anyCollection())).thenReturn(List.of(author(10L, "企业A")));
        when(categoryMapper.selectByCodes(eq("AGENT"), anyCollection()))
                .thenReturn(List.of(cat("AGENT", "office", "办公")));

        List<PublicationVO> rows = service.listByAuthorVOs(10L);

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(PublicationVO::getStatus)
                .as("作者侧不过滤状态").containsExactly("DRAFT", "PUBLISHED", "DEPRECATED");
        assertThat(rows).allSatisfy(vo -> {
            assertThat(vo.getAuthorName()).isEqualTo("企业A");
            assertThat(vo.getCategoryName()).isEqualTo("办公");
        });
        verify(authorMapper, times(1)).selectByIds(anyCollection());
        verify(categoryMapper, times(1)).selectByCodes(eq("AGENT"), anyCollection());
        verify(authorMapper, never()).selectById(any());
        verify(categoryMapper, never()).selectByCode(anyString(), anyString());
    }

    @Test
    @DisplayName("listByAuthorVOs — 作者还没有任何商品 → 空列表,且不触发任何批量查询")
    void listByAuthorVOs_empty_returnsEmptyList() {
        when(publicationMapper.selectByAuthorId(10L)).thenReturn(List.of());

        assertThat(service.listByAuthorVOs(10L)).isEmpty();
        verify(authorMapper, never()).selectByIds(anyCollection());
        verify(categoryMapper, never()).selectByCodes(anyString(), anyCollection());
    }

    @Test
    @DisplayName("listByAuthorVOs — 与 list 装配出完全一样的冗余字段(两条路径不能漂移)")
    void listByAuthorVOs_matchesListEnrichment() {
        MpPublication row = pub(1L, 10L, "PUBLISHED", "AGENT", "office");
        when(publicationMapper.selectList(any())).thenReturn(List.of(row));
        when(publicationMapper.countList(any())).thenReturn(1);
        when(publicationMapper.selectByAuthorId(10L)).thenReturn(List.of(row));
        when(authorMapper.selectById(10L)).thenReturn(author(10L, "企业A"));
        when(authorMapper.selectByIds(anyCollection())).thenReturn(List.of(author(10L, "企业A")));
        when(categoryMapper.selectByCode(eq("AGENT"), eq("office"))).thenReturn(cat("AGENT", "office", "办公"));
        when(categoryMapper.selectByCodes(eq("AGENT"), anyCollection()))
                .thenReturn(List.of(cat("AGENT", "office", "办公")));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = service.list(new PublicationListQuery());
        List<PublicationVO> fromList = (List<PublicationVO>) data.get("rows");
        List<PublicationVO> fromAuthor = service.listByAuthorVOs(10L);

        Map<String, Object> a = new HashMap<>();
        a.put("name", fromList.get(0).getName());
        a.put("authorName", fromList.get(0).getAuthorName());
        a.put("authorAvatar", fromList.get(0).getAuthorAvatar());
        a.put("categoryName", fromList.get(0).getCategoryName());
        a.put("viewCount", fromList.get(0).getViewCount());
        a.put("screenshots", fromList.get(0).getScreenshots());
        Map<String, Object> b = new HashMap<>();
        b.put("name", fromAuthor.get(0).getName());
        b.put("authorName", fromAuthor.get(0).getAuthorName());
        b.put("authorAvatar", fromAuthor.get(0).getAuthorAvatar());
        b.put("categoryName", fromAuthor.get(0).getCategoryName());
        b.put("viewCount", fromAuthor.get(0).getViewCount());
        b.put("screenshots", fromAuthor.get(0).getScreenshots());

        assertThat(a).isEqualTo(b);
    }
}
