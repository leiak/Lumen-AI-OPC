package com.ruoyi.opc.billing.controller;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <b>测试专用启动类</b> —— 只给 {@code @WebMvcTest} 切片用。
 *
 * <p>{@code @WebMvcTest} 靠给 {@code @SpringBootApplication} 的组件扫描挂
 * {@code TypeExcludeFilter} 来做到"只加载 controller"。但生产启动类
 * {@code OpcBillingApplication} 上写死了
 * {@code @ComponentScan({"com.ruoyi.opc", "com.ruoyi.system"})} ——
 * 这条<b>自己的</b>扫描不理会 TypeExcludeFilter，于是切片里被拉进了
 * 真实的 mapper / serviceimpl：</p>
 *
 * <pre>
 *   MapperFactoryBean → Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required
 *   ServiceImpl       → 需要 DataSource / JdbcTemplate
 * </pre>
 *
 * <p>而切片里根本没有 DataSource（{@code @WebMvcTest} 不加载数据源自动配置），
 * 结果整个 ApplicationContext 起不来，所有用例报
 * {@code Failed to load ApplicationContext}。</p>
 *
 * <p>本类放在与测试用例<b>同一个包</b>下 —— {@code SpringBootConfigurationFinder}
 * 从测试类所在包逐级向上找 {@code @SpringBootConfiguration}，同包优先，
 * 于是切片用的是本类而不是生产启动类：扫描范围缩到
 * {@code com.ruoyi.opc.billing.controller}，再叠加 {@code @WebMvcTest} 的
 * TypeExcludeFilter，只剩被点名的 controller。</p>
 *
 * <p>本类只在 test 源码树里，不进运行时 classpath，对生产无影响。</p>
 *
 * @author OAC
 */
@SpringBootApplication
public class OpcBillingTestApp
{
}
