package com.ruoyi.opc.marketplace.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpAuthor;
import com.ruoyi.opc.marketplace.dto.AuthorApplyRequest;
import com.ruoyi.opc.marketplace.dto.AuthorListQuery;
import com.ruoyi.opc.marketplace.dto.AuthorVO;
import com.ruoyi.opc.marketplace.mapper.MpAuthorMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.service.AuthorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 作者档案 Service 实现 — P1
 *
 * <p><b>NOT NULL 列显式补默认（W50 教训）</b>：
 * {@code mp_author} 的 {@code level/total_agents/total_subscribers/total_revenue/status} 都是 NOT NULL，
 * mapper XML INSERT 显式列出这些列 → MySQL 不应用 DDL DEFAULT，必须由 service 层设置。
 * 同 {@code opc-crm.order.score} 的修复（commit cfcd9ed）。
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorServiceImpl implements AuthorService {

    private final MpAuthorMapper authorMapper;
    private final MpPublicationMapper publicationMapper;

    @Override
    public MpAuthor apply(AuthorApplyRequest req, Long operatorId) {
        // 前置查重 — uk_author_company 不含 deleted 列,任何状态都已入驻
        MpAuthor existing = authorMapper.selectByCompanyId(req.getAuthorCompanyId());
        if (existing != null) {
            throw new OpcException(409, "该企业已是作者，不可重复申请", "MARKETPLACE");
        }
        MpAuthor a = new MpAuthor();
        a.setAuthorCompanyId(req.getAuthorCompanyId());
        a.setAuthorName(req.getAuthorName());
        a.setContactEmail(req.getContactEmail());
        a.setContactPhone(req.getContactPhone());
        a.setIntro(req.getIntro());
        a.setAvatarUrl(req.getAvatarUrl());
        // 显式补 NOT NULL 默认(W50 教训:INSERT 显式列名时不应用 DDL DEFAULT)
        a.setLevel("NORMAL");
        a.setTotalAgents(0);
        a.setTotalSubscribers(0);
        a.setTotalRevenue(BigDecimal.ZERO);
        a.setStatus("ACTIVE");
        a.setCreateBy(String.valueOf(operatorId));
        a.setUpdateBy(String.valueOf(operatorId));
        authorMapper.insert(a);
        log.info("[Author] apply success companyId={} authorId={}", req.getAuthorCompanyId(), a.getId());
        return a;
    }

    @Override
    public MpAuthor getByCompanyId(Long companyId) {
        // null 表示未入驻,语义上不应抛 404 —— /me 端点靠此区分
        return authorMapper.selectByCompanyId(companyId);
    }

    @Override
    public MpAuthor getById(Long id) {
        MpAuthor a = authorMapper.selectById(id);
        if (a == null) {
            throw new OpcException(404, "作者不存在: " + id, "MARKETPLACE");
        }
        return a;
    }

    @Override
    public AuthorVO toVO(MpAuthor a) {
        if (a == null) {
            return null;
        }
        AuthorVO vo = new AuthorVO();
        vo.setId(a.getId());
        vo.setAuthorCompanyId(a.getAuthorCompanyId());
        vo.setAuthorName(a.getAuthorName());
        vo.setContactEmail(a.getContactEmail());
        vo.setIntro(a.getIntro());
        vo.setAvatarUrl(a.getAvatarUrl());
        vo.setLevel(a.getLevel());
        vo.setTotalAgents(a.getTotalAgents());
        vo.setTotalSubscribers(a.getTotalSubscribers());
        vo.setTotalRevenue(a.getTotalRevenue());
        vo.setStatus(a.getStatus());
        vo.setCreateTime(a.getCreateTime());
        return vo;
    }

    @Override
    public AuthorVO toPublicVO(MpAuthor a) {
        AuthorVO vo = toVO(a);
        if (vo == null) {
            return null;
        }
        // 公开端点(网关白名单 /opc/marketplace/author/{id:[0-9]+})的脱敏闸门。
        // ⚠️ AuthorVO 当前没有 contactPhone 字段(domain 有),该字段不会进响应;
        //    若日后给 VO 补 contactPhone,必须在此补一行 setContactPhone(null)。
        vo.setContactEmail(null);
        vo.setTotalRevenue(null);
        log.debug("[Author] toPublicVO redacted authorId={} (contactEmail/totalRevenue cleared)", a.getId());
        return vo;
    }

    @Override
    public Map<String, Object> list(AuthorListQuery query) {
        // P1: 占位实现 —— 返回空 rows/total,保留 query.page/pageSize 给前端做"无数据"提示
        // P2: 切换到 MpAuthorMapper.selectList(params) + countList(params),与 Publication 一致
        List<MpAuthor> all = List.of();
        Map<String, Object> data = new HashMap<>();
        data.put("rows", all);
        data.put("total", 0);
        data.put("page", query.getPage());
        data.put("pageSize", query.getPageSize());
        return data;
    }

    @Override
    public Map<String, Object> stats(Long id) {
        MpAuthor a = getById(id);
        Map<String, Object> data = new HashMap<>();
        // 嵌套的 author 同样走脱敏 —— stats 的 authorId 由调用方任意指定,
        // 用 toVO 会让任意登录用户读到别家企业的邮箱与营收
        data.put("author", toPublicVO(a));
        // 只统计 PUBLISHED 的商品(用 status='PUBLISHED' 过滤,与 selectPublishedById 一致语义)
        // params 同时传 authorId + status,XML 已支持 authorId 过滤;status 在 XML 里有 <if> 过滤
        Map<String, Object> params = new HashMap<>();
        params.put("authorId", id);
        params.put("status", "PUBLISHED");
        int publicationCount = publicationMapper.countList(params);
        data.put("publicationCount", publicationCount);
        return data;
    }
}
