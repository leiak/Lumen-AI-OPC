package com.ruoyi.opc.user.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.common.utils.OpcCodeGenerator;
import com.ruoyi.opc.user.domain.OpcInvitation;
import com.ruoyi.opc.user.domain.OpcUserProfile;
import com.ruoyi.opc.user.mapper.OpcInvitationMapper;
import com.ruoyi.opc.user.mapper.OpcUserProfileMapper;
import com.ruoyi.opc.user.service.IOpcInvitationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 邀请码 Service
 *
 * <p>约束：
 * <ul>
 *   <li>同一用户最多 50 个 ACTIVE 状态的邀请码</li>
 *   <li>邀请码有效期默认 90 天</li>
 *   <li>maxUses 默认 1（一次性使用）</li>
 *   <li>被邀请人接受后，邀请人获得 50 元代金券（写入 opc_transaction）</li>
 * </ul>
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcInvitationServiceImpl implements IOpcInvitationService {

    /** 单用户最大 ACTIVE 邀请码数 */
    private static final int MAX_ACTIVE_PER_USER = 50;

    /** 默认有效期（天） */
    private static final int DEFAULT_EXPIRE_DAYS = 90;

    /** 默认单码最大使用次数 */
    private static final int DEFAULT_MAX_USES = 1;

    /** 邀请人奖励（元） */
    private static final BigDecimal INVITER_REWARD = new BigDecimal("50.00");

    private final OpcInvitationMapper invitationMapper;
    private final OpcUserProfileMapper userProfileMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpcInvitation generate(Long userId) {
        if (userId == null) throw new OpcException("userId 不能为空");

        // 检查上限
        int activeCount = invitationMapper.countActiveByInviter(userId);
        if (activeCount >= MAX_ACTIVE_PER_USER) {
            throw new OpcException("最多持有 " + MAX_ACTIVE_PER_USER + " 个有效邀请码");
        }

        // 生成邀请码（最多重试 5 次以避免冲突）
        OpcInvitation invite = new OpcInvitation();
        for (int i = 0; i < 5; i++) {
            String code = OpcCodeGenerator.inviteCode();
            if (invitationMapper.selectByCode(code) == null) {
                invite.setInviteCode(code);
                break;
            }
            if (i == 4) throw new OpcException("生成邀请码失败，请重试");
        }

        invite.setInviterId(userId);
        invite.setMaxUses(DEFAULT_MAX_USES);
        invite.setUsedCount(0);
        invite.setExpireTime(new Date(System.currentTimeMillis() + DEFAULT_EXPIRE_DAYS * 86400_000L));
        invite.setStatus("ACTIVE");
        invite.setCreateBy(String.valueOf(userId));

        invitationMapper.insert(invite);
        log.info("[Invitation] userId={} generated code={}", userId, invite.getInviteCode());
        return invite;
    }

    @Override
    public List<OpcInvitation> listByInviter(Long userId) {
        return invitationMapper.selectByInviter(userId);
    }

    @Override
    public Map<String, Object> getPublicByCode(String code) {
        OpcInvitation invite = invitationMapper.selectByCode(code);
        if (invite == null) {
            throw new OpcException("邀请码不存在");
        }
        if (!"ACTIVE".equals(invite.getStatus())) {
            throw new OpcException("邀请码已失效");
        }
        if (invite.getExpireTime() != null && invite.getExpireTime().before(new Date())) {
            throw new OpcException("邀请码已过期");
        }
        if (invite.getUsedCount() >= invite.getMaxUses()) {
            throw new OpcException("邀请码已被使用");
        }

        // 查邀请人公开信息
        OpcUserProfile inviter = userProfileMapper.selectByUserId(invite.getInviterId());

        Map<String, Object> result = new HashMap<>();
        result.put("inviteCode", invite.getInviteCode());
        result.put("expireTime", invite.getExpireTime());
        result.put("inviter", inviter == null ? null : Map.of(
                "userId", inviter.getUserId(),
                "realName", inviter.getRealName() != null ? inviter.getRealName() : "OPC 用户",
                "avatarUrl", inviter.getAvatarUrl() != null ? inviter.getAvatarUrl() : "",
                "industry", inviter.getIndustry() != null ? inviter.getIndustry() : "",
                "city", inviter.getCity() != null ? inviter.getCity() : ""
        ));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> accept(String code, Long inviteeId, String inviteeMobile) {
        if (code == null || code.isEmpty()) throw new OpcException("邀请码不能为空");
        if (inviteeId == null) throw new OpcException("被邀请人 ID 不能为空");

        OpcInvitation invite = invitationMapper.selectByCode(code);
        if (invite == null) throw new OpcException("邀请码不存在");
        if (!"ACTIVE".equals(invite.getStatus())) throw new OpcException("邀请码已失效");
        if (invite.getExpireTime() != null && invite.getExpireTime().before(new Date())) {
            throw new OpcException("邀请码已过期");
        }
        if (invite.getUsedCount() >= invite.getMaxUses()) {
            throw new OpcException("邀请码已被使用");
        }
        if (invite.getInviterId().equals(inviteeId)) {
            throw new OpcException("不能接受自己的邀请");
        }

        // 1. 更新被邀请人画像的 inviter_id
        OpcUserProfile inviteeProfile = userProfileMapper.selectByUserId(inviteeId);
        if (inviteeProfile == null) {
            inviteeProfile = new OpcUserProfile();
            inviteeProfile.setUserId(inviteeId);
            inviteeProfile.setUserType("ENTREPRENEUR");
            inviteeProfile.setInvitationCode(OpcCodeGenerator.inviteCode());
            inviteeProfile.setInviterId(invite.getInviterId());
            inviteeProfile.setStatus("ACTIVE");
            inviteeProfile.setVerified(0);
            inviteeProfile.setCreateBy(String.valueOf(inviteeId));
            userProfileMapper.insert(inviteeProfile);
        } else if (inviteeProfile.getInviterId() == null) {
            inviteeProfile.setInviterId(invite.getInviterId());
            inviteeProfile.setUpdateBy(String.valueOf(inviteeId));
            userProfileMapper.update(inviteeProfile);
        }

        // 2. 找邀请人名下的一家默认公司，把被邀请人加为 STAFF
        Long companyId = findFirstCompanyByOwner(invite.getInviterId());
        if (companyId != null) {
            try {
                jdbcTemplate.update(
                    "INSERT IGNORE INTO opc_company_member (company_id, user_id, role, joined_at, status, create_by, create_time) " +
                    "VALUES (?, ?, 'STAFF', NOW(), 'ACTIVE', ?, NOW())",
                    companyId, inviteeId, String.valueOf(inviteeId)
                );
            } catch (Exception e) {
                log.warn("[Invitation] bind company_member failed: {}", e.getMessage());
            }
        }

        // 3. 给邀请人发 50 元代金券
        if (companyId != null) {
            try {
                String txCode = OpcCodeGenerator.txCode();
                jdbcTemplate.update(
                    "INSERT INTO opc_transaction (tx_code, wallet_id, company_id, tx_type, amount, balance_before, balance_after, biz_type, biz_id, description, create_by, create_time) " +
                    "SELECT ?, w.id, ?, 'RECHARGE', ?, IFNULL(w.balance,0), IFNULL(w.balance,0)+?, 'INVITE_REWARD', ?, ?, ?, NOW() " +
                    "FROM opc_wallet w WHERE w.company_id = ? AND w.user_id = ? LIMIT 1",
                    txCode, companyId, INVITER_REWARD, INVITER_REWARD, inviteeId,
                    "邀请奖励：" + inviteeProfile.getRealName(), String.valueOf(invite.getInviterId()),
                    companyId, invite.getInviterId()
                );
                jdbcTemplate.update(
                    "UPDATE opc_wallet SET balance = balance + ?, total_recharge = total_recharge + ?, update_by = ?, update_time = NOW() " +
                    "WHERE company_id = ? AND user_id = ?",
                    INVITER_REWARD, INVITER_REWARD, String.valueOf(invite.getInviterId()),
                    companyId, invite.getInviterId()
                );
            } catch (Exception e) {
                log.warn("[Invitation] grant reward failed: {}", e.getMessage());
            }
        }

        // 4. 更新邀请码
        invite.setInviteeId(inviteeId);
        invite.setInviteeMobile(inviteeMobile);
        invite.setUsedTime(new Date());
        invite.setUsedCount(invite.getUsedCount() + 1);
        invite.setStatus(invite.getUsedCount() >= invite.getMaxUses() ? "USED" : "ACTIVE");
        invite.setUpdateBy(String.valueOf(inviteeId));
        invitationMapper.bindInvitee(invite);

        log.info("[Invitation] code={} accepted by inviteeId={}, inviterId={}",
                code, inviteeId, invite.getInviterId());

        Map<String, Object> result = new HashMap<>();
        result.put("inviterId", invite.getInviterId());
        result.put("companyId", companyId);
        result.put("rewardAmount", INVITER_REWARD);
        return result;
    }

    private Long findFirstCompanyByOwner(Long ownerUserId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT id FROM opc_company_profile WHERE owner_user_id = ? AND status = 'NORMAL' ORDER BY id ASC LIMIT 1",
                Long.class, ownerUserId
            );
        } catch (Exception e) {
            return null;
        }
    }

}
