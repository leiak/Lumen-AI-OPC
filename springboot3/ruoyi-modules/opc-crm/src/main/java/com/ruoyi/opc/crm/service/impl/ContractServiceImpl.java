package com.ruoyi.opc.crm.service.impl;

import com.ruoyi.opc.crm.domain.CrmContract;
import com.ruoyi.opc.crm.enums.ContractStatus;
import com.ruoyi.opc.crm.mapper.CrmContractMapper;
import com.ruoyi.opc.crm.service.ContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private final CrmContractMapper mapper;

    @Override
    @Transactional
    public CrmContract create(CrmContract contract, Long operatorId) {
        if (contract.getContractNo() == null || contract.getContractNo().isBlank()) {
            throw new IllegalArgumentException("contract_no required (合同编号必填)");
        }
        if (mapper.countByContractNo(contract.getContractNo()) > 0) {
            throw new IllegalArgumentException(
                "contract_no already exists: " + contract.getContractNo() +
                " (合同编号已存在)");
        }
        if (contract.getStatus() == null) contract.setStatus(ContractStatus.DRAFT.name());
        contract.setCreateBy(String.valueOf(operatorId));
        contract.setUpdateBy(String.valueOf(operatorId));
        if (contract.getDeleted() == null) contract.setDeleted(0);
        mapper.insert(contract);
        log.info("Created contract id={} contractNo={} amount={}",
            contract.getId(), contract.getContractNo(), contract.getAmount());
        return contract;
    }

    @Override
    @Transactional
    public CrmContract update(Long id, CrmContract update, Long operatorId) {
        CrmContract existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Contract not found: " + id);
        }
        if (update.getTitle() != null) existing.setTitle(update.getTitle());
        if (update.getAmount() != null) existing.setAmount(update.getAmount());
        if (update.getExpireAt() != null) existing.setExpireAt(update.getExpireAt());
        if (update.getFileUrl() != null) existing.setFileUrl(update.getFileUrl());
        if (update.getRemark() != null) existing.setRemark(update.getRemark());
        existing.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public CrmContract activate(Long id, Long operatorId) {
        CrmContract c = mapper.selectById(id);
        if (c == null) {
            throw new IllegalArgumentException("Contract not found: " + id);
        }
        if (!ContractStatus.DRAFT.name().equals(c.getStatus())) {
            throw new IllegalStateException(
                "Only DRAFT can be activated (只有草稿合同可以激活). Current: " + c.getStatus());
        }
        c.setStatus(ContractStatus.ACTIVE.name());
        c.setSignedAt(LocalDate.now());
        c.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(c);
        return c;
    }

    @Override
    @Transactional
    public CrmContract expire(Long id, Long operatorId) {
        CrmContract c = mapper.selectById(id);
        if (c == null) {
            throw new IllegalArgumentException("Contract not found: " + id);
        }
        if (!ContractStatus.ACTIVE.name().equals(c.getStatus())) {
            throw new IllegalStateException(
                "Only ACTIVE can expire (只有生效合同可以过期). Current: " + c.getStatus());
        }
        c.setStatus(ContractStatus.EXPIRED.name());
        c.setExpireAt(LocalDate.now());
        c.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(c);
        return c;
    }

    @Override
    @Transactional
    public CrmContract terminate(Long id, String reason, Long operatorId) {
        CrmContract c = mapper.selectById(id);
        if (c == null) {
            throw new IllegalArgumentException("Contract not found: " + id);
        }
        if (ContractStatus.TERMINATED.name().equals(c.getStatus()) ||
            ContractStatus.EXPIRED.name().equals(c.getStatus())) {
            throw new IllegalStateException(
                "Contract already closed: " + c.getStatus() +
                " (合同已终止/过期，不能再次终止)");
        }
        c.setStatus(ContractStatus.TERMINATED.name());
        c.setRemark(reason);
        c.setExpireAt(LocalDate.now());
        c.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(c);
        return c;
    }

    @Override
    public CrmContract getById(Long id) {
        CrmContract c = mapper.selectById(id);
        if (c == null) {
            throw new IllegalArgumentException("Contract not found: " + id);
        }
        return c;
    }

    @Override
    public List<CrmContract> listByCustomer(Long customerId) {
        return mapper.selectByCustomerId(customerId);
    }
}