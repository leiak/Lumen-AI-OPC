package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmCustomer;
import java.util.List;

public interface CustomerService {
    CrmCustomer create(CrmCustomer customer, Long ownerId);
    CrmCustomer update(Long id, CrmCustomer customer, Long operatorId);
    void delete(Long id, Long operatorId);

    /**
     * 按 id 取客户，但**必须**属于 {@code ownerId}。
     *
     * <p>不存在 / 不是你的，都回 403「客户不存在或无权访问」—— 两种情况同一句话，
     * 不给探测方留存在性预言机。</p>
     */
    CrmCustomer getById(Long id, Long ownerId);

    List<CrmCustomer> list(Long ownerId, String level, String source, String tag, String keyword);
    int countByOwner(Long ownerId);
}
