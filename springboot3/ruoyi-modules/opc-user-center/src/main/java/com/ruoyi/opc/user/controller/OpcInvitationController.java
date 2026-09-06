package com.ruoyi.opc.user.controller;

import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.user.domain.OpcInvitation;
import com.ruoyi.opc.user.service.IOpcInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OPC 邀请码 API
 *
 * <p>端点：
 * <ul>
 *   <li>POST /opc/user/invitations/generate — 当前用户生成邀请码（需登录）</li>
 *   <li>GET /opc/user/invitations — 当前用户邀请码列表（需登录）</li>
 *   <li>GET /opc/user/invitations/{code} — 公开：根据 code 查邀请人公开信息（匿名）</li>
 *   <li>POST /opc/user/invitations/accept — 接受邀请（需登录）</li>
 * </ul>
 *
 * @author OAC
 */
@Tag(name = "OPC 邀请码")
@RestController
@RequestMapping("/opc/user/invitations")
@RequiredArgsConstructor
public class OpcInvitationController extends BaseController {

    private final IOpcInvitationService invitationService;

    @Operation(summary = "生成我的邀请码")
    @PostMapping("/generate")
    public AjaxResult generate() {
        Long userId = SecurityUtils.getUserId();
        return success(invitationService.generate(userId));
    }

    @Operation(summary = "我的邀请码列表")
    @GetMapping
    public AjaxResult myInvitations() {
        Long userId = SecurityUtils.getUserId();
        return success(invitationService.listByInviter(userId));
    }

    @Operation(summary = "查询邀请码公开信息（需在 gateway 白名单配置 /opc/user/invitations/* GET）")
    @GetMapping("/{code}")
    public AjaxResult getPublic(@PathVariable String code) {
        return success(invitationService.getPublicByCode(code));
    }

    @Operation(summary = "接受邀请（被邀请人）")
    @PostMapping("/accept")
    public AjaxResult accept(@RequestBody Map<String, String> body) {
        Long userId = SecurityUtils.getUserId();
        String code = body.get("code");
        String mobile = body.get("mobile");
        return success(invitationService.accept(code, userId, mobile));
    }

}
