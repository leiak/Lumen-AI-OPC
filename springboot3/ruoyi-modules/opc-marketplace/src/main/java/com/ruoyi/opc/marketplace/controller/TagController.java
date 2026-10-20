package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.marketplace.domain.MpTag;
import com.ruoyi.opc.marketplace.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品标签 Controller — P1
 *
 * <p>路由：{@code /opc/marketplace/tag}
 * <ul>
 *   <li>GET /list — 标签列表，可选 {@code ?category=quality|lang|scale|billing|domain} 过滤</li>
 * </ul>
 *
 * <p><b>无租户守卫</b>：标签是全局参考数据（种子维护、无 company_id 列），公开读，
 * 刻意<b>不</b>调 {@code SecurityUtils.getCompanyId()}。
 *
 * @author OAC
 */
@RestController
@RequestMapping("/opc/marketplace/tag")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping("/list")
    public R<List<MpTag>> list(@RequestParam(required = false) String category) {
        if (category == null || category.isEmpty()) {
            return R.ok(tagService.list());
        }
        return R.ok(tagService.listByCategory(category));
    }
}