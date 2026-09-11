package com.ruoyi.opc.crm.service.impl;

import com.ruoyi.opc.crm.domain.CrmContact;
import com.ruoyi.opc.crm.mapper.CrmContactMapper;
import com.ruoyi.opc.crm.service.ContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    private final CrmContactMapper mapper;

    @Override
    @Transactional
    public CrmContact create(CrmContact contact, Long operatorId) {
        if (contact.getCustomerId() == null) {
            throw new IllegalArgumentException("customerId required");
        }
        if (contact.getName() == null || contact.getName().isBlank()) {
            throw new IllegalArgumentException("Contact name cannot be empty (联系人姓名不能为空)");
        }
        contact.setCreateBy(String.valueOf(operatorId));
        contact.setUpdateBy(String.valueOf(operatorId));
        if (contact.getIsPrimary() == null) contact.setIsPrimary(0);
        if (contact.getDeleted() == null) contact.setDeleted(0);
        mapper.insert(contact);
        log.info("Created contact id={} customerId={} name={}",
            contact.getId(), contact.getCustomerId(), contact.getName());
        return contact;
    }

    @Override
    @Transactional
    public CrmContact update(Long id, CrmContact update, Long operatorId) {
        CrmContact existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Contact not found: " + id);
        }
        if (update.getName() != null) existing.setName(update.getName());
        if (update.getPhone() != null) existing.setPhone(update.getPhone());
        if (update.getEmail() != null) existing.setEmail(update.getEmail());
        if (update.getPosition() != null) existing.setPosition(update.getPosition());
        if (update.getRemark() != null) existing.setRemark(update.getRemark());
        if (update.getIsPrimary() != null) existing.setIsPrimary(update.getIsPrimary());
        existing.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        CrmContact existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Contact not found: " + id);
        }
        existing.setDeleted(1);
        existing.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(existing);
    }

    @Override
    public CrmContact getById(Long id) {
        CrmContact c = mapper.selectById(id);
        if (c == null) {
            throw new IllegalArgumentException("Contact not found: " + id);
        }
        return c;
    }

    @Override
    public List<CrmContact> listByCustomer(Long customerId) {
        return mapper.selectByCustomerId(customerId);
    }

    @Override
    @Transactional
    public void setPrimary(Long customerId, Long contactId) {
        // Step 1: Unset all other primary contacts for this customer.
        mapper.unsetPrimaryForCustomer(customerId);
        // Step 2: Set the requested contact as primary.
        mapper.setPrimary(contactId);
        log.info("Set primary contact id={} for customerId={}", contactId, customerId);
    }
}
