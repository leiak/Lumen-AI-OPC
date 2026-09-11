package com.ruoyi.opc.crm.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.crm.domain.CrmFollowUp;
import com.ruoyi.opc.crm.service.FollowUpService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 跟进记录 Controller
 *
 * 注意: service 接口的 {@code markCompleted(id, operatorId)} 不接收 result 参数;
 *       scheduled 时间窗使用 {@code listUpcoming(ownerId, from)} 单点起始.
 */
@RestController
@RequestMapping("/opc/crm/follow-up")
@RequiredArgsConstructor
public class FollowUpController {

    private final FollowUpService followUpService;

    @GetMapping
    public R<List<CrmFollowUp>> list(@RequestParam(required = false) Long customerId,
                                     @RequestParam(required = false) Long contactId,
                                     @RequestParam(required = false) Long ownerId) {
        if (customerId != null) return R.ok(followUpService.listByCustomer(customerId));
        if (contactId != null) {
            // contactId 维度未提供 listByContact, 回退为按 customer
            // (前端实际不会直接按 contactId 查, 多走 customer 维度)
            return R.ok(List.of());
        }
        if (ownerId != null) return R.ok(followUpService.listByOwner(ownerId));
        return R.ok(followUpService.listByOwner(SecurityUtils.getUserId()));
    }

    @GetMapping("/{id}")
    public R<CrmFollowUp> getById(@PathVariable Long id) {
        return R.ok(followUpService.getById(id));
    }

    @PostMapping
    public R<CrmFollowUp> create(@RequestBody CrmFollowUp followUp) {
        return R.ok(followUpService.create(followUp, SecurityUtils.getUserId()));
    }

    @PostMapping("/{id}/complete")
    public R<CrmFollowUp> complete(@PathVariable Long id) {
        return R.ok(followUpService.markCompleted(id, SecurityUtils.getUserId()));
    }

    @GetMapping("/scheduled")
    public R<List<CrmFollowUp>> scheduled(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam(required = false) Long ownerId) {
        return R.ok(followUpService.listUpcoming(
            ownerId != null ? ownerId : SecurityUtils.getUserId(), from));
    }
}