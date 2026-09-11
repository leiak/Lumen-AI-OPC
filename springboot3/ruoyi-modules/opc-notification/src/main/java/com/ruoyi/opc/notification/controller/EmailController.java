package com.ruoyi.opc.notification.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.notification.dto.EmailSendRequest;
import com.ruoyi.opc.notification.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邮件发送 Controller
 */
@RestController
@RequestMapping("/opc/notification/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    @PostMapping("/send")
    public R<Void> send(@Valid @RequestBody EmailSendRequest req) {
        emailService.send(req.getTo(), req.getSubject(), req.getBody());
        return R.ok();
    }
}
