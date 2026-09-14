package com.ruoyi.opc.content.mapper;

import com.ruoyi.opc.content.domain.OpcContentPublish;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 发布记录 Mapper — opc_content_publish
 */
public interface OpcContentPublishMapper {

    int insert(OpcContentPublish p);

    int updateById(OpcContentPublish p);

    OpcContentPublish selectById(@Param("id") Long id, @Param("companyId") Long companyId);

    /** 某脚本的全部发布历史 */
    List<OpcContentPublish> selectByScript(@Param("scriptId") Long scriptId,
                                            @Param("companyId") Long companyId);

    /** 列表: companyId + status 可空, 分页 */
    List<OpcContentPublish> selectList(@Param("companyId") Long companyId,
                                        @Param("status") String status,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    int countList(@Param("companyId") Long companyId, @Param("status") String status);

    /** Dashboard 统计 — 按状态计数 */
    int countByStatus(@Param("companyId") Long companyId, @Param("status") String status);
}