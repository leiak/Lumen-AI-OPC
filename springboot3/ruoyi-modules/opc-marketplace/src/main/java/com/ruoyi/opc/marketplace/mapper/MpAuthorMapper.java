package com.ruoyi.opc.marketplace.mapper;

import com.ruoyi.opc.marketplace.domain.MpAuthor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * mp_author Mapper
 *
 * @author OAC
 */
@Mapper
public interface MpAuthorMapper {
    int insert(MpAuthor a);

    MpAuthor selectById(Long id);

    /** 按入驻公司查作者（uk_author_company，天然唯一） */
    MpAuthor selectByCompanyId(Long authorCompanyId);

    int updateById(MpAuthor a);

    /** 发布商品后 total_agents + 1 */
    int incrementTotalAgents(Long id);

    /**
     * 批量按 id 查询作者,用于列表页一次性填充 {@code authorName}/{@code authorAvatar},避免 N+1.
     *
     * <p><b>调用方契约</b>: 传入空集合时<b>不要</b>调用本方法 —— MyBatis 会发出非法 SQL
     * {@code IN ()},需在 service 层短路. 参数为空时直接返回 {@code Collections.emptyList()}.
     *
     * @param ids 作者 id 集合,不可为空
     * @return 命中的作者列表(顺序与传入 id 无关)
     */
    List<MpAuthor> selectByIds(@Param("ids") Collection<Long> ids);
}