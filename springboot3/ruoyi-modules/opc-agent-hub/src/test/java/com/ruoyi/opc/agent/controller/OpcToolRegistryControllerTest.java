package com.ruoyi.opc.agent.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.opc.ai.tools.ToolRegistry;
import com.ruoyi.opc.ai.tools.ToolSpec;
import com.ruoyi.opc.agent.tools.BillingToolClient;
import com.ruoyi.opc.agent.tools.FinanceToolClient;
import com.ruoyi.opc.agent.tools.FinanceTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link OpcToolRegistryController} 单元测试。
 *
 * <p><b>这个端点存在的理由</b>：健康检查第 26.5 条要断言「假工具确实下线了」。
 * 不把这个注册表暴露出来，那条断言就无从下手 —— 注册表里还有没有
 * {@code query_daily_report} / {@code create_voucher} / {@code extract_voucher}，
 * 从外面根本看不见。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
class OpcToolRegistryControllerTest {

    @Mock
    private ToolRegistry registry;

    /** 真注册表测试里用 —— 注册过程不调它们，只是构造 FinanceTools 需要 */
    @Mock
    private FinanceToolClient financeClient;

    @Mock
    private BillingToolClient billingClient;

    private OpcToolRegistryController controller;

    private static final List<String> NAMES =
            List.of("query_bank_flow", "query_voucher", "query_balance");

    @BeforeEach
    void setUp() {
        controller = new OpcToolRegistryController(registry);
    }

    /** 注册表里有 3 个真工具，且都带 spec */
    private void stubRegistry() {
        when(registry.names()).thenReturn(NAMES);
        when(registry.specs()).thenReturn(Map.of(
                "query_bank_flow", ToolSpec.of("query_bank_flow", "查银行流水", Map.of()),
                "query_voucher", ToolSpec.of("query_voucher", "查凭证", Map.of()),
                "query_balance", ToolSpec.of("query_balance", "查余额", Map.of())));
    }

    private Set<String> namesOf(R<List<Map<String, Object>>> r) {
        return r.getData().stream()
                .map(m -> (String) m.get("name"))
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("list — 返回的 data 里含 3 个真工具的 name")
    void tools_listsRegisteredNames() {
        stubRegistry();
        R<List<Map<String, Object>>> r = controller.list();

        assertEquals(R.SUCCESS, r.getCode());
        assertNotNull(r.getData());
        assertTrue(namesOf(r).containsAll(NAMES), "返回的工具名应含 " + NAMES + "，实际: " + namesOf(r));
    }

    @Test
    @DisplayName("list — 不含任何假工具（健康检查第 26.5 条断言的就是这个）—— 用【真注册表】守")
    void tools_doesNotListFakeTools() {
        // 这一条必须用真的 ToolRegistry —— 之前拿 @Mock ToolRegistry，
        // 把 names() 直接 stub 成 3 个名字，那条断言【不可能失败】：
        // 它只守住了「controller 不硬塞假名字」，守不住「注册表里真的没有假工具」。
        // 现在是真的注册一遍，再从端点往外看。
        ToolRegistry realRegistry = new ToolRegistry();
        new FinanceTools(realRegistry, financeClient, billingClient).register();
        OpcToolRegistryController realController = new OpcToolRegistryController(realRegistry);

        R<List<Map<String, Object>>> r = realController.list();

        Set<String> names = namesOf(r);
        for (String fake : List.of("query_daily_report", "create_voucher", "extract_voucher")) {
            assertFalse(names.contains(fake),
                    "假工具 " + fake + " 不该出现在注册表里 —— 实际注册表: " + names);
        }
        // 而且恰好是那 3 个真工具 —— 「假工具下线」不是「少几个」，是「恰好这几个」
        assertEquals(Set.copyOf(NAMES), names,
                "真注册表里应当恰好是这 3 个真工具，实际: " + names);
    }

    @Test
    @DisplayName("list — 每个工具都带 hasSpec=true（spec 与 handler 同生共死）")
    void tools_reportsHasSpec() {
        stubRegistry();
        R<List<Map<String, Object>>> r = controller.list();

        for (Map<String, Object> item : r.getData()) {
            assertEquals(Boolean.TRUE, item.get("hasSpec"),
                    "工具 " + item.get("name") + " 应当带 spec");
            assertNotNull(item.get("specDescription"), "工具 " + item.get("name") + " 应当有 spec 描述");
        }
    }

    @Test
    @DisplayName("list — 端点标注 @InnerAuth（网关打不到，只有服务间调用能命中）")
    void tools_isInnerAuthOnly() throws Exception {
        Method m = OpcToolRegistryController.class.getMethod("list");
        InnerAuth annotation = m.getAnnotation(InnerAuth.class);
        assertNotNull(annotation, "list 方法必须标注 @InnerAuth");
        assertFalse(annotation.isUser(), "默认 isUser=false —— 内部调用无需 user 信息");
    }

}
