package com.ruoyi.opc.hr.service;

import com.ruoyi.opc.hr.dto.OpcHrOfferDto;

/**
 * HR Offer 服务接口
 * 2 endpoints: create / respond
 */
public interface IOpcHrOfferService {

    /**
     * 创建 Offer(默认 status=PENDING, sentAt=NOW();一个 application 只能有一个 Offer)
     *
     * @return 新 Offer 的雪花 ID
     */
    Long create(OpcHrOfferDto dto);

    /**
     * 候选人响应(ACCEPTED / REJECTED);PENDING → 响应后置 respondedAt
     */
    void respond(Long id, Long companyId, String response);
}
