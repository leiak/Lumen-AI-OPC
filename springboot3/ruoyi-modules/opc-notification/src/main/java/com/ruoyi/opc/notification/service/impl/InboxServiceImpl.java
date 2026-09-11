package com.ruoyi.opc.notification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.opc.notification.domain.NotificationInbox;
import com.ruoyi.opc.notification.mapper.NotificationInboxMapper;
import com.ruoyi.opc.notification.service.InboxService;
import com.ruoyi.opc.notification.ws.WsSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内信服务实现（含 WebSocket 推送）
 *
 * @author OAC
 */
@Slf4j
@Service
public class InboxServiceImpl implements InboxService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final NotificationInboxMapper mapper;
    private final WsSessionRegistry wsRegistry;
    private final ObjectMapper objectMapper;

    public InboxServiceImpl(NotificationInboxMapper mapper,
                            WsSessionRegistry wsRegistry,
                            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.wsRegistry = wsRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void push(Long userId, String type, String title, String body, String link) {
        NotificationInbox row = new NotificationInbox();
        row.setUserId(userId);
        row.setType(type);
        row.setTitle(title);
        row.setBody(body);
        row.setLink(link);
        row.setCreatedAt(LocalDateTime.now());
        mapper.insert(row);
        log.info("[inbox] pushed id={} user={} type={} title={}", row.getId(), userId, type, title);

        // WebSocket 推送为 best-effort：失败只告警，不影响站内信落库
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", row.getId());
            payload.put("type", type);
            payload.put("title", title);
            payload.put("body", body);
            payload.put("link", link == null ? "" : link);
            payload.put("createdAt", row.getCreatedAt().toString());
            String json = objectMapper.writeValueAsString(payload);
            wsRegistry.sendToUser(userId, json);
        } catch (JsonProcessingException e) {
            log.warn("[inbox] ws serialize failed for user={}: {}", userId, e.getMessage());
        } catch (Exception e) {
            log.warn("[inbox] ws push failed for user={}: {}", userId, e.getMessage());
        }
    }

    @Override
    public List<NotificationInbox> listInbox(Long userId, int page, int pageSize) {
        int safePage = page < 1 ? 1 : page;
        int safeSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        int offset = (safePage - 1) * safeSize;
        return mapper.selectInboxPage(userId, offset, safeSize);
    }

    @Override
    public void markRead(Long userId, Long inboxId) {
        NotificationInbox row = mapper.selectById(inboxId);
        if (row == null) {
            throw new IllegalArgumentException("Inbox not found: " + inboxId);
        }
        if (!row.getUserId().equals(userId)) {
            throw new SecurityException("Cannot mark another user's inbox");
        }
        row.setReadAt(LocalDateTime.now());
        mapper.updateById(row);
    }

    @Override
    public int markAllRead(Long userId) {
        return mapper.markAllRead(userId);
    }

    @Override
    public long unreadCount(Long userId) {
        Long count = mapper.selectUnreadCount(userId);
        return count == null ? 0L : count;
    }
}
