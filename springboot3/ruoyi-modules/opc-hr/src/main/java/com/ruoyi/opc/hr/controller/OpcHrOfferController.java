package com.ruoyi.opc.hr.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.hr.dto.OpcHrOfferDto;
import com.ruoyi.opc.hr.service.IOpcHrOfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HR Offer Controller
 * 2 endpoints: create / respond
 */
@RestController
@RequestMapping("/opc/hr/offer")
@RequiredArgsConstructor
public class OpcHrOfferController {

    private final IOpcHrOfferService offerService;

    @PostMapping
    public R<Long> create(@RequestBody OpcHrOfferDto dto) {
        return R.ok(offerService.create(dto));
    }

    @PutMapping("/{id}/respond")
    public R<Void> respond(@PathVariable Long id,
                           @RequestParam Long companyId,
                           @RequestParam String response) {
        offerService.respond(id, companyId, response);
        return R.ok();
    }
}
