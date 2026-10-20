package com.ruoyi.opc.marketplace.service.impl;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
    void install_AGENT_callsAgentHubAndPersistsInstall() {
        MpPublication p = MpPublication.builder().id(1L).status("PUBLISHED")
                .productType("AGENT").agentDefinitionId(50L).build();
        when(publicationMapper.selectById(1L)).thenReturn(p);
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(null);
        when(agentHubClient.createAgentInstance(any())).thenReturn(Map.of("id", 100L));
        when(installMapper.insert(any(MpInstall.class))).thenAnswer(inv -> {
            MpInstall i = inv.getArgument(0);
            i.setId(200L);
            return 1;
        });

        MpInstall result = installService.install(1L, 1L, 1L);

        assertThat(result.getAgentInstanceId()).isEqualTo(100L);
        assertThat(result.getInstallSource()).isEqualTo("MARKET");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(agentHubClient).createAgentInstance(any());
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
}