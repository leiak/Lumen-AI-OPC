package com.ruoyi.common.security.tenant;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.context.SecurityContextHolder;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.system.api.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CompanyIdBodyAdvice} 契约测试 —— 3b
 *
 * <p>请求体那条路上的 companyId 与查询参数是同一个问题：
 * 端点把 body 里的 companyId 直接写库，客户端填谁的就算谁的。</p>
 *
 * @author OAC
 */
class CompanyIdBodyAdviceTest
{
    private final CompanyIdBodyAdvice advice = new CompanyIdBodyAdvice(new TenantGuard(new TenantProperties()));

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    private void loginAs(long userId, Long companyId)
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(userId);
        loginUser.setCompanyId(companyId);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
        SecurityContextHolder.setUserId(String.valueOf(userId));
    }

    private MethodParameter parameter(String method, Class<?> bodyType) throws NoSuchMethodException
    {
        return new MethodParameter(FakeController.class.getMethod(method, bodyType), 0);
    }

    @Test
    @DisplayName("body 里的 companyId 被换成登录租户")
    void overridesBodyCompanyId() throws Exception
    {
        loginAs(42L, 7L);
        ProductDto dto = new ProductDto();
        dto.setCompanyId(999L);

        advice.afterBodyRead(dto, null, parameter("create", ProductDto.class), ProductDto.class,
                MappingJackson2HttpMessageConverter.class);

        assertEquals(7L, dto.getCompanyId(), "body 里的 companyId 由客户端填, 不能算数");
    }

    @Test
    @DisplayName("body 里没填 companyId → 补上登录租户")
    void fillsBodyCompanyId() throws Exception
    {
        loginAs(42L, 7L);
        ProductDto dto = new ProductDto();

        advice.afterBodyRead(dto, null, parameter("create", ProductDto.class), ProductDto.class,
                MappingJackson2HttpMessageConverter.class);

        assertEquals(7L, dto.getCompanyId());
    }

    @Test
    @DisplayName("匿名 → body 里的 companyId 换成公开租户")
    void anonymousUsesPublicCompany() throws Exception
    {
        ProductDto dto = new ProductDto();
        dto.setCompanyId(2L);

        advice.afterBodyRead(dto, null, parameter("create", ProductDto.class), ProductDto.class,
                MappingJackson2HttpMessageConverter.class);

        assertEquals(1L, dto.getCompanyId());
    }

    @Test
    @DisplayName("没有 companyId 字段的 DTO → 不处理")
    void ignoresDtoWithoutCompanyId() throws Exception
    {
        MethodParameter parameter = parameter("create", PlainDto.class);

        assertFalse(advice.supports(parameter, PlainDto.class, MappingJackson2HttpMessageConverter.class));
    }

    @Test
    @DisplayName("带 companyId 的 DTO → 处理")
    void supportsDtoWithCompanyId() throws Exception
    {
        MethodParameter parameter = parameter("create", ProductDto.class);

        assertTrue(advice.supports(parameter, ProductDto.class, MappingJackson2HttpMessageConverter.class));
    }

    @Test
    @DisplayName("@InnerAuth 端点 → 不改写")
    void innerAuthIsExempt() throws Exception
    {
        MethodParameter parameter = parameter("createInner", ProductDto.class);

        assertFalse(advice.supports(parameter, ProductDto.class, MappingJackson2HttpMessageConverter.class));
    }

    @Test
    @DisplayName("company_id 写法(下划线)同样认")
    void supportsSnakeCaseField() throws Exception
    {
        MethodParameter parameter = parameter("create", SnakeDto.class);
        assertTrue(advice.supports(parameter, SnakeDto.class, MappingJackson2HttpMessageConverter.class));

        loginAs(42L, 7L);
        SnakeDto dto = new SnakeDto();
        dto.setCompanyId(2L);
        advice.afterBodyRead(dto, null, parameter, SnakeDto.class, MappingJackson2HttpMessageConverter.class);

        assertEquals(7L, dto.getCompanyId());
    }

    @Test
    @DisplayName("body 为 null 时不炸")
    void toleratesNullBody() throws Exception
    {
        loginAs(42L, 7L);

        assertNull(advice.afterBodyRead(null, null, parameter("create", ProductDto.class), ProductDto.class,
                MappingJackson2HttpMessageConverter.class));
    }

    /** 假 DTO —— 模拟 OpcErpProductDto 那类带 companyId 的请求体。 */
    public static class ProductDto
    {
        private Long companyId;
        private String name;

        public Long getCompanyId()
        {
            return companyId;
        }

        public void setCompanyId(Long companyId)
        {
            this.companyId = companyId;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }
    }

    /** 下划线写法的 DTO。 */
    public static class SnakeDto
    {
        private Long company_id;

        public Long getCompanyId()
        {
            return company_id;
        }

        public void setCompanyId(Long companyId)
        {
            this.company_id = companyId;
        }
    }

    /** 没有 companyId 的 DTO。 */
    public static class PlainDto
    {
        private String name;

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }
    }

    /** 假控制器 —— 只为拿到 MethodParameter 与 @InnerAuth 标注。 */
    static class FakeController
    {
        public void create(ProductDto dto)
        {
        }

        public void create(PlainDto dto)
        {
        }

        public void create(SnakeDto dto)
        {
        }

        @InnerAuth
        public void createInner(ProductDto dto)
        {
        }
    }
}
