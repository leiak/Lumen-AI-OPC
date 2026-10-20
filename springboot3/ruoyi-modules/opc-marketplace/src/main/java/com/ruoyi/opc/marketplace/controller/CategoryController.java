package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpCategory;
import com.ruoyi.opc.marketplace.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品类目 Controller — P1
 *
 * <p>路由：{@code /opc/marketplace/category}
 * <ul>
 *   <li>GET /tree       — 全部类目（P1 扁平，前端按 parentId 自行拼树）</li>
 *   <li>GET /list       — 类目列表，可选 {@code ?productType=AGENT|MODULE} 过滤</li>
 *   <li>GET /{id}       — 单个类目，不存在 → 404</li>
 * </ul>
 *
 * <p><b>无租户守卫</b>：类目是全局参考数据（种子维护、无 company_id 列），所有端点都是
 * 公开读，因此刻意<b>不</b>调 {@code SecurityUtils.getCompanyId()} —— 加了 fail-closed
 * 守卫会把匿名浏览类目树的场景误伤成 400。
 *
 * @author OAC
 */
@RestController
@RequestMapping("/opc/marketplace/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/tree")
    public R<List<MpCategory>> tree() {
        return R.ok(categoryService.tree());
    }

    @GetMapping("/list")
    public R<List<MpCategory>> list(@RequestParam(required = false) String productType) {
        if (productType == null || productType.isEmpty()) {
            return R.ok(categoryService.tree());
        }
        return R.ok(categoryService.listByType(productType));
    }

    /**
     * 单个类目。
     *
     * <p><b>P1 有意为之的取舍</b>：走 {@code tree()} 再在内存里按 id 过滤，是 O(n) 的
     * 单条查询（mapper 已有 {@code selectById}）。类目表是种子维护的静态小表，n 极小，
     * 换来的是「树与详情共用一条查询路径」的读一致性；P2 若接入类目管理后台再换回
     * {@code selectById}。
     */
    @GetMapping("/{id}")
    public R<MpCategory> get(@PathVariable Long id) {
        MpCategory c = categoryService.tree().stream().filter(x -> x.getId().equals(id)).findFirst()
                .orElseThrow(() -> new OpcException(404, "类目不存在", "MARKETPLACE"));
        return R.ok(c);
    }
}