package com.ruoyi.opc.marketplace;

import com.ruoyi.opc.marketplace.mapper.CommunityModuleMapper;
import com.ruoyi.opc.marketplace.mapper.MpAuthorMapper;
import com.ruoyi.opc.marketplace.mapper.MpCategoryMapper;
import com.ruoyi.opc.marketplace.mapper.MpInstallMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.mapper.MpReviewMapper;
import com.ruoyi.opc.marketplace.mapper.MpSubscriptionMapper;
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
 * 临时校验（Task 2）：8 个 mapper XML 能被 MyBatis 解析，
 * 且接口每个方法都有对应 statement。
 */
class MpMapperXmlParseTest {

    @Test
    void mapperXmlsParseAndEveryMethodHasStatement() throws Exception {
        Configuration cfg = new Configuration();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:mapper/marketplace/*.xml");
        assertEquals(8, resources.length, "应有 8 个 mapper XML");
        for (Resource r : resources) {
            try (InputStream in = r.getInputStream()) {
                new XMLMapperBuilder(in, cfg, r.toString(), cfg.getSqlFragments()).parse();
            }
        }

        List<Class<?>> mappers = List.of(MpAuthorMapper.class, MpPublicationMapper.class,
                MpInstallMapper.class, MpReviewMapper.class, MpCategoryMapper.class, MpTagMapper.class,
                CommunityModuleMapper.class, MpSubscriptionMapper.class);
        int methods = 0;
        for (Class<?> mapper : mappers) {
            for (Method m : mapper.getDeclaredMethods()) {
                methods++;
                String id = mapper.getName() + "." + m.getName();
                assertTrue(cfg.hasStatement(id), "缺少 statement: " + id);
            }
        }
        assertEquals(53, methods, "8 mapper 共 53 个方法 "
                + "(W80 P2 新增 CommunityModuleMapper 2 个只读方法 + MpSubscriptionMapper 7 个方法; "
                + "另加 MpPublicationMapper.decrementSubscriptionCount 与 "
                + "MpAuthorMapper.refreshTotalSubscribers —— 42 + 2 + 7 + 1 + 1)");
    }
}