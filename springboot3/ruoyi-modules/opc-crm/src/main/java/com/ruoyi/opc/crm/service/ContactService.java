package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmContact;

import java.util.List;

public interface ContactService {

    CrmContact create(CrmContact contact, Long operatorId);

    CrmContact update(Long id, CrmContact contact, Long operatorId);

    void delete(Long id, Long operatorId);

    /** 按 id 取联系人，但**必须**挂在 {@code ownerId} 名下的客户下。 */
    CrmContact getById(Long id, Long ownerId);

    /** 按客户列联系人，且该客户必须属于 {@code ownerId}。 */
    List<CrmContact> listByCustomer(Long customerId, Long ownerId);

    /**
     * 设主联系人。
     *
     * <p>客户 id 由服务端从联系人本身反查，**不采信**客户端给的那个 —— 否则就能拿
     * 别人的 contactId 顺手把人家客户的主联系人清掉。</p>
     */
    void setPrimary(Long contactId, Long operatorId);
}
