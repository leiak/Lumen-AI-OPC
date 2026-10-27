package com.ruoyi.opc.community.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.opc.community.domain.CommunityModule;
import com.ruoyi.opc.community.service.ModuleService;
import com.ruoyi.opc.common.exception.OpcException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 模块 — Marketplace 内部 API（W80 P2）
 *
 * <p>专供 opc-marketplace 通过 Feign（{@code CommunityClient}）调用，标注
 * {@link InnerAuth}（要求 {@code from-source: inner} header）。网关 AuthFilter 会剥掉
 * 外部请求伪造的该 header，因此这些端点不会被公网触达。
 *
 * <p><b>为什么社区需要一个被 marketplace 调用的端点</b>：
 * marketplace 的「功能模块」商品（{@code mp_publication.module_id} →
 * {@code opc_community_module.id}）装完之后，只写一行 {@code mp_install}，
 * 社区那边的 {@code install_count} 一行没动 —— 数字与事实对不上。
 * 根因是社区的 {@code incrementInstall} 只有一个写入方，就是社区自己那个公开端点
 * {@code POST /opc/community/module/{id}/install}；marketplace 够不着它。
 *
 * <p><b>边界</b>：社区拥有模块，marketplace 只读它的目录；<b>写操作一律走这里</b>。
 * marketplace 侧因此不该、也拿不到社区的写能力（它连社区的 mapper 都没有）。
 *
 * <p>与既有的 {@link ModuleController#install(Long)} <b>不重叠</b>：
 * <ul>
 *   <li>{@link ModuleController#install} 是<b>公开</b>端点，浏览器直接调，只加计数、
 *       不留安装记录（社区页自己的「安装」按钮）。</li>
 *   <li>本 controller 是<b>内部</b>端点，marketplace 装商品时调它，两边数字才一致。</li>
 * </ul>
 *
 * @author OAC
 */
@Tag(name = "OPC Community Marketplace 内部 API")
@Slf4j
@RestController
@RequestMapping("/opc/community/marketplace/module")
@RequiredArgsConstructor
public class OpcMarketplaceModuleController {

    private final ModuleService moduleService;

    /**
     * marketplace 装了一个功能模块商品 → 社区这边把安装数 +1。
     *
     * <p>request body（全部可选，仅用于审计）:
     * <pre>
     * {
     *   "tenantCompanyId": 2,   // 装的人是哪个租户公司
     *   "userId":         101,  // 操作者
     *   "publicationId":  1    // 对应的 marketplace 商品 id
     * }
     * </pre>
     *
     * <p>模块不存在 → 404（不是 500）：这属于「marketplace 挂了指向不存在的模块」，
     * 是调用方契约错，调用方会据此降级（安装照常成功，只是不涨社区计数）。
     */
    @Operation(summary = "Marketplace 装了一个功能模块 → 社区安装数 +1")
    @InnerAuth
    @PostMapping("/{id}/install")
    public R<Map<String, Object>> install(@PathVariable("id") Long id,
                                          @RequestBody(required = false) Map<String, Object> body) {
        Long tenantCompanyId = asLong(body, "tenantCompanyId");
        Long userId = asLong(body, "userId");
        Long publicationId = asLong(body, "publicationId");

        CommunityModule m;
        try {
            m = moduleService.getById(id);
        } catch (IllegalArgumentException ex) {
            // ModuleServiceImpl.getById 对不存在的模块抛 IllegalArgumentException,
            // 不是返回 null —— 这里翻译成 404:这属于「marketplace 挂了指向不存在的模块」,
            // 是调用方契约错,调用方会据此降级(安装照常成功,只是社区计数不涨)。
            throw new OpcException(404, "社区模块不存在: " + id, "COMMUNITY");
        }
        moduleService.incrementInstall(id);
        // 重新取一次拿自增后的计数 —— 上面那份是自增**前**的快照
        CommunityModule after = moduleService.getById(id);
        log.info("[Marketplace] community module installed moduleId={} code={} publicationId={} "
                        + "tenantCompanyId={} userId={}",
                id, m.getCode(), publicationId, tenantCompanyId, userId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("code", m.getCode());
        resp.put("installCount", after.getInstallCount());
        return R.ok(resp);
    }

    private static Long asLong(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(v.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
