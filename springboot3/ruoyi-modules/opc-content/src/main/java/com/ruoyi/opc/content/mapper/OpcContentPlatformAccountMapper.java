package com.ruoyi.opc.content.mapper;

import com.ruoyi.opc.content.domain.OpcContentPlatformAccount;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 平台账号 Mapper — opc_content_platform_account (DOUYIN OAuth token 持久化)
 */
public interface OpcContentPlatformAccountMapper {

    int insert(OpcContentPlatformAccount a);

    int updateById(OpcContentPlatformAccount a);

    /** 软删: status='REVOKED' */
    int softDeleteById(@Param("id") Long id, @Param("companyId") Long companyId);

    OpcContentPlatformAccount selectById(@Param("id") Long id, @Param("companyId") Long companyId);

    /** 按 openId + platform 查重(OAuth callback 用) */
    OpcContentPlatformAccount selectByOpenId(@Param("openId") String openId,
                                              @Param("platform") String platform,
                                              @Param("companyId") Long companyId);

    /** 公司下全部账号 */
    List<OpcContentPlatformAccount> selectListByCompany(@Param("companyId") Long companyId);

    /** 单刷 token(4 个字段 + updated_at) */
    int updateTokens(@Param("id") Long id,
                     @Param("companyId") Long companyId,
                     @Param("accessTokenEnc") String accessTokenEnc,
                     @Param("refreshTokenEnc") String refreshTokenEnc,
                     @Param("accessTokenExpiresAt") Long accessTokenExpiresAt,
                     @Param("refreshTokenExpiresAt") Long refreshTokenExpiresAt);
}