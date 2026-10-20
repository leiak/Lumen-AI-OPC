package com.ruoyi.opc.marketplace.service;

import com.ruoyi.opc.marketplace.domain.MpCategory;

import java.util.List;

/**
 * 商品类目 Service — P1
 *
 * <p><b>全局参考数据</b>，非租户维度：类目由种子数据维护（无写接口、无软删、无
 * {@code companyId}），因此本 Service 的所有方法都<b>不需要</b>
 * {@code SecurityUtils.getCompanyId()}，controller 层也不做 fail-closed 租户守卫
 * —— 加了反而会把「匿名浏览类目树」的公开读接口误伤成 400。
 *
 * <p>P1 只有 2 个只读方法；写入（新增/编辑/排序）留给 P2 的运营后台。
 *
 * @author OAC
 */
public interface CategoryService {

    /**
     * 类目列表（P1 扁平返回）
     *
     * <p>P1 <b>不做</b> {@code parent_id} 递归组树，直接返回 {@code selectAll()} 的
     * 扁平列表，由前端按 {@code parentId} 自行拼树。递归组树会引入
     * 「孤儿节点 / 环」处理成本，而类目是种子数据维护的静态表，收益为零。
     */
    List<MpCategory> tree();

    /** 按商品类型过滤类目（{@code AGENT} / {@code MODULE}），空值语义由 controller 兜底 */
    List<MpCategory> listByType(String productType);
}