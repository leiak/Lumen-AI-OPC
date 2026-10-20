package com.ruoyi.opc.marketplace.service;

import com.ruoyi.opc.marketplace.domain.MpTag;

import java.util.List;

/**
 * 商品标签 Service — P1
 *
 * <p>与 {@link CategoryService} 同为<b>全局参考数据</b>：标签由种子数据维护，
 * 无租户维度、无写接口，因此不做 {@code SecurityUtils.getCompanyId()} 守卫。
 *
 * @author OAC
 */
public interface TagService {

    /** 全部标签；空值语义（{@code category} 缺省）由 controller 兜底 */
    List<MpTag> list();

    /** 按标签分类过滤（{@code quality} / {@code lang} / {@code scale} / {@code billing} / {@code domain}） */
    List<MpTag> listByCategory(String category);
}