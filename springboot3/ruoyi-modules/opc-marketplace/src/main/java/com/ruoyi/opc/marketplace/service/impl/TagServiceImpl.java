package com.ruoyi.opc.marketplace.service.impl;

import com.ruoyi.opc.marketplace.domain.MpTag;
import com.ruoyi.opc.marketplace.mapper.MpTagMapper;
import com.ruoyi.opc.marketplace.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品标签 Service 实现 — P1
 *
 * <p>纯只读透传，无租户守卫（标签是全局参考数据）。
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final MpTagMapper tagMapper;

    @Override
    public List<MpTag> list() {
        return tagMapper.selectAll();
    }

    @Override
    public List<MpTag> listByCategory(String category) {
        return tagMapper.selectByCategory(category);
    }
}