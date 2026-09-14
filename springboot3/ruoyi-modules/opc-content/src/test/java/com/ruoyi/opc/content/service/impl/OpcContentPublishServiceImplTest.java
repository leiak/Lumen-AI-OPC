package com.ruoyi.opc.content.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.content.domain.OpcContentPlatformAccount;
import com.ruoyi.opc.content.domain.OpcContentPublish;
import com.ruoyi.opc.content.domain.OpcContentScript;
import com.ruoyi.opc.content.dto.OpcContentPublishRequest;
import com.ruoyi.opc.content.enums.ContentPlatform;
import com.ruoyi.opc.content.enums.ContentPublishStatus;
import com.ruoyi.opc.content.enums.ContentScriptStatus;
import com.ruoyi.opc.content.enums.ContentScriptType;
import com.ruoyi.opc.content.mapper.OpcContentPublishMapper;
import com.ruoyi.opc.content.mapper.OpcContentScriptMapper;
import com.ruoyi.opc.content.service.IOpcContentPlatformAccountService;
import com.ruoyi.opc.content.service.IOpcContentScriptService;
import com.ruoyi.opc.content.service.platform.PlatformClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpcContentPublishServiceImpl 单测 (W74 Task 9 — 12 cases)。
 *
 * <p>覆盖: publish 守卫 + listByCompany + detail + retry (FAILED→PENDING 状态机) + 错误处理。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcContentPublishServiceImpl 单测 (12 cases)")
class OpcContentPublishServiceImplTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long SCRIPT_ID = 100L;
    private static final Long ACCOUNT_ID = 200L;
    private static final Long PUBLISH_ID = 300L;

    @Mock
    private OpcContentPublishMapper publishMapper;

    @Mock
    private OpcContentScriptMapper scriptMapper;

    @Mock
    private IOpcContentScriptService scriptService;

    @Mock
    private IOpcContentPlatformAccountService accountService;

    @Mock
    private PlatformClient platformClient;

    @InjectMocks
    private OpcContentPublishServiceImpl publishService;

    private OpcContentPublishRequest sampleReq() {
        return OpcContentPublishRequest.builder()
                .companyId(COMPANY_ID)
                .scriptId(SCRIPT_ID)
                .platformAccountId(ACCOUNT_ID)
                .title("测试发布标题")
                .tags(new String[]{"#标签1", "#标签2"})
                .build();
    }

    private OpcContentScript readyScript() {
        return OpcContentScript.builder()
                .id(SCRIPT_ID).companyId(COMPANY_ID)
                .type(ContentScriptType.VIDEO.getCode())
                .status(ContentScriptStatus.READY.getCode())
                .title("脚本").promptInput("p").build();
    }

    private OpcContentPlatformAccount activeAccount() {
        return OpcContentPlatformAccount.builder()
                .id(ACCOUNT_ID).companyId(COMPANY_ID)
                .platform(ContentPlatform.DOUYIN.getCode())
                .status("ACTIVE").openId("open_1").build();
    }

    /** Test 1 */
    @Test
    @DisplayName("publish - READY 脚本 + ACTIVE 账号 → SUCCESS,script 转 PUBLISHED")
    void publish_readyScript_success() {
        OpcContentScript script = readyScript();
        OpcContentPlatformAccount account = activeAccount();
        when(scriptService.detail(SCRIPT_ID, COMPANY_ID)).thenReturn(script);
        when(accountService.detail(ACCOUNT_ID, COMPANY_ID)).thenReturn(account);
        when(platformClient.platformName()).thenReturn("DOUYIN");
        when(platformClient.uploadVideo(any(), any(), any())).thenReturn("video_123");
        when(platformClient.createVideo(any(), eq("video_123"), eq("测试发布标题"), any()))
                .thenReturn(new PlatformClient.PublishResult("post_456", "https://mock/post_456"));

        Long id = publishService.publish(sampleReq());

        assertThat(id).isNotNull().isPositive();
        ArgumentCaptor<OpcContentPublish> captor = ArgumentCaptor.forClass(OpcContentPublish.class);
        verify(publishMapper).insert(captor.capture());
        OpcContentPublish saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ContentPublishStatus.SUCCESS.getCode());
        assertThat(saved.getExternalVideoId()).isEqualTo("video_123");
        assertThat(saved.getExternalPostId()).isEqualTo("post_456");
        assertThat(saved.getTags()).contains("标签1");

        // script 状态持久化为 PUBLISHED
        verify(scriptMapper).updateById(any(OpcContentScript.class));
    }

    /** Test 2 */
    @Test
    @DisplayName("publish - script.status=DRAFT 拒绝")
    void publish_draftScript_rejected() {
        OpcContentScript script = readyScript();
        script.setStatus(ContentScriptStatus.DRAFT.getCode());
        when(scriptService.detail(SCRIPT_ID, COMPANY_ID)).thenReturn(script);

        assertThatThrownBy(() -> publishService.publish(sampleReq()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("READY");
        verify(publishMapper, never()).insert(any(OpcContentPublish.class));
    }

    /** Test 3 */
    @Test
    @DisplayName("publish - 账号平台与渠道不匹配拒绝")
    void publish_platformMismatch_rejected() {
        OpcContentScript script = readyScript();
        OpcContentPlatformAccount account = activeAccount();
        account.setPlatform("WECHAT"); // 不匹配
        when(scriptService.detail(SCRIPT_ID, COMPANY_ID)).thenReturn(script);
        when(accountService.detail(ACCOUNT_ID, COMPANY_ID)).thenReturn(account);
        when(platformClient.platformName()).thenReturn("DOUYIN");

        assertThatThrownBy(() -> publishService.publish(sampleReq()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("平台账号与发布渠道不匹配");
        verify(publishMapper, never()).insert(any(OpcContentPublish.class));
    }

    /** Test 4 */
    @Test
    @DisplayName("publish - 缺 companyId 抛 ServiceException")
    void publish_blankCompanyId_throws() {
        OpcContentPublishRequest req = sampleReq();
        req.setCompanyId(null);

        assertThatThrownBy(() -> publishService.publish(req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("companyId");
    }

    /** Test 5 */
    @Test
    @DisplayName("publish - 缺 title 抛 ServiceException")
    void publish_blankTitle_throws() {
        OpcContentPublishRequest req = sampleReq();
        req.setTitle("");

        assertThatThrownBy(() -> publishService.publish(req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("title");
    }

    /** Test 6 */
    @Test
    @DisplayName("publish - tags=null 序列化 []")
    void publish_nullTags_serializedAsEmptyArray() {
        OpcContentScript script = readyScript();
        OpcContentPlatformAccount account = activeAccount();
        when(scriptService.detail(SCRIPT_ID, COMPANY_ID)).thenReturn(script);
        when(accountService.detail(ACCOUNT_ID, COMPANY_ID)).thenReturn(account);
        when(platformClient.platformName()).thenReturn("DOUYIN");
        when(platformClient.uploadVideo(any(), any(), any())).thenReturn("v");
        when(platformClient.createVideo(any(), any(), any(), any()))
                .thenReturn(new PlatformClient.PublishResult("p", "u"));

        OpcContentPublishRequest req = sampleReq();
        req.setTags(null);
        publishService.publish(req);

        ArgumentCaptor<OpcContentPublish> captor = ArgumentCaptor.forClass(OpcContentPublish.class);
        verify(publishMapper).insert(captor.capture());
        assertThat(captor.getValue().getTags()).isEqualTo("[]");
    }

    /** Test 7 */
    @Test
    @DisplayName("listByCompany - 默认 page/size 兜底,total 与 rows 返回")
    void listByCompany_defaultsPagination() {
        OpcContentPublish row = OpcContentPublish.builder().id(PUBLISH_ID).status("SUCCESS").build();
        when(publishMapper.selectList(COMPANY_ID, "SUCCESS", 0, 20)).thenReturn(List.of(row));
        when(publishMapper.countList(COMPANY_ID, "SUCCESS")).thenReturn(1);

        var resp = publishService.listByCompany(COMPANY_ID, "SUCCESS", 0, 0);

        assertThat(resp.getRows()).hasSize(1);
        assertThat(resp.getTotal()).isEqualTo(1);
        verify(publishMapper).selectList(COMPANY_ID, "SUCCESS", 0, 20);
    }

    /** Test 8 */
    @Test
    @DisplayName("listByCompany - 缺 companyId 抛 ServiceException")
    void listByCompany_blankCompanyId_throws() {
        assertThatThrownBy(() -> publishService.listByCompany(null, "SUCCESS", 1, 10))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("companyId");
    }

    /** Test 9 */
    @Test
    @DisplayName("detail - 返回单条记录")
    void detail_returnsRecord() {
        OpcContentPublish row = OpcContentPublish.builder().id(PUBLISH_ID).status("SUCCESS").build();
        when(publishMapper.selectById(PUBLISH_ID, COMPANY_ID)).thenReturn(row);

        OpcContentPublish result = publishService.detail(PUBLISH_ID, COMPANY_ID);

        assertThat(result.getId()).isEqualTo(PUBLISH_ID);
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
    }

    /** Test 10 */
    @Test
    @DisplayName("detail - 记录不存在抛 ServiceException")
    void detail_notFound_throws() {
        when(publishMapper.selectById(PUBLISH_ID, COMPANY_ID)).thenReturn(null);

        assertThatThrownBy(() -> publishService.detail(PUBLISH_ID, COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不存在");
    }

    /** Test 11 */
    @Test
    @DisplayName("retry - FAILED → PENDING 合法转换")
    void retry_failedToPending_success() {
        OpcContentPublish existing = OpcContentPublish.builder()
                .id(PUBLISH_ID).companyId(COMPANY_ID)
                .status(ContentPublishStatus.FAILED.getCode())
                .errorCode("E001").errorMessage("Oops").build();
        when(publishMapper.selectById(PUBLISH_ID, COMPANY_ID)).thenReturn(existing);
        when(publishMapper.updateById(any(OpcContentPublish.class))).thenReturn(1);

        publishService.retry(PUBLISH_ID, COMPANY_ID);

        ArgumentCaptor<OpcContentPublish> captor = ArgumentCaptor.forClass(OpcContentPublish.class);
        verify(publishMapper).updateById(captor.capture());
        OpcContentPublish updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo(ContentPublishStatus.PENDING.getCode());
        assertThat(updated.getErrorCode()).isNull();
        assertThat(updated.getErrorMessage()).isNull();
    }

    /** Test 12 */
    @Test
    @DisplayName("retry - SUCCESS 状态不允许重试")
    void retry_successStatus_rejected() {
        OpcContentPublish existing = OpcContentPublish.builder()
                .id(PUBLISH_ID).companyId(COMPANY_ID)
                .status(ContentPublishStatus.SUCCESS.getCode()).build();
        when(publishMapper.selectById(PUBLISH_ID, COMPANY_ID)).thenReturn(existing);

        assertThatThrownBy(() -> publishService.retry(PUBLISH_ID, COMPANY_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("非法");
        verify(publishMapper, never()).updateById(any(OpcContentPublish.class));
    }
}