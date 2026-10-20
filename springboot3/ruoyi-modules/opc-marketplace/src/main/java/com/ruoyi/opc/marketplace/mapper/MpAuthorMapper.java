package com.ruoyi.opc.marketplace.mapper;

import com.ruoyi.opc.marketplace.domain.MpAuthor;
import org.apache.ibatis.annotations.Mapper;

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
}