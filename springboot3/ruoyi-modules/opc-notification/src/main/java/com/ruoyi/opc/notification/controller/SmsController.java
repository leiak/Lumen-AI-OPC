package com.ruoyi.opc.notification.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.notification.dto.SmsSendRequest;
import com.ruoyi.opc.notification.service.SmsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短信发送 Controller
 */
@RestController
@RequestMapping("/opc/notification/sms")
@RequiredArgsConstructor
public class SmsController {

    private final SmsService smsService;

    @PostMapping("/send")
    public R<Void> send(@Valid @RequestBody SmsSendRequest req) {
        smsService.send(req.getPhone(), req.getTemplateCode(), req.getVars());
        return R.ok();
    }
}
