package com.ruoyi.opc.agent.service.impl;

import com.ruoyi.opc.agent.mapper.OpcAgentDefinitionMapper;
import com.ruoyi.opc.agent.mapper.OpcAgentInstanceMapper;
import com.ruoyi.opc.common.exception.OpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OpcAgentInstanceServiceImpl} 租户作用域回归测试 — W80+
 *
 * <p>验证「在 SQL 层做租户校验」的契约：{@code pause} / {@code resume} / {@code revoke}
 * 三个写方法必须把 {@code companyId} 透传到 mapper,mapper 的 SQL 是
 * {@code WHERE id = #{id} AND company_id = #{companyId}}。
 * 当 {@code rowsAffected == 0}(实例不存在 / 不属于该租户),service 必须抛
 * {@link OpcException} code=403。
 *
 * <p>为何不让 controller 单测覆盖:反射式 service 契约校验能挡住未来「不小心把
 * companyId 参数删掉」的回归,这是单测能抓到而 controller 单测抓不到的回归面。
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcAgentInstanceServiceImplTenantTest {

    @Mock
    private OpcAgentInstanceMapper instanceMapper;

    @Mock
    private OpcAgentDefinitionMapper definitionMapper;

    @InjectMocks
    private OpcAgentInstanceServiceImpl service;

    private static final Long INSTANCE_ID = 999L;
    private static final Long COMPANY_A = 100L;
    private static final Long COMPANY_B = 200L;
    private static final String OPERATOR = "7";

    @BeforeEach
    void setUp() {
        // 默认:0 行 — 模拟「companyId 不匹配 / 实例不存在」
    }

    // ==================== pause ====================

    @Test
    @DisplayName("pause — rowsAffected=1 时正常返回 1")
    void pause_ok_whenRowUpdated() {
        when(instanceMapper.updateStatusByCompany(eq(INSTANCE_ID), eq(COMPANY_A),
                eq("PAUSED"), eq(OPERATOR))).thenReturn(1);

        int rows = service.pause(INSTANCE_ID, COMPANY_A, OPERATOR);
        assertEquals(1, rows);
    }

    @Test
    @DisplayName("pause — rowsAffected=0 时抛 403;companyId 必须透传到 mapper")
    void pause_403_whenZeroRowsAffected() {
        when(instanceMapper.updateStatusByCompany(anyLong(), anyLong(), any(), any())).thenReturn(0);

        OpcException ex = assertThrows(OpcException.class,
                () -> service.pause(INSTANCE_ID, COMPANY_A, OPERATOR));
        assertEquals(403, ex.getCode());

        // 关键:companyId 必须透传到 mapper(否则 SQL WHERE 漏掉 company_id 就没意义)
        verify(instanceMapper).updateStatusByCompany(INSTANCE_ID, COMPANY_A, "PAUSED", OPERATOR);
    }

    @Test
    @DisplayName("pause — 不带 companyId → 400(防止空指针穿透到 mapper)")
    void pause_400_whenCompanyIdNull() {
        OpcException ex = assertThrows(OpcException.class,
                () -> service.pause(INSTANCE_ID, null, OPERATOR));
        assertEquals(400, ex.getCode());
        // 关键:mapper 完全不被调
        verify(instanceMapper, never()).updateStatusByCompany(anyLong(), anyLong(), any(), any());
    }

    // ==================== resume ====================

    @Test
    @DisplayName("resume — rowsAffected=0 → 403")
    void resume_403_whenZeroRowsAffected() {
        when(instanceMapper.updateStatusByCompany(anyLong(), anyLong(), any(), any())).thenReturn(0);

        OpcException ex = assertThrows(OpcException.class,
                () -> service.resume(INSTANCE_ID, COMPANY_A, OPERATOR));
        assertEquals(403, ex.getCode());
        verify(instanceMapper).updateStatusByCompany(INSTANCE_ID, COMPANY_A, "RUNNING", OPERATOR);
    }

    // ==================== revoke ====================

    @Test
    @DisplayName("revoke — rowsAffected=1 → 1 (正常)")
    void revoke_ok_whenRowUpdated() {
        when(instanceMapper.updateStatusByCompany(eq(INSTANCE_ID), eq(COMPANY_B),
                eq("REVOKED"), eq("marketplace"))).thenReturn(1);

        int rows = service.revoke(INSTANCE_ID, COMPANY_B, "marketplace");
        assertEquals(1, rows);
    }

    @Test
    @DisplayName("revoke — B 公司租户传错 companyId(A) → 403,revoke 不会影响 A 公司的实例")
    void revoke_403_whenCompanyMismatch() {
        when(instanceMapper.updateStatusByCompany(anyLong(), anyLong(), any(), any())).thenReturn(0);

        // marketplace 路径:tenantCompanyId 必须等于 instance.companyId 才能 revoke
        OpcException ex = assertThrows(OpcException.class,
                () -> service.revoke(INSTANCE_ID, COMPANY_A, "marketplace"));
        assertEquals(403, ex.getCode());
        // 关键断言:companyId 是 COMPANY_A,不是 COMPANY_B —— 防止 marketplace 误传别家 id
        verify(instanceMapper).updateStatusByCompany(INSTANCE_ID, COMPANY_A, "REVOKED", "marketplace");
    }

    @Test
    @DisplayName("revoke — null id 或 null companyId → 400")
    void revoke_400_whenParamsNull() {
        assertThrows(OpcException.class, () -> service.revoke(null, COMPANY_A, "op"));
        assertThrows(OpcException.class, () -> service.revoke(INSTANCE_ID, null, "op"));
        verify(instanceMapper, never()).updateStatusByCompany(anyLong(), anyLong(), any(), any());
    }

    // ==================== operatorId 默认值 ====================

    @Test
    @DisplayName("revoke — operatorId 为 null 时回退到 'system' 写入 update_by")
    void revoke_defaultsOperatorToSystem_whenNull() {
        when(instanceMapper.updateStatusByCompany(eq(INSTANCE_ID), eq(COMPANY_A),
                eq("REVOKED"), eq("system"))).thenReturn(1);

        int rows = service.revoke(INSTANCE_ID, COMPANY_A, null);
        assertEquals(1, rows);
        verify(instanceMapper).updateStatusByCompany(INSTANCE_ID, COMPANY_A, "REVOKED", "system");
    }

    // ==================== getByIdAndCompany ====================

    @Test
    @DisplayName("getByIdAndCompany — 返回 null 时 controller 负责判 403;service 不抛")
    void getByIdAndCompany_returnsNullWhenNotOwned() {
        when(instanceMapper.selectByIdAndCompany(INSTANCE_ID, COMPANY_A)).thenReturn(null);

        Object inst = service.getByIdAndCompany(INSTANCE_ID, COMPANY_A);
        org.junit.jupiter.api.Assertions.assertNull(inst);
        verify(instanceMapper).selectByIdAndCompany(INSTANCE_ID, COMPANY_A);
    }

    @Test
    @DisplayName("getByIdAndCompany — controller 用的非租户入口 getById 仍存在但不在 controller 调用路径上")
    void getById_andGetByIdAndCompany_areBothAvailable() {
        // service 仍保留 getById(无租户过滤) —— 内部代码(如 addTokenUsed/incrementTaskCount
        // 后续若要加)可用,但 controller 公开入口必须用 getByIdAndCompany
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(null);
        service.getById(INSTANCE_ID);
        verify(instanceMapper).selectById(INSTANCE_ID);
    }
}
