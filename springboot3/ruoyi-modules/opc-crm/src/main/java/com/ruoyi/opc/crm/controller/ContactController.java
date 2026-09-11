package com.ruoyi.opc.crm.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.crm.domain.CrmContact;
import com.ruoyi.opc.crm.service.ContactService;
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
 * 联系人 Controller
 */
@RestController
@RequestMapping("/opc/crm/contact")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    @GetMapping
    public R<List<CrmContact>> listByCustomer(@RequestParam Long customerId) {
        return R.ok(contactService.listByCustomer(customerId));
    }

    @GetMapping("/{id}")
    public R<CrmContact> getById(@PathVariable Long id) {
        return R.ok(contactService.getById(id));
    }

    @PostMapping
    public R<CrmContact> create(@RequestBody CrmContact contact) {
        return R.ok(contactService.create(contact, SecurityUtils.getUserId()));
    }

    @PutMapping("/{id}")
    public R<CrmContact> update(@PathVariable Long id, @RequestBody CrmContact contact) {
        return R.ok(contactService.update(id, contact, SecurityUtils.getUserId()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        contactService.delete(id, SecurityUtils.getUserId());
        return R.ok();
    }

    @PostMapping("/{id}/primary")
    public R<Void> setPrimary(@PathVariable Long id, @RequestParam Long customerId) {
        contactService.setPrimary(customerId, id);
        return R.ok();
    }
}