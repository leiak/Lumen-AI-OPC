package com.ruoyi.opc.crm.service.impl;

import com.ruoyi.opc.crm.domain.CrmCustomer;
import com.ruoyi.opc.crm.mapper.CrmCustomerMapper;
import com.ruoyi.opc.crm.service.CrmOwnership;
import com.ruoyi.opc.crm.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CrmCustomerMapper mapper;

    @Override
    @Transactional
    public CrmCustomer create(CrmCustomer customer, Long ownerId) {
        if (customer.getName() == null || customer.getName().isBlank()) {
            throw new IllegalArgumentException("Customer name cannot be empty (客户名称不能为空)");
        }
        customer.setOwnerId(ownerId);
        customer.setCreateBy(String.valueOf(ownerId));
        customer.setUpdateBy(String.valueOf(ownerId));
        if (customer.getSource() == null) customer.setSource("OTHER");
        if (customer.getLevel() == null) customer.setLevel("C");
        if (customer.getDeleted() == null) customer.setDeleted(0);
        mapper.insert(customer);
        log.info("Created customer id={} name={}", customer.getId(), customer.getName());
        return customer;
    }

    @Override
    @Transactional
    public CrmCustomer update(Long id, CrmCustomer update, Long operatorId) {
        // 取数就带 owner —— 「id 对上了但不是你的」在这里直接查不到,
        // 不给探测方留存在性预言机(不存在 和 不是你的, 同一句话)。
        CrmCustomer existing = mapper.selectByIdAndOwner(id, operatorId);
        if (existing == null) {
            throw CrmOwnership.denied("客户");
        }
        if (update.getName() != null) existing.setName(update.getName());
        if (update.getSource() != null) existing.setSource(update.getSource());
        if (update.getTags() != null) existing.setTags(update.getTags());
        if (update.getLevel() != null) existing.setLevel(update.getLevel());
        if (update.getPhone() != null) existing.setPhone(update.getPhone());
        if (update.getEmail() != null) existing.setEmail(update.getEmail());
        if (update.getAddress() != null) existing.setAddress(update.getAddress());
        if (update.getRemark() != null) existing.setRemark(update.getRemark());
        existing.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        CrmCustomer existing = mapper.selectByIdAndOwner(id, operatorId);
        if (existing == null) {
            throw CrmOwnership.denied("客户");
        }
        existing.setDeleted(1);
        existing.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(existing);
    }

    @Override
    public CrmCustomer getById(Long id, Long ownerId) {
        CrmCustomer c = mapper.selectByIdAndOwner(id, ownerId);
        if (c == null) {
            throw CrmOwnership.denied("客户");
        }
        return c;
    }

    @Override
    public List<CrmCustomer> list(Long ownerId, String level, String source, String tag, String keyword) {
        return mapper.selectList(ownerId, level, source, tag, keyword);
    }

    @Override
    public int countByOwner(Long ownerId) {
        return mapper.countByOwner(ownerId);
    }
}
