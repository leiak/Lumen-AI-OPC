package com.ruoyi.opc.content.mapper;

import com.ruoyi.opc.content.domain.OpcContentAdapt;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 平台适配关系 Mapper — opc_content_adapt
 */
public interface OpcContentAdaptMapper {

    int insert(OpcContentAdapt a);

    /** 按原文查所有适配记录 (1→N,同一原文可能适配多个平台) */
    List<OpcContentAdapt> selectBySource(@Param("sourceScriptId") Long sourceScriptId,
                                          @Param("companyId") Long companyId);

    /** 按适配后脚本查原文 (1→1,adapted_script_id UNIQUE) */
    OpcContentAdapt selectByAdapted(@Param("adaptedScriptId") Long adaptedScriptId,
                                     @Param("companyId") Long companyId);

    /** 公司下全部适配记录 */
    List<OpcContentAdapt> selectListByCompany(@Param("companyId") Long companyId);
}