package com.ruoyi.common.security.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.context.SecurityContextHolder;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.system.api.model.LoginUser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SecurityUtils#getCompanyId()} 契约测试
 *
 * <p>两条不可让步的契约：
 * <ol>
 *   <li><b>无登录上下文 → 抛 ServiceException</b>（不能静默返回 null，
 *       否则定时任务 / MQ 消费等无身份场景会带着"匿名租户"继续跑业务）；</li>
 *   <li><b>有登录上下文但没公司 → 返回 null</b>（不能返回 0，
 *       把"无公司"当成"公司 0"去查库等于越权）。</li>
 * </ol>
 *
 * @author ruoyi
 */
class SecurityUtilsCompanyTest
{
    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.remove();
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("无登录上下文 → 抛 ServiceException（而不是返回 null）")
    void noLoginContext_throwsServiceException()
    {
        assertThrows(ServiceException.class, SecurityUtils::getCompanyId,
                "没有登录上下文时必须抛异常，避免匿名场景带着空租户执行业务");
    }

    @Test
    @DisplayName("有登录上下文但未加入任何公司 → 返回 null（不是 0）")
    void loginUserWithoutCompany_returnsNull()
    {
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, new LoginUser());

        assertNull(SecurityUtils.getCompanyId(),
                "用户未加入任何公司时必须返回 null；返回 0 会把'无租户'伪装成'公司0'");
    }

    @Test
    @DisplayName("有登录上下文且已解析出公司 → 返回该公司ID")
    void loginUserWithCompany_returnsCompanyId()
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setCompanyId(9527L);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);

        assertEquals(9527L, SecurityUtils.getCompanyId());
    }

    @Test
    @DisplayName("companyId=0 也照常返回，不做兜底改写")
    void companyIdZero_isReturnedAsIs()
    {
        // 防回归护栏：解析器若哪天错误地返回 0，本测试会提醒这里不做静默改写，
        // 让问题暴露在写入侧而不是被读取侧悄悄吞掉。
        LoginUser loginUser = new LoginUser();
        loginUser.setCompanyId(0L);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);

        assertEquals(0L, SecurityUtils.getCompanyId());
    }

    @Test
    @DisplayName("getCompanyId() 与 getUserId() 互不干扰")
    void companyIdDoesNotAffectUserId()
    {
        SecurityContextHolder.setUserId("1001");
        LoginUser loginUser = new LoginUser();
        loginUser.setCompanyId(9527L);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);

        assertEquals(1001L, SecurityUtils.getUserId());
        assertEquals(9527L, SecurityUtils.getCompanyId());
    }
}