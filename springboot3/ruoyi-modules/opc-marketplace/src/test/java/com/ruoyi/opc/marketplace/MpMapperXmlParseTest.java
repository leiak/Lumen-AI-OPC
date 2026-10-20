package com.ruoyi.opc.marketplace;

import com.ruoyi.opc.marketplace.mapper.MpAuthorMapper;
import com.ruoyi.opc.marketplace.mapper.MpCategoryMapper;
import com.ruoyi.opc.marketplace.mapper.MpInstallMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.mapper.MpReviewMapper;
import com.ruoyi.opc.marketplace.mapper.MpTagMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 临时校验（Task 2）：6 个 mapper XML 能被 MyBatis 解析，
 * 且接口每个方法都有对应 statement。
 */
class MpMapperXmlParseTest {

    @Test
    void mapperXmlsParseAndEveryMethodHasStatement() throws Exception {
        Configuration cfg = new Configuration();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:mapper/marketplace/*.xml");
        assertEquals(6, resources.length, "应有 6 个 mapper XML");
        for (Resource r : resources) {
            try (InputStream in = r.getInputStream()) {
                new XMLMapperBuilder(in, cfg, r.toString(), cfg.getSqlFragments()).parse();
            }
        }

        List<Class<?>> mappers = List.of(MpAuthorMapper.class, MpPublicationMapper.class,
                MpInstallMapper.class, MpReviewMapper.class, MpCategoryMapper.class, MpTagMapper.class);
        int methods = 0;
        for (Class<?> mapper : mappers) {
            for (Method m : mapper.getDeclaredMethods()) {
                methods++;
                String id = mapper.getName() + "." + m.getName();
                assertTrue(cfg.hasStatement(id), "缺少 statement: " + id);
            }
        }
        assertEquals(40, methods, "6 mapper 共 40 个方法 (MpAuthorMapper 新增 selectByIds 用于列表页批量填充 authorName)");
    }
}