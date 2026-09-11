package com.ruoyi.opc.notification.dto;

import com.ruoyi.opc.notification.domain.NotificationInbox;
import lombok.Data;

import java.util.List;

/**
 * 站内信分页响应
 */
@Data
public class InboxResponse {
    private List<NotificationInbox> items;
    private long unreadCount;
    private int page;
    private int pageSize;

    public static InboxResponse of(List<NotificationInbox> items, long unread, int page, int pageSize) {
        InboxResponse r = new InboxResponse();
        r.items = items;
        r.unreadCount = unread;
        r.page = page;
        r.pageSize = pageSize;
        return r;
    }
}
