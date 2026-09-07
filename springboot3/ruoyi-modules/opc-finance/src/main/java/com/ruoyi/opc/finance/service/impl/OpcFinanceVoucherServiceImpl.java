package com.ruoyi.opc.finance.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.common.utils.OpcCodeGenerator;
import com.ruoyi.opc.finance.domain.OpcFinanceVoucher;
import com.ruoyi.opc.finance.mapper.OpcFinanceVoucherMapper;
import com.ruoyi.opc.finance.service.IOpcFinanceVoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OpcFinanceVoucherServiceImpl implements IOpcFinanceVoucherService {

    private final OpcFinanceVoucherMapper mapper;

    @Override
    public OpcFinanceVoucher getById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public List<OpcFinanceVoucher> listByCompany(Long companyId, String period, String status, Integer limit) {
        return mapper.selectByCompany(companyId, period, status, limit == null ? 20 : limit);
    }

    @Override
    public Long create(OpcFinanceVoucher voucher) {
        if (voucher.getVoucherCode() == null) {
            voucher.setVoucherCode(OpcCodeGenerator.voucherCode());
        }
        voucher.setStatus("DRAFT");
        // W10.3 审计字段：初始 updateBy = createBy（同一人创建即最后更新）
        // updateTime 由 mapper XML 自动 NOW()
        if (voucher.getUpdateBy() == null) {
            voucher.setUpdateBy(voucher.getCreateBy());
        }
        mapper.insert(voucher);
        return voucher.getId();
    }

    @Override
    public int update(OpcFinanceVoucher voucher) {
        // W10.3 审计字段：updateBy 必传（controller 在 updateVoucher 注入 SecurityUtils.getUsername()）
        // updateTime 由 mapper XML 自动 NOW()
        return mapper.update(voucher);
    }

    @Override
    public int reviewPass(Long id, String reviewer) {
        OpcFinanceVoucher v = new OpcFinanceVoucher();
        v.setId(id);
        v.setStatus("REVIEW");
        v.setReviewedBy(reviewer);
        v.setReviewedTime(new Date());
        v.setUpdateBy(reviewer);
        return mapper.update(v);
    }

    @Override
    public int reviewReject(Long id, String reviewer, String opinion) {
        OpcFinanceVoucher v = new OpcFinanceVoucher();
        v.setId(id);
        v.setStatus("REJECTED");
        v.setReviewedBy(reviewer);
        v.setReviewedTime(new Date());
        v.setRemark(opinion);
        v.setUpdateBy(reviewer);
        return mapper.update(v);
    }

    @Override
    public int post(Long id, String operator) {
        OpcFinanceVoucher exist = mapper.selectById(id);
        if (exist == null) throw new OpcException("凭证不存在");
        if (!"REVIEW".equals(exist.getStatus())) throw new OpcException("凭证未通过审核，不能入账");

        OpcFinanceVoucher v = new OpcFinanceVoucher();
        v.setId(id);
        v.setStatus("POSTED");
        v.setPostedBy(operator);
        v.setPostedTime(new Date());
        v.setUpdateBy(operator);
        return mapper.update(v);
    }

}
