package com.ruoyi.opc.notification.service;

import com.ruoyi.opc.notification.domain.NotificationInbox;

import java.util.List;

/**
 * 站内信服务
 *
 * @author OAC
 */
public interface InboxService {

    /**
     * 创建一条站内信并通过 WebSocket 推送给用户
     */
    void push(Long userId, String type, String title, String body, String link);

    /**
     * 分页查询某用户的站内信（按 created_at DESC）
     */
    List<NotificationInbox> listInbox(Long userId, int page, int pageSize);

    /**
     * 标记单条站内信已读（必须属于 userId，否则抛 SecurityException）
     */
    void markRead(Long userId, Long inboxId);

    /**
     * 批量标记某用户全部未读站内信为已读
     */
    int markAllRead(Long userId);

    /**
     * 查询某用户未读站内信数量
     */
    long unreadCount(Long userId);
}
