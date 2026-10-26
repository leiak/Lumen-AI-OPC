package com.ruoyi.opc.crm.service.impl;

import com.ruoyi.opc.crm.domain.CrmContact;
import com.ruoyi.opc.crm.mapper.CrmContactMapper;
import com.ruoyi.opc.crm.mapper.CrmCustomerMapper;
import com.ruoyi.opc.crm.service.ContactService;
import com.ruoyi.opc.crm.service.CrmOwnership;
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
    private final CrmCustomerMapper customerMapper;

    @Override
    @Transactional
    public CrmContact create(CrmContact contact, Long operatorId) {
        if (contact.getCustomerId() == null) {
            throw new IllegalArgumentException("customerId required");
        }
        if (contact.getName() == null || contact.getName().isBlank()) {
            throw new IllegalArgumentException("Contact name cannot be empty (联系人姓名不能为空)");
        }
        // 联系人挂在别人客户下 = 越权写入
        CrmOwnership.requireCustomer(customerMapper, contact.getCustomerId(), operatorId);
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
        CrmContact existing = mapper.selectByIdAndOwner(id, operatorId);
        if (existing == null) {
            throw CrmOwnership.denied("联系人");
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
        CrmContact existing = mapper.selectByIdAndOwner(id, operatorId);
        if (existing == null) {
            throw CrmOwnership.denied("联系人");
        }
        existing.setDeleted(1);
        existing.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(existing);
    }

    @Override
    public CrmContact getById(Long id, Long ownerId) {
        CrmContact c = mapper.selectByIdAndOwner(id, ownerId);
        if (c == null) {
            throw CrmOwnership.denied("联系人");
        }
        return c;
    }

    @Override
    public List<CrmContact> listByCustomer(Long customerId, Long ownerId) {
        return mapper.selectByCustomerIdAndOwner(customerId, ownerId);
    }

    @Override
    @Transactional
    public void setPrimary(Long contactId, Long operatorId) {
        // 先按归属把联系人取出来 —— 不是你的联系人直接 403。
        // 客户 id 用**取出来的这个联系人**身上的, 不用客户端给的那个:
        // 否则「拿我的 contactId + 别人的 customerId」就能把人家客户的主联系人清掉。
        CrmContact contact = mapper.selectByIdAndOwner(contactId, operatorId);
        if (contact == null) {
            throw CrmOwnership.denied("联系人");
        }
        Long customerId = contact.getCustomerId();
        // Step 1: Unset all other primary contacts for this customer.
        mapper.unsetPrimaryForCustomer(customerId);
        // Step 2: Set the requested contact as primary.
        mapper.setPrimary(contactId);
        log.info("Set primary contact id={} for customerId={}", contactId, customerId);
    }
}
