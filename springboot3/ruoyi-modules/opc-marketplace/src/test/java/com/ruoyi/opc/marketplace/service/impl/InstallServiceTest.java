package com.ruoyi.opc.marketplace.service.impl;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpInstall;
import com.ruoyi.opc.marketplace.domain.MpPublication;
import com.ruoyi.opc.marketplace.feign.AgentHubClient;
import com.ruoyi.opc.marketplace.mapper.MpInstallMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InstallServiceImpl} 单元测试 — P1 Task 8.5
 *
 * <p>重点覆盖「软删除 × 不含 deleted 的唯一键」这条设计文档 §6 陷阱：
 * {@code mp_install.uk_install(publication_id, tenant_company_id, user_id)} 不含
 * {@code deleted} 列，卸载后的行<b>仍占用唯一键</b>。因此「卸载 → 重装」必须是
 * {@link MpInstallMapper#reactivate} 复活既有行，而不是再 {@code INSERT} 一次（否则 1062）。
 * 同时「复活」<b>不得</b>累加 {@code install_count}（市场级计数器，刷榜是真实缺陷）。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstallServiceTest {

    @Mock
    MpInstallMapper installMapper;
    @Mock
    MpPublicationMapper publicationMapper;
    @Mock
    AgentHubClient agentHubClient;

    private InstallServiceImpl installService;

    @BeforeEach
    void setUp() {
        installService = new InstallServiceImpl(installMapper, publicationMapper, agentHubClient);
    }

    private static MpPublication pub(String productType) {
        return MpPublication.builder().id(1L).status("PUBLISHED").productType(productType).build();
    }

    @Test
    @DisplayName("install — 商品不存在 → 404,且不触碰任何 install 表")
    void install_404_whenPublicationMissing() {
        when(publicationMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> installService.install(999L, 1L, 1L))
                .isInstanceOf(OpcException.class);

        verify(installMapper, never()).insert(any(MpInstall.class));
    }

    @Test
    @DisplayName("install — 商品未发布(DRAFT) → 400「未发布」,不下探到 install 表")
    void install_400_whenNotPublished() {
        MpPublication p = MpPublication.builder().id(1L).status("DRAFT").productType("AGENT").build();
        when(publicationMapper.selectById(1L)).thenReturn(p);

        assertThatThrownBy(() -> installService.install(1L, 1L, 1L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("未发布");

        verify(installMapper, never()).selectUnique(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("install — 已 ACTIVE → 409「已安装」,不重复调远端建实例(用户连点只计一次)")
    void install_409_whenAlreadyInstalled() {
        when(publicationMapper.selectById(1L)).thenReturn(pub("AGENT"));
        MpInstall existing = MpInstall.builder().id(99L).status("ACTIVE").build();
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(existing);

        assertThatThrownBy(() -> installService.install(1L, 1L, 1L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("已安装");

        verify(agentHubClient, never()).createAgentInstance(any());
    }

    @Test
    @DisplayName("install — 曾卸载过的行必须 reactivate 复活,不得 INSERT(uk_install 不含 deleted,否则 1062);且不累加 install_count")
    void install_revivesRow_whenPreviouslyUninstalled() {
        when(publicationMapper.selectById(1L)).thenReturn(pub("MODULE"));
        MpInstall existing = MpInstall.builder().id(99L).status("UNINSTALLED").build();
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(existing);

        MpInstall result = installService.install(1L, 1L, 1L);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(installMapper).reactivate(eq(99L), isNull(), any(LocalDateTime.class), eq("1"));
        verify(installMapper, never()).insert(any(MpInstall.class));
        // 复活是同一行的 status 翻转,不是「新装」,不能喂市场级计数器
        verify(publicationMapper, never()).incrementInstallCount(anyLong());
    }

    @Test
    @DisplayName("uninstall — 记录已是 UNINSTALLED → 409「已卸载」,不重复写状态")
    void uninstall_409_whenAlreadyUninstalled() {
        MpInstall existing = MpInstall.builder().id(99L).status("UNINSTALLED").build();
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(existing);

        assertThatThrownBy(() -> installService.uninstall(1L, 1L, 1L))
                .isInstanceOf(OpcException.class)
                .hasMessageContaining("已卸载");

        verify(installMapper, never()).updateStatus(anyLong(), any(), any());
    }

    @Test
    @DisplayName("install — AGENT 商品:先调 agent-hub 建实例,拿 id 落 mp_install,并累加 install_count")
    @SuppressWarnings("unchecked")
    void install_AGENT_callsAgentHubAndPersistsInstall() {
        MpPublication p = MpPublication.builder().id(1L).status("PUBLISHED")
                .productType("AGENT").agentDefinitionId(50L).name("AI 助手").build();
        when(publicationMapper.selectById(1L)).thenReturn(p);
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(null);
        when(agentHubClient.createAgentInstance(any())).thenReturn(R.ok(Map.<String, Object>of("id", 100L)));
        when(installMapper.insert(any(MpInstall.class))).thenAnswer(inv -> {
            MpInstall i = inv.getArgument(0);
            i.setId(200L);
            return 1;
        });

        MpInstall result = installService.install(1L, 1L, 1L);

        assertThat(result.getAgentInstanceId()).isEqualTo(100L);
        assertThat(result.getInstallSource()).isEqualTo("MARKET");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        // 本模块唯一的 Feign 边界,也是「别的租户的身份」唯一一次跨线的地方 —— 四个键必须逐个断言。
        // verify(createAgentInstance(any())) 对任何一个键被删/改 null 都是绿的。
        // agentDefinitionId 取自 MpPublication(MpPublication.agentDefinitionId 声明为 Long),
        // tenantCompanyId / userId 取自入参 —— 漏掉任一个,对端要么建不出实例,要么建到别的租户名下。
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(agentHubClient).createAgentInstance(body.capture());
        assertThat(body.getValue())
                .containsEntry("agentDefinitionId", 50L)
                .containsEntry("tenantCompanyId", 1L)
                .containsEntry("userId", 1L)
                .containsEntry("nickname", "AI 助手");
        verify(publicationMapper).incrementInstallCount(1L);
    }

    @Test
    @DisplayName("install — MODULE 商品:纯本地登记,完全不碰 agent-hub")
    void install_MODULE_skipsAgentHub() {
        MpPublication p = MpPublication.builder().id(1L).status("PUBLISHED")
                .productType("MODULE").moduleId(50L).build();
        when(publicationMapper.selectById(1L)).thenReturn(p);
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(null);
        when(installMapper.insert(any(MpInstall.class))).thenAnswer(inv -> {
            MpInstall i = inv.getArgument(0);
            i.setId(200L);
            return 1;
        });

        MpInstall result = installService.install(1L, 1L, 1L);

        assertThat(result.getAgentInstanceId()).isNull();
        verify(agentHubClient, never()).createAgentInstance(any());
    }

    // ——————————————— Task 8.5 code-review 补齐 ———————————————

    @Test
    @DisplayName("install — AGENT 商品重装:既有 UNINSTALLED 行复活,且必须重新绑定新实例 id")
    void install_AGENT_revive_reassignsInstanceId() {
        MpPublication p = MpPublication.builder().id(1L).status("PUBLISHED")
                .productType("AGENT").agentDefinitionId(50L).name("AI 助手").build();
        when(publicationMapper.selectById(1L)).thenReturn(p);
        MpInstall existing = MpInstall.builder().id(99L).status("UNINSTALLED")
                .publicationId(1L).tenantCompanyId(1L).userId(1L).build();
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(existing);
        when(agentHubClient.createAgentInstance(any())).thenReturn(R.ok(Map.<String, Object>of("id", 100L)));

        MpInstall result = installService.install(1L, 1L, 1L);

        // MODULE 的复活用例里 instanceId 恒为 null,「把新实例 id 写回复活行」这条分支
        // 只有 AGENT 才走得到 —— 少了 existing.setAgentInstanceId(instanceId),重装用户的
        // mp_install.agent_instance_id 会永远停在旧值/空,后续找不到自己的实例。
        assertThat(result.getAgentInstanceId()).isEqualTo(100L);
        verify(installMapper).reactivate(eq(99L), eq(100L), any(LocalDateTime.class), eq("1"));
        verify(installMapper, never()).insert(any(MpInstall.class));
        verify(publicationMapper, never()).incrementInstallCount(anyLong());
    }

    @Test
    @DisplayName("uninstall — 正常路径:ACTIVE 行置 UNINSTALLED 并写 uninstalled_at")
    void uninstall_success_setsUninstalled() {
        MpInstall existing = MpInstall.builder().id(99L).status("ACTIVE").build();
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(existing);

        installService.uninstall(1L, 1L, 1L);

        verify(installMapper).updateStatus(eq(99L), eq("UNINSTALLED"), any(LocalDateTime.class));
        // 卸载只是本地置态,不下架远端 Agent 实例(设计选择:实例由用户在 agent-hub 显式退订)
        verify(agentHubClient, never()).revokeAgentInstance(anyLong(), any());
    }

    @Test
    @DisplayName("install — agent-hub 返回 null → 503「返回空响应」(防御分支,不得退化成 NPE/500)")
    void install_503_whenAgentHubReturnsNull() {
        MpPublication p = MpPublication.builder().id(1L).status("PUBLISHED")
                .productType("AGENT").agentDefinitionId(50L).name("AI 助手").build();
        when(publicationMapper.selectById(1L)).thenReturn(p);
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(null);
        when(agentHubClient.createAgentInstance(any())).thenReturn(null);

        assertThatThrownBy(() -> installService.install(1L, 1L, 1L))
                .isInstanceOf(OpcException.class)
                .hasMessage("opc-agent-hub 返回空响应");
        // 消息是固定文案,不拼 cause.getMessage() —— 对端内部异常不得外泄
        OpcException ex = catchThrowableOfType(
                () -> installService.install(1L, 1L, 1L), OpcException.class);
        assertThat(ex.getCode()).isEqualTo(503);

        // 远端没拿到 id,本地一行都不能写;也不存在可退订的实例
        verify(installMapper, never()).insert(any(MpInstall.class));
        verify(agentHubClient, never()).revokeAgentInstance(anyLong(), any());
    }

    @Test
    @DisplayName("install — agent-hub 的 id 不是数字(如 String)→ 503「类型异常」,不得 ClassCastException/500")
    void install_503_whenAgentHubIdNotNumber() {
        MpPublication p = MpPublication.builder().id(1L).status("PUBLISHED")
                .productType("AGENT").agentDefinitionId(50L).name("AI 助手").build();
        when(publicationMapper.selectById(1L)).thenReturn(p);
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(null);
        when(agentHubClient.createAgentInstance(any())).thenReturn(R.ok(Map.<String, Object>of("id", "not-a-number")));

        assertThatThrownBy(() -> installService.install(1L, 1L, 1L))
                .isInstanceOf(OpcException.class)
                .hasMessage("opc-agent-hub 返回的实例 id 缺失或类型异常");

        verify(installMapper, never()).insert(any(MpInstall.class));
    }

    @Test
    @DisplayName("install — 远端已建实例但本地 insert 失败 → best-effort revoke(带 tenantCompanyId),原异常照抛")
    void install_localWriteFails_revokesRemoteInstance() {
        MpPublication p = MpPublication.builder().id(1L).status("PUBLISHED")
                .productType("AGENT").agentDefinitionId(50L).name("AI 助手").build();
        when(publicationMapper.selectById(1L)).thenReturn(p);
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(null);
        when(agentHubClient.createAgentInstance(any())).thenReturn(R.ok(Map.<String, Object>of("id", 100L)));
        when(installMapper.insert(any(MpInstall.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> installService.install(1L, 1L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");

        // 不退订就会留下「幽灵 AgentInstance + 无 mp_install 行」。revoke body 必须带
        // tenantCompanyId —— 服务端在 SQL 层校验 WHERE id=? AND company_id=?,
        // 漏了会被判 403,补偿就等于没做(且有跨租户误退订的风险)。
        verify(agentHubClient).revokeAgentInstance(eq(100L),
                argThat(m -> Long.valueOf(1L).equals(m.get("tenantCompanyId"))));
        verify(publicationMapper, never()).incrementInstallCount(anyLong());
    }

    @Test
    @DisplayName("install — 本地写失败且 revoke 也失败 → 仍抛原始 insert 异常(补偿不得掩盖根因)")
    void install_revokeAlsoFails_stillPropagatesOriginal() {
        MpPublication p = MpPublication.builder().id(1L).status("PUBLISHED")
                .productType("AGENT").agentDefinitionId(50L).name("AI 助手").build();
        when(publicationMapper.selectById(1L)).thenReturn(p);
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(null);
        when(agentHubClient.createAgentInstance(any())).thenReturn(R.ok(Map.<String, Object>of("id", 100L)));
        when(installMapper.insert(any(MpInstall.class))).thenThrow(new RuntimeException("db down"));
        when(agentHubClient.revokeAgentInstance(anyLong(), any()))
                .thenThrow(new RuntimeException("revoke down"));

        // 两条都失败是真孤儿状态,由 log.error 带三元组交给运维;但对外抛的必须是
        // insert 的异常 —— 若改成抛 revoke 的异常,排障方向就被带偏了。
        assertThatThrownBy(() -> installService.install(1L, 1L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");

        verify(agentHubClient).revokeAgentInstance(eq(100L), any());
    }

    @Test
    @DisplayName("install — MODULE 本地写失败:没有远端实例,绝不去 revoke(防 null id 打远端)")
    void install_MODULE_localWriteFails_doesNotRevoke() {
        MpPublication p = MpPublication.builder().id(1L).status("PUBLISHED")
                .productType("MODULE").moduleId(50L).build();
        when(publicationMapper.selectById(1L)).thenReturn(p);
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(null);
        when(installMapper.insert(any(MpInstall.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> installService.install(1L, 1L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");

        // if (instanceId != null) 这道闸门被拆掉的话,MODULE 路径会拿着 null 去退订
        verify(agentHubClient, never()).revokeAgentInstance(anyLong(), any());
    }
}