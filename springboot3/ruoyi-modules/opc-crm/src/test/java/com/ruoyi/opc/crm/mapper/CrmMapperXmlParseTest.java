package com.ruoyi.opc.crm.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class CrmMapperXmlParseTest {

    private static final String[] MAPPERS = {
        "CrmCustomerMapper", "CrmContactMapper", "CrmFollowUpMapper",
        "CrmOpportunityMapper", "CrmContractMapper", "CrmOrderMapper"
    };

    @Test
    void all_mapper_xmls_parse_and_bind_to_interfaces() throws Exception {
        Configuration cfg = new Configuration();
        for (String m : MAPPERS) {
            String res = "mapper/" + m + ".xml";
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(res)) {
                assertThat(in).as(res).isNotNull();
                new XMLMapperBuilder(in, cfg, res, cfg.getSqlFragments()).parse();
            }
        }
        // every interface method must have a bound statement
        for (String m : MAPPERS) {
            Class<?> iface = Class.forName("com.ruoyi.opc.crm.mapper." + m);
            for (java.lang.reflect.Method method : iface.getDeclaredMethods()) {
                String id = iface.getName() + "." + method.getName();
                assertThat(cfg.hasStatement(id)).as(id).isTrue();
            }
        }
    }
}
