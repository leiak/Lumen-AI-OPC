package com.ruoyi.system.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SysUserCompanyMapper} 的 mapper XML 解析测试 — 安全关键链路的最小防回归网
 *
 * <p><b>为什么 ruoyi-system 这个模块要开第一个测试</b>：本模块此前没有任何
 * {@code src/test}。而 {@code SysUserCompanyMapper.xml}（commit a943721）位于
 * <b>「登录时解析每个用户的租户身份」</b>这条链路上 ——
 * {@code SysUserController#info} → {@code UserCompanyResolver} → 本 mapper →
 * {@code LoginUser.companyId}，全平台每个 {@code SecurityUtils.getCompanyId()}
 * 的取值都源于它。
 *
 * <p><b>它防的是什么</b>：W48 的经典故障 —— mapper XML 能被解析，但 statement id
 * 与接口方法对不上，运行期才抛 {@code Invalid bound statement (not found)}。
 * 那种故障在这里的表现是<b>整个平台登录后拿不到租户身份</b>，
 * 所有 {@code SecurityUtils.getCompanyId()} 全返回 null，所有租户维度端点 400。
 * 这类故障在 code review 时肉眼几乎看不出来，只有测试能稳定抓住。
 *
 * <p>刻意<b>不做</b>更广的覆盖：ruoyi-system 没有测试文化，
 * 给它铺一整套单测超出本次范围。这里只守住这一个 mapper。
 *
 * <p>手法抄自 {@code opc-marketplace} 的 {@code MpMapperXmlParseTest}
 * （同仓库、同目的、已存在的先例）。
 *
 * @author OAC
 */
class SysUserCompanyMapperXmlTest {

    @Test
    @DisplayName("SysUserCompanyMapper.xml 能被 MyBatis 解析,且接口每个方法都有对应 statement")
    void mapperXmlParsesAndEveryMethodHasStatement() throws Exception {
        Configuration cfg = new Configuration();
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/system/SysUserCompanyMapper.xml");
        assertEquals(1, resources.length,
                "应恰好命中 1 个 mapper XML（重命名或移动文件会让本测试失效，请同步更新）");

        for (Resource r : resources) {
            try (InputStream in = r.getInputStream()) {
                new XMLMapperBuilder(in, cfg, r.toString(), cfg.getSqlFragments()).parse();
            }
        }

        int methods = 0;
        for (Method m : SysUserCompanyMapper.class.getDeclaredMethods()) {
            methods++;
            String id = SysUserCompanyMapper.class.getName() + "." + m.getName();
            assertTrue(cfg.hasStatement(id),
                    "mapper XML 缺少 statement: " + id
                            + " —— 运行期会抛 Invalid bound statement，登录时租户解析将整体失效");
        }

        // 方法数是刻意钉死的：新增接口方法忘了写 XML 时这里会先红，提示补 statement
        assertEquals(1, methods,
                "SysUserCompanyMapper 方法数变了。当前: " + Arrays.toString(
                        SysUserCompanyMapper.class.getDeclaredMethods()));
    }
}