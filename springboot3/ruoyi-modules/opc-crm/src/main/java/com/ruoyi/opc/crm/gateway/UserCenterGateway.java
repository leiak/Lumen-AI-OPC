package com.ruoyi.opc.crm.gateway;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "opc-user-center", url = "${opc.crm.user-center.base-url}")
public interface UserCenterGateway {

    @GetMapping("/opc/user-center/user/{id}")
    Map<String, Object> getUser(@PathVariable Long id);
}