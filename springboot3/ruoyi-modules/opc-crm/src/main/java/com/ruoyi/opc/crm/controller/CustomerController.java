package com.ruoyi.opc.crm.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.crm.domain.CrmCustomer;
import com.ruoyi.opc.crm.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户档案 Controller
 */
@RestController
@RequestMapping("/opc/crm/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public R<List<CrmCustomer>> list(@RequestParam(required = false) String level,
                                     @RequestParam(required = false) String source,
                                     @RequestParam(required = false) String tag,
                                     @RequestParam(required = false) String keyword) {
        Long ownerId = SecurityUtils.getUserId();
        return R.ok(customerService.list(ownerId, level, source, tag, keyword));
    }

    @GetMapping("/{id}")
    public R<CrmCustomer> getById(@PathVariable Long id) {
        return R.ok(customerService.getById(id));
    }

    @PostMapping
    public R<CrmCustomer> create(@RequestBody CrmCustomer customer) {
        return R.ok(customerService.create(customer, SecurityUtils.getUserId()));
    }

    @PutMapping("/{id}")
    public R<CrmCustomer> update(@PathVariable Long id, @RequestBody CrmCustomer customer) {
        return R.ok(customerService.update(id, customer, SecurityUtils.getUserId()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        customerService.delete(id, SecurityUtils.getUserId());
        return R.ok();
    }
}