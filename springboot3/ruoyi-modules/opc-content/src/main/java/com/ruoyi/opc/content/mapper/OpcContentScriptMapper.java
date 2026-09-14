package com.ruoyi.opc.content.mapper;

import com.ruoyi.opc.content.domain.OpcContentScript;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 内容脚本主表 Mapper — opc_content_script
 * 所有 selectById / softDeleteById / updateById 都加 company_id 过滤,防跨租户。
 */
public interface OpcContentScriptMapper {

    /** 雪花 ID 应用层生成,不使用 useGeneratedKeys */
    int insert(OpcContentScript s);

    int updateById(OpcContentScript s);

    /** 软删: SET status='DELETED' */
    int softDeleteById(@Param("id") Long id, @Param("companyId") Long companyId);

    OpcContentScript selectById(@Param("id") Long id, @Param("companyId") Long companyId);

    /** 列表: companyId 必传, type/status 可空, 分页 */
    List<OpcContentScript> selectList(@Param("companyId") Long companyId,
                                       @Param("type") String type,
                                       @Param("status") String status,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    int countList(@Param("companyId") Long companyId,
                  @Param("type") String type,
                  @Param("status") String status);

    /** 今日生成数 (Dashboard) */
    int countTodayByCompany(@Param("companyId") Long companyId);

    /** 按状态统计 (Dashboard / 校验) */
    int countByStatus(@Param("companyId") Long companyId, @Param("status") String status);

    /** 近 7 天失败数 (Dashboard 失败率) */
    int countFailedLast7Days(@Param("companyId") Long companyId);

    /** 最近 N 条 (Dashboard / 默认 limit=5) */
    List<OpcContentScript> selectRecent(@Param("companyId") Long companyId, @Param("limit") int limit);
}