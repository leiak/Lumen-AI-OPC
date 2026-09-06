package com.ruoyi.opc.user.mapper;

import com.ruoyi.opc.user.domain.OpcInvitation;

import java.util.List;

public interface OpcInvitationMapper {

    OpcInvitation selectByCode(String code);

    OpcInvitation selectById(Long id);

    List<OpcInvitation> selectByInviter(Long inviterId);

    int countActiveByInviter(Long inviterId);

    int insert(OpcInvitation invitation);

    int incrementUsedCount(Long id);

    int bindInvitee(OpcInvitation invitation);

    int updateStatus(OpcInvitation invitation);

}
