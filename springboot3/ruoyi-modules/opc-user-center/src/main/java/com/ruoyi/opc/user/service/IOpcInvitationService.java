package com.ruoyi.opc.user.service;

import com.ruoyi.opc.user.domain.OpcInvitation;

import java.util.List;
import java.util.Map;

public interface IOpcInvitationService {

    /**
     * 当前用户生成邀请码（同一用户最多 50 个未用邀请码）
     */
    OpcInvitation generate(Long userId);

    /**
     * 查询当前用户的所有邀请码
     */
    List<OpcInvitation> listByInviter(Long userId);

    /**
     * 公开：根据 code 查询邀请信息（仅返回 inviter 公开字段）
     */
    Map<String, Object> getPublicByCode(String code);

    /**
     * 接受邀请（被邀请人调用）
     * - 校验邀请码合法
     * - 写 opc_company_member（成为邀请人公司的 STAFF）
     * - 给邀请人发 50 元代金券（opc_transaction）
     * - 更新 opc_invitation.invitee_id, used_count
     */
    Map<String, Object> accept(String code, Long inviteeId, String inviteeMobile);

}
