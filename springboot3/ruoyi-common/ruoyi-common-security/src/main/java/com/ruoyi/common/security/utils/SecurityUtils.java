package com.ruoyi.common.security.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.constant.TokenConstants;
import com.ruoyi.common.core.constant.UserConstants;
import com.ruoyi.common.core.context.SecurityContextHolder;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.ServletUtils;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.system.api.model.LoginUser;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 权限获取工具类
 * 
 * @author ruoyi
 */
public class SecurityUtils
{
    /**
     * 获取用户ID
     */
    public static Long getUserId()
    {
        return SecurityContextHolder.getUserId();
    }

    /**
     * 获取用户名称
     */
    public static String getUsername()
    {
        return SecurityContextHolder.getUserName();
    }

    /**
     * 获取用户key
     */
    public static String getUserKey()
    {
        return SecurityContextHolder.getUserKey();
    }

    /**
     * 获取登录用户信息
     */
    public static LoginUser getLoginUser()
    {
        return SecurityContextHolder.get(SecurityConstants.LOGIN_USER, LoginUser.class);
    }

    /**
     * 获取当前登录用户所属公司ID（租户ID）。
     *
     * <p><b>可能返回 null</b>：用户尚未加入任何公司时就是 null。
     * 调用方必须显式处理无租户场景，<b>不要</b>用 0 或其他默认值兜底 ——
     * 把「无公司」当成「公司 0」继续查库会造成越权或脏数据。</p>
     *
     * <p>与 {@link #getUserId()} 不同：{@code getUserId()} 在无登录上下文时返回 null，
     * 本方法在<b>完全没有登录上下文</b>（未认证请求、定时任务、MQ 消费等）时
     * 直接抛 {@link ServiceException}，避免业务代码在无身份的场景下继续执行。</p>
     *
     * @return 当前用户所属公司ID，未加入任何公司时为 null
     * @throws ServiceException 当前线程没有登录上下文时抛出
     */
    public static Long getCompanyId()
    {
        LoginUser loginUser = getLoginUser();
        if (StringUtils.isNull(loginUser))
        {
            throw new ServiceException("获取公司ID失败：当前请求没有登录上下文");
        }
        return loginUser.getCompanyId();
    }

    /**
     * 获取请求token
     */
    public static String getToken()
    {
        return getToken(ServletUtils.getRequest());
    }

    /**
     * 根据request获取请求token
     */
    public static String getToken(HttpServletRequest request)
    {
        // 从header获取token标识
        String token = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);
        return replaceTokenPrefix(token);
    }

    /**
     * 裁剪token前缀
     */
    public static String replaceTokenPrefix(String token)
    {
        // 如果前端设置了令牌前缀，则裁剪掉前缀
        if (StringUtils.isNotEmpty(token) && token.startsWith(TokenConstants.PREFIX))
        {
            token = token.replaceFirst(TokenConstants.PREFIX, "");
        }
        return token;
    }

    /**
     * 是否为管理员
     * 
     * @return 结果
     */
    public static boolean isAdmin()
    {
        return isAdmin(getUserId());
    }

    /**
     * 是否为管理员
     * 
     * @param userId 用户ID
     * @return 结果
     */
    public static boolean isAdmin(Long userId)
    {
        return UserConstants.isAdmin(userId);
    }

    /**
     * 生成BCryptPasswordEncoder密码
     *
     * @param password 密码
     * @return 加密字符串
     */
    public static String encryptPassword(String password)
    {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder.encode(password);
    }

    /**
     * 判断密码是否相同
     *
     * @param rawPassword 真实密码
     * @param encodedPassword 加密后字符
     * @return 结果
     */
    public static boolean matchesPassword(String rawPassword, String encodedPassword)
    {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
