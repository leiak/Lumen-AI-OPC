package com.ruoyi.opc.content.service;

import com.ruoyi.opc.content.domain.OpcContentPlatformAccount;

import java.util.List;

/**
 * 平台账号 Service 接口 — 抖音 OAuth state 生成/解析、token 持久化与刷新。
 *
 * @author OAC
 */
public interface IOpcContentPlatformAccountService {

    /**
     * 生成抖音 OAuth 授权 URL(state 携带 companyId + 随机 nonce 防 CSRF)。
     *
     * @return 完整 authorize URL,前端 window.location 跳转
     */
    String buildAuthorizeUrl(Long companyId);

    /**
     * 抖音 OAuth 回调处理: 解析 state → 调 PlatformClient.exchangeCode → 落 platform_account 表。
     *
     * @return 新绑定的账号 ID
     */
    Long handleCallback(String code, String state);

    /**
     * 公司下全部账号(ACTIVE/EXPIRED 状态都返回,REVOKED 已软删不返)
     */
    List<OpcContentPlatformAccount> listByCompany(Long companyId);

    /**
     * 软删账号(status → REVOKED)
     */
    int delete(Long id, Long companyId);

    /**
     * 刷新 access_token(过期前调用)。失败抛 ServiceException。
     */
    void refreshToken(Long id, Long companyId);

    /**
     * 账号详情
     */
    OpcContentPlatformAccount detail(Long id, Long companyId);
}