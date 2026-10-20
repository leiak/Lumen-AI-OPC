package com.ruoyi.opc.marketplace.service.impl;

import com.ruoyi.opc.marketplace.domain.MpCategory;
import com.ruoyi.opc.marketplace.mapper.MpCategoryMapper;
import com.ruoyi.opc.marketplace.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品类目 Service 实现 — P1
 *
 * <p>纯只读透传：类目是全局参考数据（种子维护），既无租户维度也无软删语义，
 * 所以这里没有 {@code companyId} 守卫，也没有 {@code @Transactional}。
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final MpCategoryMapper categoryMapper;

    @Override
    public List<MpCategory> tree() {
        // P1: flat list (no parent_id handling)
        return categoryMapper.selectAll();
    }

    @Override
    public List<MpCategory> listByType(String productType) {
        return categoryMapper.selectByProductType(productType);
    }
}