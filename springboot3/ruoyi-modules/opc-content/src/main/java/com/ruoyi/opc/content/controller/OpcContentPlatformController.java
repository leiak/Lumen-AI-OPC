package com.ruoyi.opc.content.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.content.domain.OpcContentPlatformAccount;
import com.ruoyi.opc.content.service.IOpcContentPlatformAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * OPC 内容平台账号 Controller — 抖音 OAuth 绑定 + 列表 / 解绑 / 刷新 token。
 *
 * <p>W74 Task 5: 5 endpoints。
 * <ul>
 *   <li>GET   /oauth/douyin/authorize        生成 OAuth URL 并 302 重定向到抖音授权页</li>
 *   <li>GET   /oauth/callback                抖音回调(校验 state + 换 token + 落库)</li>
 *   <li>GET   /list                          公司下全部平台账号</li>
 *   <li>DELETE /{id}                          解绑(软删 status=REVOKED)</li>
 *   <li>POST  /{id}/refresh                   手动刷新 access_token</li>
 * </ul>
 */
@Tag(name = "OPC 内容平台账号")
@RestController
@RequestMapping("/opc/content/platform-account")
@RequiredArgsConstructor
@Slf4j
public class OpcContentPlatformController {

    /** 前端回调落地地址(本地 aiopc-frontend:8079,生产替换为公网域名) */
    private static final String FRONTEND_REDIRECT_BASE = "http://127.0.0.1:8079/opc/content/platform-account";

    private final IOpcContentPlatformAccountService accountService;

    /**
     * 生成抖音 OAuth 授权 URL 并 302 重定向(state 携带 companyId + nonce 防 CSRF)。
     */
    @Operation(summary = "生成抖音 OAuth URL 并 302 重定向")
    @GetMapping("/oauth/douyin/authorize")
    public void authorizeDouyin(@RequestParam Long companyId,
                                HttpServletResponse response) throws IOException {
        String url = accountService.buildAuthorizeUrl(companyId);
        log.info("重定向到抖音授权页 companyId={}", companyId);
        response.sendRedirect(url);
    }

    /**
     * 抖音 OAuth 回调: 解析 state → 换 token → 落库 → 重定向回前端(带 bound={id})。
     */
    @Operation(summary = "抖音 OAuth 回调(校验 state + 换 token + 落库 + 回跳前端)")
    @GetMapping("/oauth/callback")
    public void callback(@RequestParam String code,
                         @RequestParam String state,
                         HttpServletResponse response) throws IOException {
        Long accountId = accountService.handleCallback(code, state);
        log.info("OAuth 回调成功 → accountId={}, 重定向前端", accountId);
        response.sendRedirect(FRONTEND_REDIRECT_BASE + "?bound=" + accountId);
    }

    /**
     * 公司下全部平台账号(ACTIVE/EXPIRED 状态都返回,REVOKED 已软删不返)。
     */
    @Operation(summary = "公司下平台账号列表")
    @GetMapping("/list")
    public R<List<OpcContentPlatformAccount>> list(@RequestParam Long companyId) {
        return R.ok(accountService.listByCompany(companyId));
    }

    /**
     * 解绑(软删 status → REVOKED,跨租户校验)。
     */
    @Operation(summary = "解绑平台账号(软删)")
    @DeleteMapping("/{id}")
    public R<Integer> delete(@PathVariable Long id,
                             @RequestParam Long companyId) {
        return R.ok(accountService.delete(id, companyId));
    }

    /**
     * 手动刷新 access_token(过期前调用,失败抛 ServiceException)。
     */
    @Operation(summary = "刷新 access_token")
    @PostMapping("/{id}/refresh")
    public R<Void> refresh(@PathVariable Long id,
                           @RequestParam Long companyId) {
        accountService.refreshToken(id, companyId);
        return R.ok();
    }
}
