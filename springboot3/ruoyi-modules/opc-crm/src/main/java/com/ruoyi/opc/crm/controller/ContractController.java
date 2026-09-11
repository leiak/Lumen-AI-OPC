package com.ruoyi.opc.crm.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.crm.domain.CrmContract;
import com.ruoyi.opc.crm.service.ContractService;
import lombok.RequiredArgsConstructor;
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
 * 合同 Controller
 */
@RestController
@RequestMapping("/opc/crm/contract")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @GetMapping
    public R<List<CrmContract>> list(@RequestParam Long customerId) {
        return R.ok(contractService.listByCustomer(customerId));
    }

    @GetMapping("/{id}")
    public R<CrmContract> getById(@PathVariable Long id) {
        return R.ok(contractService.getById(id));
    }

    @PostMapping
    public R<CrmContract> create(@RequestBody CrmContract contract) {
        return R.ok(contractService.create(contract, SecurityUtils.getUserId()));
    }

    @PutMapping("/{id}")
    public R<CrmContract> update(@PathVariable Long id, @RequestBody CrmContract contract) {
        return R.ok(contractService.update(id, contract, SecurityUtils.getUserId()));
    }

    @PostMapping("/{id}/activate")
    public R<CrmContract> activate(@PathVariable Long id) {
        return R.ok(contractService.activate(id, SecurityUtils.getUserId()));
    }

    @PostMapping("/{id}/expire")
    public R<CrmContract> expire(@PathVariable Long id) {
        return R.ok(contractService.expire(id, SecurityUtils.getUserId()));
    }

    @PostMapping("/{id}/terminate")
    public R<CrmContract> terminate(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return R.ok(contractService.terminate(id, reason, SecurityUtils.getUserId()));
    }
}