package com.ruoyi.opc.crm.service.impl;

import com.ruoyi.opc.crm.domain.CrmFollowUp;
import com.ruoyi.opc.crm.mapper.CrmContactMapper;
import com.ruoyi.opc.crm.mapper.CrmCustomerMapper;
import com.ruoyi.opc.crm.mapper.CrmFollowUpMapper;
import com.ruoyi.opc.crm.service.CrmOwnership;
import com.ruoyi.opc.crm.service.FollowUpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpServiceImpl implements FollowUpService {

    private final CrmFollowUpMapper mapper;
    private final CrmCustomerMapper customerMapper;
    private final CrmContactMapper contactMapper;

    @Override
    @Transactional
    public CrmFollowUp create(CrmFollowUp followUp, Long operatorId) {
        if (followUp.getCustomerId() == null) {
            throw new IllegalArgumentException("customerId required");
        }
        if (followUp.getContent() == null || followUp.getContent().isBlank()) {
            throw new IllegalArgumentException("Follow-up content cannot be empty (跟进内容不能为空)");
        }
        // 跟进挂到别人客户下 = 越权写入; contactId 同理(否则能把别人的联系人挂进来)
        CrmOwnership.requireCustomer(customerMapper, followUp.getCustomerId(), operatorId);
        CrmOwnership.requireContact(contactMapper, followUp.getContactId(), operatorId);
        followUp.setOwnerId(operatorId);
        followUp.setCreateBy(String.valueOf(operatorId));
        followUp.setUpdateBy(String.valueOf(operatorId));
        if (followUp.getType() == null) followUp.setType("PHONE");
        if (followUp.getDeleted() == null) followUp.setDeleted(0);
        mapper.insert(followUp);
        log.info("Created follow-up id={} customerId={} ownerId={}",
            followUp.getId(), followUp.getCustomerId(), followUp.getOwnerId());
        return followUp;
    }

    @Override
    @Transactional
    public CrmFollowUp markCompleted(Long id, Long operatorId) {
        CrmFollowUp existing = mapper.selectByIdAndOwner(id, operatorId);
        if (existing == null) {
            throw CrmOwnership.denied("跟进记录");
        }
        // Mark as completed by clearing nextAt — no further reminder needed.
        existing.setNextAt(null);
        existing.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(existing);
        log.info("Marked follow-up id={} completed by operator={}", id, operatorId);
        return existing;
    }

    @Override
    public CrmFollowUp getById(Long id, Long ownerId) {
        CrmFollowUp f = mapper.selectByIdAndOwner(id, ownerId);
        if (f == null) {
            throw CrmOwnership.denied("跟进记录");
        }
        return f;
    }

    @Override
    public List<CrmFollowUp> listByCustomer(Long customerId, Long ownerId) {
        return mapper.selectByCustomerIdAndOwner(customerId, ownerId);
    }

    @Override
    public List<CrmFollowUp> listByOwner(Long ownerId) {
        return mapper.selectByOwnerId(ownerId);
    }

    @Override
    public List<CrmFollowUp> listUpcoming(Long ownerId, LocalDateTime from) {
        return mapper.selectUpcoming(ownerId, from);
    }
}
