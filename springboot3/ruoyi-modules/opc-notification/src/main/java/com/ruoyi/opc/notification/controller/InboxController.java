package com.ruoyi.opc.notification.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.notification.dto.InboxResponse;
import com.ruoyi.opc.notification.service.InboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内信 Controller
 */
@RestController
@RequestMapping("/opc/notification/inbox")
@RequiredArgsConstructor
public class InboxController {

    private final InboxService inboxService;

    @GetMapping
    public R<InboxResponse> list(@RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = SecurityUtils.getUserId();
        var items = inboxService.listInbox(userId, page, pageSize);
        long unread = inboxService.unreadCount(userId);
        return R.ok(InboxResponse.of(items, unread, page, pageSize));
    }

    @PostMapping("/read/{id}")
    public R<Void> markRead(@PathVariable Long id) {
        Long userId = SecurityUtils.getUserId();
        inboxService.markRead(userId, id);
        return R.ok();
    }

    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(inboxService.unreadCount(SecurityUtils.getUserId()));
    }
}
