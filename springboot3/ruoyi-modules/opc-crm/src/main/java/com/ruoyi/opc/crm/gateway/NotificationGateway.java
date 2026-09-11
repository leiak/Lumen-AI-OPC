package com.ruoyi.opc.crm.gateway;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "opc-notification", url = "${opc.crm.notification.base-url}")
public interface NotificationGateway {

    @PostMapping("/opc/notification/inbox/push")
    Map<String, Object> pushInbox(@RequestParam Long userId,
                                  @RequestParam String title,
                                  @RequestParam String content);
}