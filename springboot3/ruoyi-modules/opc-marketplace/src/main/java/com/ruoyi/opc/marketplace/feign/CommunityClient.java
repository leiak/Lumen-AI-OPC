package com.ruoyi.opc.marketplace.feign;

import com.ruoyi.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * opc-marketplace → opc-community Feign 客户端 — W80 P2
 *
 * <p>用途：安装「功能模块」商品（{@code productType='MODULE'}）时，由 opc-community
 * 把 {@code opc_community_module.install_count} +1 —— 那是社区自己的计数，
 * marketplace 不该、也没有能力直接写它。
 *
 * <p><b>为什么必须有这个调用</b>：模块商品的「安装」此前只写一行 {@code mp_install}，
 * 社区那边一行没动。于是同一件事在两个页面上显示成两个数字：
 * 商品卡「已安装 N 次」与模块社区「有 M 人安装」对不上，且谁都不是真的。
 * 社区的 {@code incrementInstall} 唯一写入方是社区自己的公开端点，
 * marketplace 够不着 —— 这个 client 就是那条通路。
 *
 * <p><b>降级语义与 {@link AgentHubClient} 相反，这是刻意的</b>：
 * <ul>
 *   <li>agent-hub 建实例失败 → 503，整个安装失败。因为实例没建出来，
 *       「装成功」本身就是假的。</li>
 *   <li>community 计数失败 → <b>只记 WARN，安装照常成功</b>。社区那个数字只是展示用的
 *       计数器，它没涨只说明这次计数没记上，不说明模块没装 —— 拿一个展示计数把
 *       整个安装操作回滚，比数字不准更糟。</li>
 * </ul>
 * 代价是可能出现「商品装好了、社区计数没涨」的短暂不一致；对应的护栏见
 * {@code deploy/scripts/health-check.sh} 第 19 节。
 *
 * <p>对端 controller 标注 {@code @InnerAuth}（要求 {@code from-source: inner} header），
 * 由 {@code FeignRequestInterceptor} 无条件注入该 header，公网直连打不到。
 *
 * @author OAC
 */
@FeignClient(contextId = "opcMarketplaceCommunity",
        name = "opc-community",
        path = "/opc/community",
        fallbackFactory = CommunityClientFallback.class)
public interface CommunityClient {

    /**
     * 通知社区「这个模块被装了一次」（社区那边把 install_count +1）。
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
     * <p>成功响应：{@code R.ok({id, code, installCount})}。
     * 模块不存在 → 对端返回 404，Feign 走 fallback（见
     * {@link CommunityClientFallback}）。
     */
    @PostMapping("/marketplace/module/{id}/install")
    R<Map<String, Object>> installModule(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);
}
