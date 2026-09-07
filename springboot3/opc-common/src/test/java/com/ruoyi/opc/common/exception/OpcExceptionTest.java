package com.ruoyi.opc.common.exception;

import com.ruoyi.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OpcException} 单元测试 — W7
 *
 * <p>W7 修复验证：{@code OpcException} 现在继承 {@link ServiceException}，
 * 测试覆盖以下契约：
 * <ul>
 *   <li>继承链：{@code OpcException instanceof ServiceException} 为 true（advice 路由正确）</li>
 *   <li>{@link ServiceException#getMessage()} 重写：super(message) 必须传递到父类字段</li>
 *   <li>{@link ServiceException#getCode()} 与 Lombok 生成方法兼容：子类 int 字段覆盖父类 Integer 字段</li>
 *   <li>errorLevel 字段保持 OPC 自定义语义</li>
 *   <li>{@code instanceof RuntimeException}（不破坏现有调用栈）</li>
 * </ul>
 *
 * @author OAC
 */
class OpcExceptionTest {

    @Test
    @DisplayName("OpcException 是 ServiceException 子类（handleServiceException 能接住）")
    void opcException_isInstanceOfServiceException() {
        OpcException ex = new OpcException("foo");

        assertTrue(ex instanceof ServiceException,
                "OpcException 必须继承 ServiceException，advice 才能按 code 字段路由");
        assertTrue(ex instanceof RuntimeException,
                "OpcException 仍应间接继承 RuntimeException（不破坏调用栈）");
    }

    @Test
    @DisplayName("OpcException(String) — code 默认 500 + errorLevel 默认 ERROR + msg 正确")
    void singleArgConstructor_defaultsCode500ErrorLevelError() {
        OpcException ex = new OpcException("余额不足");

        assertEquals("余额不足", ex.getMessage());
        assertEquals(500, ex.getCode());
        assertEquals("ERROR", ex.getErrorLevel());
    }

    @Test
    @DisplayName("OpcException(int, String) — 自定义 code + msg + errorLevel 默认 ERROR")
    void twoArgConstructor_respectsCodeAndDefaultsErrorLevel() {
        OpcException ex = new OpcException(400, "凭证已存在");

        assertEquals("凭证已存在", ex.getMessage());
        assertEquals(400, ex.getCode(),
                "W7 修复：code 字段必须能被 getCode() 正确读出（不再被父类 Integer code 阴影）");
        assertEquals("ERROR", ex.getErrorLevel());
    }

    @Test
    @DisplayName("OpcException(int, String, String) — 三个参数都生效")
    void threeArgConstructor_allFieldsApplied() {
        OpcException ex = new OpcException(403, "Prompt 注入检测", "SECURITY");

        assertEquals("Prompt 注入检测", ex.getMessage());
        assertEquals(403, ex.getCode());
        assertEquals("SECURITY", ex.getErrorLevel());
    }

    @Test
    @DisplayName("getMessage() 走父类 ServiceException 的 message 字段（不是 Throwable.detailMessage）")
    void getMessage_usesServiceExceptionMessageField() {
        // 这是 W7 修复的关键点：ServiceException 重写 getMessage() 返回自己的 message 字段
        // OpcException 子类 super(message) 必须调用 ServiceException(String) ctor 正确填充该字段
        // 否则 getMessage() 会返回 null（父类 message 字段未初始化）
        OpcException ex = new OpcException(400, "msg字段测试");

        assertEquals("msg字段测试", ex.getMessage(),
                "必须通过 ServiceException(String) 构造器传递 message");
        // 再确认 Throwable.getLocalizedMessage() 也走父类字段
        assertEquals("msg字段测试", ex.getLocalizedMessage());
    }

    @Test
    @DisplayName("Throwable 链仍能工作（printStackTrace / getStackTrace 不破坏）")
    void throwableChainStillWorks() {
        OpcException ex = new OpcException("test");
        StackTraceElement[] trace = ex.getStackTrace();

        assertNotNull(trace);
        assertTrue(trace.length > 0, "stack trace 应非空");
        assertTrue(trace[0].getClassName().contains("OpcExceptionTest"),
                "stack trace 起点应是测试类");
    }

    @Test
    @DisplayName("ServiceException.getCode() 返回 Integer；OpcException.getCode() 返回 int — 签名兼容")
    void getCodeReturnTypeCompatibility() throws NoSuchMethodException {
        // 编译期检查：ServiceException.getCode() 返回 Integer.class
        // Lombok 生成的 OpcException.getCode() 返回 int.class
        // JVM 层：int 与 Integer 方法描述符不同，但子类可以重写父类方法返回更窄类型（协变返回）
        java.lang.reflect.Method parentMethod = ServiceException.class.getMethod("getCode");
        java.lang.reflect.Method childMethod = OpcException.class.getMethod("getCode");

        assertEquals(Integer.class, parentMethod.getReturnType(),
                "ServiceException.getCode() 应返回 Integer（父类字段）");
        assertEquals(int.class, childMethod.getReturnType(),
                "OpcException.getCode() 应返回 int（Lombok @Getter on int field）");
        assertTrue(java.lang.reflect.Modifier.isPublic(childMethod.getModifiers()),
                "Lombok @Getter 应生成 public 方法");
    }

    @Test
    @DisplayName("OpcException(403, ..., \"SECURITY\") — PromptGuard 用的 3-arg 构造器")
    void threeArgConstructorForPromptGuardUseCase() {
        // PromptGuard.java:116/122/144 实际使用 3-arg 构造器
        OpcException ex = new OpcException(403, "检测到潜在的 Prompt 注入", "SECURITY");

        // 验证它能被 @ExceptionHandler(ServiceException.class) 接住
        assertTrue(ex instanceof ServiceException);
        assertEquals(403, ex.getCode());
        assertEquals("SECURITY", ex.getErrorLevel());
    }
}