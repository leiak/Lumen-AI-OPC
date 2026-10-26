package com.ruoyi.common.security.tenant;

import com.ruoyi.common.security.annotation.InnerAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

/**
 * 请求体 companyId 改写 —— 3b
 *
 * <p>与 {@link TenantCompanyFilter} 处理查询参数是同一件事的两面：
 * 端点也可以把 companyId 放在请求体 DTO 里（{@code OpcErpProductDto}、
 * {@code OpcContentScriptDto}、{@code OpcContentGenerateRequest} ……），
 * 那条路上同样不能让客户端决定租户。</p>
 *
 * <p>Spring 的 {@code RequestResponseBodyMethodProcessor} 会在消息转换器把 body
 * 反序列化完之后回调 {@code RequestBodyAdvice#afterBodyRead}，
 * 此时 DTO 已经是普通对象，直接改字段即可 —— 比在参数解析阶段动手可靠得多
 * （body 里根本没有「参数」这回事）。</p>
 *
 * @author OAC
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class CompanyIdBodyAdvice implements RequestBodyAdvice
{
    private static final String[] COMPANY_ID_FIELDS = { "companyId", "company_id" };

    private final TenantGuard tenantGuard;

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType)
    {
        // @InnerAuth 端点的 companyId 由调用方显式指定, 不改写。
        if (methodParameter != null && methodParameter.getMethodAnnotation(InnerAuth.class) != null)
        {
            return false;
        }
        return findCompanyIdField(targetType) != null;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
                                           Type targetType, Class<? extends HttpMessageConverter<?>> converterType)
    {
        return inputMessage;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                  Type targetType, Class<? extends HttpMessageConverter<?>> converterType)
    {
        return body;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType)
    {
        if (body == null)
        {
            return body;
        }
        Field field = findCompanyIdField(targetType);
        if (field == null)
        {
            return body;
        }
        try
        {
            Long requested = readCompanyId(field, body);
            Long effective = tenantGuard.resolveAllowed(requested);
            if (effective != null)
            {
                writeCompanyId(field, body, effective);
            }
        }
        catch (IllegalAccessException e)
        {
            // 改写失败不该让请求挂掉 —— 但必须留痕, 否则等于没有守卫。
            log.warn("[租户守卫] 请求体 companyId 改写失败 ({}): {}", field.getName(), e.getMessage());
        }
        return body;
    }

    /**
     * 找出 DTO 里的 companyId 字段（含 {@code company_id} 写法与父类字段）。
     */
    private Field findCompanyIdField(Type targetType)
    {
        Class<?> raw = resolveRawType(targetType);
        if (raw == null)
        {
            return null;
        }
        for (Class<?> clazz = raw; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass())
        {
            for (String name : COMPANY_ID_FIELDS)
            {
                try
                {
                    Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                }
                catch (NoSuchFieldException e)
                {
                    // 继续找下一个名字 / 父类
                }
                catch (SecurityException e)
                {
                    return null;
                }
            }
        }
        return null;
    }

    private Class<?> resolveRawType(Type targetType)
    {
        if (targetType instanceof Class<?> clazz)
        {
            return clazz;
        }
        if (targetType instanceof ResolvableType resolvable)
        {
            return resolvable.resolve(Object.class);
        }
        if (targetType instanceof java.lang.reflect.ParameterizedType parameterized)
        {
            return resolveRawType(parameterized.getRawType());
        }
        return null;
    }

    private Long readCompanyId(Field field, Object body) throws IllegalAccessException
    {
        Object value = field.get(body);
        if (value == null)
        {
            return null;
        }
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank())
        {
            try
            {
                return Long.valueOf(text.trim());
            }
            catch (NumberFormatException e)
            {
                // 非数字的公司号 —— 当作"没给", 由服务端补
                return null;
            }
        }
        return null;
    }

    private void writeCompanyId(Field field, Object body, Long effective) throws IllegalAccessException
    {
        Class<?> type = field.getType();
        if (type == Long.class || type == long.class)
        {
            field.set(body, effective);
        }
        else if (type == Integer.class || type == int.class)
        {
            field.set(body, effective.intValue());
        }
        else if (type == String.class)
        {
            field.set(body, String.valueOf(effective));
        }
        else
        {
            log.debug("[租户守卫] companyId 字段类型 {} 不支持改写, 跳过", type.getName());
        }
    }
}
