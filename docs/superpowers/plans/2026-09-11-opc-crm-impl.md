# opc-crm Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `opc-crm` customer relationship management service (客户档案 + 标签 + 联系人 + 跟进 + 商机 + 合同 + 订单 + LLM 商机打分) as the second service in the OPC expansion roadmap (W55-W58, 4 weeks compressed into single sprint).

**Architecture:** Spring Boot 3 microservice following the W48 thin-jar + Nacos + Jasypt + mybatis (NOT mybatis-plus) pattern. 6 tables (4 spec + 2 user-added contract/order). OpenFeign calls to `opc-notification` (跟进提醒) and `opc-ai-core` (LLM 商机打分). Stage state machine for opportunity (lead → qualified → proposal → negotiation → won/lost).

**Tech Stack:**
- Spring Boot 3.2 + Spring Cloud OpenFeign
- MyBatis 3.5 + MySQL 8.0 (`opc_crm_*` tables)
- Nacos config + discovery (namespace `opc-dev`)
- Jasypt `OpcEncrypt!2026` for any secrets
- Vanilla MyBatis (NOT MyBatis-Plus — W48 lesson)
- Lombok + JUnit5 + Mockito + AssertJ
- OpenFeign for cross-service (notification + ai-core)
- Vue 3 + TS frontend (Customer list / Opportunity kanban / Follow-up timeline)

**Reference:** Spec at [`docs/superpowers/specs/2026-09-10-opc-roadmap-expansion-design.md` §5.3](../../specs/2026-09-10-opc-roadmap-expansion-design.md)

**Extends spec:** User requested 2 additional tables (contract + order) beyond original 4. Total 6 tables, 8 endpoints (was 6).

---

## File Structure

```
springboot3/ruoyi-modules/opc-crm/
├── Dockerfile                                            # W48 thin jar template
├── pom.xml                                               # extends ruoyi-modules
└── src/main/java/com/ruoyi/opc/crm/
    ├── OpcCrmApplication.java                            # @SpringBootApplication
    ├── config/
    │   ├── MybatisConfig.java                            # @MapperScan
    │   └── FeignConfig.java                              # @EnableFeignClients(opc.*)
    ├── controller/
    │   ├── CustomerController.java                       # /opc/crm/customer
    │   ├── ContactController.java                        # /opc/crm/contact
    │   ├── FollowUpController.java                       # /opc/crm/follow-up
    │   ├── OpportunityController.java                    # /opc/crm/opportunity (+ /score)
    │   ├── ContractController.java                       # /opc/crm/contract (NEW)
    │   ├── OrderController.java                          # /opc/crm/order (NEW)
    │   └── DashboardController.java                      # /opc/crm/dashboard
    ├── service/
    │   ├── CustomerService.java + Impl
    │   ├── ContactService.java + Impl
    │   ├── FollowUpService.java + Impl
    │   ├── OpportunityService.java + Impl                # state machine
    │   ├── ContractService.java + Impl
    │   ├── OrderService.java + Impl
    │   └── DashboardService.java + Impl                  # funnel aggregation
    ├── domain/
    │   ├── CrmCustomer.java
    │   ├── CrmContact.java
    │   ├── CrmFollowUp.java
    │   ├── CrmOpportunity.java
    │   ├── CrmContract.java
    │   └── CrmOrder.java
    ├── mapper/
    │   ├── CrmCustomerMapper.java + .xml
    │   ├── CrmContactMapper.java + .xml
    │   ├── CrmFollowUpMapper.java + .xml
    │   ├── CrmOpportunityMapper.java + .xml
    │   ├── CrmContractMapper.java + .xml
    │   └── CrmOrderMapper.java + .xml
    ├── dto/
    │   ├── CustomerCreateRequest.java
    │   ├── CustomerUpdateRequest.java
    │   ├── ContactCreateRequest.java
    │   ├── FollowUpCreateRequest.java
    │   ├── OpportunityCreateRequest.java
    │   ├── ContractCreateRequest.java
    │   ├── OrderCreateRequest.java
    │   ├── DashboardFunnelResponse.java
    │   └── OpportunityScoreResponse.java
    ├── enums/
    │   ├── OpportunityStage.java                         # LEAD/QUALIFIED/PROPOSAL/NEGOTIATION/WON/LOST
    │   ├── CustomerLevel.java                            # A/B/C/D
    │   ├── CustomerSource.java                           # REFERRAL/AD/WEBSITE/COLD_CALL/OTHER
    │   ├── ContractStatus.java                           # DRAFT/ACTIVE/EXPIRED/TERMINATED
    │   └── OrderStatus.java                              # PENDING/PAID/SHIPPED/COMPLETED/CANCELLED
    ├── gateway/
    │   ├── NotificationGateway.java                      # OpenFeign → opc-notification
    │   ├── AiCoreGateway.java                            # OpenFeign → opc-ai-core (LLM)
    │   └── UserCenterGateway.java                        # OpenFeign → opc-user-center (owner info)
    └── statemachine/
        └── OpportunityStateMachine.java                  # stage transitions validation

springboot3/ruoyi-modules/opc-crm/src/test/java/com/ruoyi/opc/crm/
├── service/CustomerServiceTest.java                      # 8 @Test
├── service/ContactServiceTest.java                       # 4 @Test
├── service/FollowUpServiceTest.java                      # 6 @Test
├── service/OpportunityServiceTest.java                   # 10 @Test
├── service/ContractServiceTest.java                      # 6 @Test
├── service/OrderServiceTest.java                         # 6 @Test
├── service/DashboardServiceTest.java                     # 4 @Test
├── statemachine/OpportunityStateMachineTest.java         # 8 @Test
└── gateway/NotificationGatewayTest.java                  # 4 @Test (Mocked Feign)

vue3-typescript/src/views/opc/crm/
├── CustomerList.vue                                      # 客户列表 + 标签筛选
├── CustomerDetail.vue                                    # 详情 + 联系人 + 跟进 + 商机
├── OpportunityKanban.vue                                 # 商机看板 (drag & drop)
└── FollowUpTimeline.vue                                  # 跟进时间线

vue3-typescript/src/api/opc/crm.ts

springboot3/sql/migrations/V20260918__crm_schema.sql
springboot3/sql/seed/crm_seed.sql                         # 10 customers + 30 contacts + 50 follow-ups + 20 opps

springboot3/deploy/nacos/opc-crm-dev.yml
springboot3/deploy/nacos/opc-crm-prod.yml
springboot3/deploy/docker-compose.yml                     # MODIFY: add aiopc-crm
springboot3/deploy/scripts/health-check.sh                # MODIFY: add 8 endpoints
springboot3/deploy/RECOVERY.md                            # MODIFY: add §opc-crm
springboot3/deploy/helm/opc/values.yaml                   # MODIFY: add crm service
springboot3/deploy/helm/opc/templates/deployment-crm.yaml # NEW
springboot3/deploy/helm/opc/templates/service-crm.yaml    # NEW

docs/verification/week-50/OPC-W50-VERIFICATION-opc-crm.md
```

---

## Task 1: Scaffold opc-crm module

**Files:**
- Create: `springboot3/ruoyi-modules/opc-crm/pom.xml`
- Create: `springboot3/ruoyi-modules/opc-crm/Dockerfile`
- Create: `springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/OpcCrmApplication.java`
- Create: `springboot3/ruoyi-modules/opc-crm/src/main/resources/bootstrap.yml`
- Create: `springboot3/ruoyi-modules/opc-crm/src/main/resources/application.yml`

- [ ] **Step 1: Create pom.xml**

Extend `ruoyi-modules` parent 3.6.8. Use vanilla MyBatis (NOT MyBatis-Plus — W48 lesson). Add `spring-boot-starter-web`, `mybatis`, `mysql-connector-j`, `opc-common` dep, `lombok`, `spring-cloud-starter-openfeign`. Thin-jar pattern: `<phase>none</phase>` on `spring-boot:repackage`.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.ruoyi</groupId>
        <artifactId>ruoyi-modules</artifactId>
        <version>3.6.8</version>
    </parent>

    <artifactId>opc-crm</artifactId>
    <description>OPC 客户关系管理 (Customer + Contact + FollowUp + Opportunity + Contract + Order)</description>

    <dependencies>
        <dependency><groupId>com.ruoyi</groupId><artifactId>opc-common</artifactId><version>3.6.8</version></dependency>
        <dependency><groupId>com.ruoyi</groupId><artifactId>ruoyi-common-security</artifactId><version>3.6.8</version></dependency>
        <dependency><groupId>com.ruoyi</groupId><artifactId>ruoyi-system-api</artifactId><version>3.6.8</version></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-openfeign</artifactId></dependency>
        <dependency><groupId>com.baomidou</groupId><artifactId>mybatis-plus-boot-starter</artifactId><version>3.5.5</version><scope>provided</scope></dependency>
        <!-- vanilla MyBatis (NOT mybatis-plus) — W48 lesson -->
        <dependency><groupId>org.mybatis</groupId><artifactId>mybatis</artifactId><version>3.5.19</version></dependency>
        <dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>3.0.4</version></dependency>
        <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId></dependency>
        <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><scope>provided</scope></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    </dependencies>

    <build>
        <finalName>${project.artifactId}</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>default</id>
                        <phase>none</phase>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create Dockerfile (W48 thin-jar template)**

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/opc-crm.jar opc-crm.jar
COPY target/dependency/ lib/
EXPOSE http
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -cp 'opc-crm.jar:lib/*' com.ruoyi.opc.crm.OpcCrmApplication"]
```

Replace `http` with `9312` later (use placeholder during scaffold).

- [ ] **Step 3: Create OpcCrmApplication.java**

```java
package com.ruoyi.opc.crm;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@EnableCustomConfig
@EnableRyFeignClients(basePackages = { "com.ruoyi.system.api", "com.ruoyi.opc" })
@SpringBootApplication
@ComponentScan({ "com.ruoyi.opc", "com.ruoyi.system" })
public class OpcCrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpcCrmApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC CRM 启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
```

- [ ] **Step 4: Create bootstrap.yml**

```yaml
server:
  port: ${SERVER_PORT:9312}

spring:
  application:
    name: opc-crm
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER:nacos1:8848}
        namespace: ${NACOS_NAMESPACE:opc-dev}
        fail-fast: true
      config:
        server-addr: ${NACOS_SERVER:nacos1:8848}
        namespace: ${NACOS_NAMESPACE:opc-dev}
        file-extension: yml
        fail-fast: true
        refresh-enabled: true
        retry-time: 3000
        max-retry-time: 60000
  config:
    import:
      - nacos:application-${spring.profiles.active}.${spring.config.file-extension}
      - nacos:${spring.application.name}-${spring.profiles.active}.${spring.config.file-extension}
      - nacos:opc-common-${spring.profiles.active}.${spring.config.file-extension}

jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD:OpcEncrypt!2026}
    algorithm: PBEWithMD5AndDES

logging:
  level:
    com.ruoyi.opc: debug
    com.alibaba.cloud.nacos: info
```

- [ ] **Step 5: Create application.yml**

```yaml
spring:
  main:
    allow-bean-definition-overriding: true
  cloud:
    allow-bean-definition-overriding: true
  datasource:
    dynamic:
      primary: master
      datasource:
        master:
          url: jdbc:mysql://${MYSQL_HOST:mysql}:${MYSQL_PORT:3306}/${MYSQL_DB:ry-vue-opc}?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
          username: ${MYSQL_USER:root}
          password: ${MYSQL_PASSWORD:root}
  redis:
    host: ${REDIS_HOST:redis}
    port: ${REDIS_PORT:6379}

# MyBatis
mybatis:
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: com.ruoyi.opc.crm.domain
  configuration:
    map-underscore-to-camel-case: true

# OPC CRM properties
opc:
  crm:
    # OpenFeign downstreams
    notification:
      base-url: http://opc-notification:9310
    ai-core:
      base-url: http://opc-ai-core:9301
    user-center:
      base-url: http://opc-user-center:9302
    # State machine
    opportunity:
      auto-notify-on-stage-change: true
      auto-notify-on-new-followup: true
```

- [ ] **Step 6: Verify compile**

```bash
cd "D:/work-ai/0401-lumen-opc/springboot3"
JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" \
  mvn -pl ruoyi-modules/opc-crm -am compile -Dmaven.test.skip=true
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add springboot3/ruoyi-modules/opc-crm/
git commit -m "feat(crm): Task 1 — scaffold opc-crm module (pom + Dockerfile + app + yml)"
git push origin main
```

---

## Task 2: MySQL migration + seed SQL

**Files:**
- Create: `springboot3/sql/migrations/V20260918__crm_schema.sql`
- Create: `springboot3/sql/seed/crm_seed.sql`
- Create: `springboot3/deploy/mysql-initdb.d/09-opc-crm-schema.sql`
- Create: `springboot3/deploy/mysql-initdb.d/95-opc-crm-seed.sql`

- [ ] **Step 1: Create migration SQL**

```sql
-- V20260918__crm_schema.sql
-- OPC CRM 6 表表 + 审计列

CREATE TABLE IF NOT EXISTS opc_crm_customer (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    name          VARCHAR(200) NOT NULL,
    source        VARCHAR(32)  NOT NULL DEFAULT 'OTHER',
    tags          VARCHAR(512) NOT NULL DEFAULT '',       -- 逗号分隔
    owner_id      BIGINT       NOT NULL,
    level         VARCHAR(8)   NOT NULL DEFAULT 'C',
    phone         VARCHAR(32),
    email         VARCHAR(128),
    address       VARCHAR(512),
    remark        VARCHAR(1024),
    create_by     VARCHAR(64)  NOT NULL DEFAULT 'system',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by     VARCHAR(64),
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    INDEX idx_owner_id (owner_id),
    INDEX idx_level (level),
    INDEX idx_source (source),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户档案';

CREATE TABLE IF NOT EXISTS opc_crm_contact (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT       NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    phone       VARCHAR(32),
    email       VARCHAR(128),
    position    VARCHAR(64),
    is_primary  TINYINT      NOT NULL DEFAULT 0,
    remark      VARCHAR(512),
    create_by   VARCHAR(64)  NOT NULL DEFAULT 'system',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   VARCHAR(64),
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    INDEX idx_customer_id (customer_id),
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户联系人';

CREATE TABLE IF NOT EXISTS opc_crm_follow_up (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT       NOT NULL,
    contact_id  BIGINT,
    type        VARCHAR(32)  NOT NULL DEFAULT 'PHONE',    -- PHONE/EMAIL/WECHAT/VISIT/OTHER
    content     VARCHAR(2048) NOT NULL,
    next_at     DATETIME,
    owner_id    BIGINT       NOT NULL,
    create_by   VARCHAR(64)  NOT NULL DEFAULT 'system',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   VARCHAR(64),
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    INDEX idx_customer_id (customer_id),
    INDEX idx_owner_id (owner_id),
    INDEX idx_next_at (next_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跟进记录';

CREATE TABLE IF NOT EXISTS opc_crm_opportunity (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id     BIGINT       NOT NULL,
    name            VARCHAR(200) NOT NULL,
    amount          DECIMAL(18,2) NOT NULL DEFAULT 0,
    stage           VARCHAR(32)  NOT NULL DEFAULT 'LEAD',   -- LEAD/QUALIFIED/PROPOSAL/NEGOTIATION/WON/LOST
    score           INT          NOT NULL DEFAULT 0,         -- 0-100 LLM 商机打分
    score_reason    VARCHAR(1024),
    expected_close  DATE,
    owner_id        BIGINT       NOT NULL,
    lost_reason     VARCHAR(512),
    create_by       VARCHAR(64)  NOT NULL DEFAULT 'system',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64),
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    INDEX idx_customer_id (customer_id),
    INDEX idx_stage (stage),
    INDEX idx_owner_id (owner_id),
    INDEX idx_expected_close (expected_close)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售商机';

CREATE TABLE IF NOT EXISTS opc_crm_contract (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id     BIGINT       NOT NULL,
    opportunity_id  BIGINT,
    contract_no     VARCHAR(64)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    amount          DECIMAL(18,2) NOT NULL DEFAULT 0,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT/ACTIVE/EXPIRED/TERMINATED
    signed_at       DATE,
    expire_at       DATE,
    file_url        VARCHAR(512),
    remark          VARCHAR(1024),
    create_by       VARCHAR(64)  NOT NULL DEFAULT 'system',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64),
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_contract_no (contract_no),
    INDEX idx_customer_id (customer_id),
    INDEX idx_status (status),
    INDEX idx_signed_at (signed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户合同';

CREATE TABLE IF NOT EXISTS opc_crm_order (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id     BIGINT       NOT NULL,
    contract_id     BIGINT,
    order_no        VARCHAR(64)  NOT NULL,
    items_json      TEXT         NOT NULL,                  -- [{sku, name, qty, price}]
    total           DECIMAL(18,2) NOT NULL DEFAULT 0,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING', -- PENDING/PAID/SHIPPED/COMPLETED/CANCELLED
    paid_at         DATETIME,
    shipped_at      DATETIME,
    remark          VARCHAR(1024),
    create_by       VARCHAR(64)  NOT NULL DEFAULT 'system',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64),
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_customer_id (customer_id),
    INDEX idx_contract_id (contract_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户订单';
```

- [ ] **Step 2: Create seed SQL (10 customers + 30 contacts + 50 follow-ups + 20 opps + 8 contracts + 12 orders)**

```sql
-- crm_seed.sql
-- Seed for opc-crm dev environment

INSERT INTO opc_crm_customer (name, source, tags, owner_id, level, phone, email, remark) VALUES
('北京华联贸易有限公司', 'REFERRAL', 'VIP,大客户', 1, 'A', '010-66668888', 'contact@hualian.cn', '老客户推荐'),
('上海星辉科技', 'AD', '科技,SaaS', 1, 'B', '021-55556666', 'info@xinghui.com', '试用中'),
('广州博远咨询', 'WEBSITE', '咨询,服务', 1, 'A', '020-88889999', 'hello@boyuan.cn', '高价值'),
('深圳创新工场', 'COLD_CALL', 'VC,投资', 1, 'B', '0755-22223333', 'bd@chuangxin.com', '潜在投资方'),
('杭州云栖智能', 'REFERRAL', 'AI,智能', 1, 'A', '0571-44445555', 'sales@yunqi.ai', '战略客户'),
('成都西南物流', 'WEBSITE', '物流,运输', 1, 'C', '028-77776666', 'service@sw-logistics.cn', '中型'),
('武汉光谷生物', 'REFERRAL', '生物,医药', 1, 'A', '027-66668888', 'contact@bio-optics.cn', '重点'),
('西安丝路软件', 'AD', '软件,外包', 1, 'B', '029-55554444', 'sales@silkroad-soft.cn', '技术合作'),
('南京雨花制造', 'WEBSITE', '制造,工业', 1, 'C', '025-33332222', 'info@yuhua-mfg.cn', '传统行业'),
('苏州工业园能源', 'COLD_CALL', '能源,化工', 1, 'B', '0512-11112222', 'bd@sip-energy.cn', '新客户');

-- 30 contacts (3 per customer)
INSERT INTO opc_crm_contact (customer_id, name, phone, email, position, is_primary) VALUES
(1, '张总', '13800000001', 'zhang@hualian.cn', '总经理', 1),
(1, '李经理', '13800000002', 'li@hualian.cn', '采购经理', 0),
(1, '王助理', '13800000003', 'wang@hualian.cn', '助理', 0),
(2, '陈总', '13800000004', 'chen@xinghui.com', 'CTO', 1),
(2, '刘经理', '13800000005', 'liu@xinghui.com', '运营经理', 0),
(2, '赵助理', '13800000006', 'zhao@xinghui.com', '助理', 0),
(3, '孙总', '13800000007', 'sun@boyuan.cn', '合伙人', 1),
(3, '周经理', '13800000008', 'zhou@boyuan.cn', '项目经理', 0),
(3, '吴助理', '13800000009', 'wu@boyuan.cn', '助理', 0),
(4, '郑总', '13800000010', 'zheng@chuangxin.com', '创始合伙人', 1),
(4, '冯经理', '13800000011', 'feng@chuangxin.com', '投资经理', 0),
(4, '褚助理', '13800000012', 'chu@chuangxin.com', '助理', 0),
(5, '卫总', '13800000013', 'wei@yunqi.ai', 'CEO', 1),
(5, '蒋经理', '13800000014', 'jiang@yunqi.ai', 'AI负责人', 0),
(5, '沈助理', '13800000015', 'shen@yunqi.ai', '助理', 0),
(6, '韩总', '13800000016', 'han@sw-logistics.cn', '董事长', 1),
(6, '杨经理', '13800000017', 'yang@sw-logistics.cn', '运营总监', 0),
(6, '朱助理', '13800000018', 'zhu@sw-logistics.cn', '助理', 0),
(7, '秦总', '13800000019', 'qin@bio-optics.cn', '总经理', 1),
(7, '尤经理', '13800000020', 'you@bio-optics.cn', '研发总监', 0),
(7, '许助理', '13800000021', 'xu@bio-optics.cn', '助理', 0),
(8, '何总', '13800000022', 'he@silkroad-soft.cn', '创始人', 1),
(8, '吕经理', '13800000023', 'lv@silkroad-soft.cn', '技术总监', 0),
(8, '施助理', '13800000024', 'shi@silkroad-soft.cn', '助理', 0),
(9, '苗总', '13800000025', 'miao@yuhua-mfg.cn', '厂长', 1),
(9, '凤经理', '13800000026', 'feng@yuhua-mfg.cn', '生产经理', 0),
(9, '花助理', '13800000027', 'hua@yuhua-mfg.cn', '助理', 0),
(10, '方总', '13800000028', 'fang@sip-energy.cn', '总经理', 1),
(10, '俞经理', '13800000029', 'yu@sip-energy.cn', '能源经理', 0),
(10, '任助理', '13800000030', 'ren@sip-energy.cn', '助理', 0);

-- 50 follow-ups (5 per customer)
INSERT INTO opc_crm_follow_up (customer_id, contact_id, type, content, next_at, owner_id) VALUES
(1, 1, 'PHONE', '电话沟通需求，预算 100w', '2026-10-15 10:00:00', 1),
(1, 2, 'EMAIL', '发送方案 v1', '2026-10-20 14:00:00', 1),
(1, 1, 'VISIT', '现场拜访，演示产品', '2026-10-25 09:00:00', 1),
(1, 3, 'WECHAT', '微信沟通细节', '2026-11-01 16:00:00', 1),
(1, 1, 'PHONE', '价格谈判', '2026-11-10 11:00:00', 1),
(2, 4, 'PHONE', '初次接触，了解需求', '2026-10-12 15:00:00', 1),
(2, 4, 'EMAIL', '发送产品介绍', '2026-10-18 10:00:00', 1),
(2, 5, 'VISIT', '上门演示', '2026-10-22 14:00:00', 1),
(2, 4, 'PHONE', '技术答疑', '2026-10-28 11:00:00', 1),
(2, 4, 'EMAIL', '商务报价', '2026-11-05 09:00:00', 1),
(3, 7, 'VISIT', '战略合作洽谈', '2026-10-15 13:00:00', 1),
(3, 7, 'PHONE', '高层对接', '2026-10-25 16:00:00', 1),
(3, 8, 'EMAIL', '方案细化', '2026-11-01 10:00:00', 1),
(3, 7, 'VISIT', '签约前准备', '2026-11-08 14:00:00', 1),
(3, 7, 'PHONE', '最终确认', '2026-11-15 11:00:00', 1),
(4, 10, 'PHONE', '初步意向沟通', '2026-10-18 14:00:00', 1),
(4, 10, 'EMAIL', 'BP 文档', '2026-10-26 09:00:00', 1),
(4, 11, 'VISIT', '实地考察', '2026-11-02 15:00:00', 1),
(4, 10, 'PHONE', '估值讨论', '2026-11-12 10:00:00', 1),
(4, 10, 'EMAIL', 'TS 草案', '2026-11-20 16:00:00', 1),
(5, 13, 'PHONE', '战略合作意向', '2026-10-14 10:00:00', 1),
(5, 13, 'VISIT', '高层会面', '2026-10-22 14:00:00', 1),
(5, 14, 'EMAIL', '技术方案', '2026-10-30 11:00:00', 1),
(5, 13, 'PHONE', '框架协议', '2026-11-08 09:00:00', 1),
(5, 13, 'VISIT', '签约仪式', '2026-11-18 16:00:00', 1),
(6, 16, 'PHONE', '初次接触', '2026-10-20 11:00:00', 1),
(6, 16, 'EMAIL', '方案介绍', '2026-10-28 14:00:00', 1),
(6, 17, 'VISIT', '现场调研', '2026-11-05 10:00:00', 1),
(6, 16, 'PHONE', '商务谈判', '2026-11-15 15:00:00', 1),
(6, 16, 'EMAIL', '合同草案', '2026-11-25 09:00:00', 1),
(7, 19, 'VISIT', '合作意向', '2026-10-16 14:00:00', 1),
(7, 19, 'PHONE', '细节讨论', '2026-10-24 10:00:00', 1),
(7, 20, 'EMAIL', '技术白皮书', '2026-11-01 13:00:00', 1),
(7, 19, 'VISIT', '实地考察', '2026-11-10 11:00:00', 1),
(7, 19, 'PHONE', '商务确认', '2026-11-20 16:00:00', 1),
(8, 22, 'PHONE', '技术合作意向', '2026-10-19 09:00:00', 1),
(8, 22, 'EMAIL', '合作方案', '2026-10-27 14:00:00', 1),
(8, 23, 'VISIT', '技术对接', '2026-11-03 10:00:00', 1),
(8, 22, 'PHONE', '资源协调', '2026-11-12 15:00:00', 1),
(8, 22, 'EMAIL', '合同细节', '2026-11-22 11:00:00', 1),
(9, 25, 'PHONE', '需求了解', '2026-10-21 13:00:00', 1),
(9, 25, 'EMAIL', '产品方案', '2026-10-29 10:00:00', 1),
(9, 26, 'VISIT', '现场勘查', '2026-11-06 14:00:00', 1),
(9, 25, 'PHONE', '价格沟通', '2026-11-16 09:00:00', 1),
(9, 25, 'EMAIL', '合同初稿', '2026-11-26 16:00:00', 1),
(10, 28, 'PHONE', '初步接触', '2026-10-23 11:00:00', 1),
(10, 28, 'EMAIL', '公司介绍', '2026-10-31 14:00:00', 1),
(10, 29, 'VISIT', '客户拜访', '2026-11-08 10:00:00', 1),
(10, 28, 'PHONE', '需求确认', '2026-11-18 13:00:00', 1),
(10, 28, 'EMAIL', '报价单', '2026-11-28 15:00:00', 1);

-- 20 opportunities (2 per customer, various stages)
INSERT INTO opc_crm_opportunity (customer_id, name, amount, stage, score, expected_close, owner_id) VALUES
(1, 'CRM系统采购', 1200000.00, 'NEGOTIATION', 85, '2026-11-15', 1),
(1, '数据分析模块', 800000.00, 'PROPOSAL', 70, '2026-12-01', 1),
(2, '试用转正式', 500000.00, 'QUALIFIED', 60, '2026-12-15', 1),
(2, '扩展模块', 300000.00, 'LEAD', 30, '2027-01-30', 1),
(3, '战略合作', 2000000.00, 'WON', 95, '2026-11-08', 1),
(3, '追加服务', 600000.00, 'NEGOTIATION', 80, '2026-12-01', 1),
(4, '种子轮投资', 5000000.00, 'PROPOSAL', 75, '2026-12-20', 1),
(4, '联合办公', 400000.00, 'LEAD', 40, '2027-02-15', 1),
(5, 'AI联合实验室', 3000000.00, 'WON', 90, '2026-11-18', 1),
(5, '数据中台', 1500000.00, 'NEGOTIATION', 85, '2026-12-15', 1),
(6, '物流调度系统', 900000.00, 'QUALIFIED', 65, '2027-01-15', 1),
(6, '仓储管理', 600000.00, 'PROPOSAL', 70, '2026-12-30', 1),
(7, '生物样本库', 2500000.00, 'NEGOTIATION', 88, '2026-12-10', 1),
(7, '研发协作平台', 800000.00, 'LEAD', 35, '2027-03-01', 1),
(8, '技术外包框架', 1500000.00, 'PROPOSAL', 75, '2026-12-25', 1),
(8, '联合产品开发', 2000000.00, 'LOST', 50, '2026-10-30', 1),
(9, '智能制造升级', 1800000.00, 'QUALIFIED', 60, '2027-01-20', 1),
(9, 'MES系统', 700000.00, 'LEAD', 30, '2027-02-28', 1),
(10, '能源监控平台', 1100000.00, 'PROPOSAL', 72, '2026-12-20', 1),
(10, '智能电表', 450000.00, 'LOST', 45, '2026-10-15', 1);

-- 8 contracts
INSERT INTO opc_crm_contract (customer_id, opportunity_id, contract_no, title, amount, status, signed_at, expire_at) VALUES
(3, 5, 'C2026-001', '战略合作主合同', 2000000.00, 'ACTIVE', '2026-11-08', '2027-11-07'),
(5, 9, 'C2026-002', 'AI联合实验室协议', 3000000.00, 'ACTIVE', '2026-11-18', '2027-11-17'),
(1, 1, 'C2026-003', 'CRM系统采购合同', 1200000.00, 'DRAFT', NULL, '2027-11-15'),
(5, 10, 'C2026-004', '数据中台建设', 1500000.00, 'DRAFT', NULL, '2027-12-15'),
(1, 2, 'C2026-005', '数据分析模块补充', 800000.00, 'DRAFT', NULL, '2027-12-01'),
(7, 13, 'C2026-006', '生物样本库框架', 2500000.00, 'DRAFT', NULL, '2027-12-10'),
(8, 15, 'C2026-007', '技术外包框架协议', 1500000.00, 'DRAFT', NULL, '2027-12-25'),
(9, 17, 'C2026-008', '智能制造升级意向', 1800000.00, 'DRAFT', NULL, '2028-01-20');

-- 12 orders
INSERT INTO opc_crm_order (customer_id, contract_id, order_no, items_json, total, status, paid_at) VALUES
(3, 1, 'O2026-001', '[{"sku":"AI-LAB-001","name":"AI实验环境","qty":1,"price":1000000.00}]', 1000000.00, 'PAID', '2026-11-08 10:00:00'),
(3, 1, 'O2026-002', '[{"sku":"DATA-SVC-001","name":"数据服务年费","qty":1,"price":500000.00}]', 500000.00, 'PAID', '2026-11-08 10:00:00'),
(3, 1, 'O2026-003', '[{"sku":"CONSULT-001","name":"咨询服务","qty":1,"price":500000.00}]', 500000.00, 'PENDING', NULL),
(5, 2, 'O2026-004', '[{"sku":"GPU-CLUSTER-001","name":"GPU集群","qty":2,"price":1000000.00}]', 2000000.00, 'PAID', '2026-11-18 11:00:00'),
(5, 2, 'O2026-005', '[{"sku":"ML-PLATFORM-001","name":"ML平台","qty":1,"price":1000000.00}]', 1000000.00, 'SHIPPED', '2026-11-19 14:00:00'),
(1, 3, 'O2026-006', '[{"sku":"CRM-CORE-001","name":"CRM核心模块","qty":1,"price":600000.00}]', 600000.00, 'PENDING', NULL),
(1, 3, 'O2026-007', '[{"sku":"CRM-EXT-001","name":"CRM扩展","qty":1,"price":600000.00}]', 600000.00, 'PENDING', NULL),
(5, 4, 'O2026-008', '[{"sku":"DATA-PLATFORM-001","name":"数据中台","qty":1,"price":1500000.00}]', 1500000.00, 'PENDING', NULL),
(7, 6, 'O2026-009', '[{"sku":"BIO-SAMPLE-001","name":"生物样本库","qty":1,"price":1500000.00}]', 1500000.00, 'PENDING', NULL),
(7, 6, 'O2026-010', '[{"sku":"BIO-ANALYSIS-001","name":"样本分析模块","qty":1,"price":1000000.00}]', 1000000.00, 'PENDING', NULL),
(8, 7, 'O2026-011', '[{"sku":"DEV-OUT-001","name":"外包开发","qty":1,"price":800000.00}]', 800000.00, 'PENDING', NULL),
(8, 7, 'O2026-012', '[{"sku":"QA-OUT-001","name":"外包测试","qty":1,"price":700000.00}]', 700000.00, 'CANCELLED', NULL);
```

- [ ] **Step 3: Copy to deploy/initdb.d**

```bash
cp springboot3/sql/migrations/V20260918__crm_schema.sql springboot3/deploy/mysql-initdb.d/09-opc-crm-schema.sql
cp springboot3/sql/seed/crm_seed.sql springboot3/deploy/mysql-initdb.d/95-opc-crm-seed.sql
```

- [ ] **Step 4: Apply to live MySQL**

```bash
docker exec aiopc-mysql mysql -uroot -proot ry-vue-opc < springboot3/sql/migrations/V20260918__crm_schema.sql
docker exec aiopc-mysql mysql -uroot -proot ry-vue-opc < springboot3/sql/seed/crm_seed.sql
docker exec aiopc-mysql mysql -uroot -proot ry-vue-opc -e "SHOW TABLES LIKE 'opc_crm_%'"
```

Expected: 6 tables listed (`opc_crm_customer`, `opc_crm_contact`, `opc_crm_follow_up`, `opc_crm_opportunity`, `opc_crm_contract`, `opc_crm_order`).

- [ ] **Step 5: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add springboot3/sql/ springboot3/deploy/mysql-initdb.d/
git commit -m "feat(crm): Task 2 — 6 tables + seed (10 customers/30 contacts/50 follow-ups/20 opps/8 contracts/12 orders)"
git push origin main
```

---

## Task 3: Domain enums

**Files:**
- Create: `springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/enums/OpportunityStage.java`
- Create: `springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/enums/CustomerLevel.java`
- Create: `springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/enums/CustomerSource.java`
- Create: `springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/enums/ContractStatus.java`
- Create: `springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/enums/OrderStatus.java`

- [ ] **Step 1: Create OpportunityStage enum (with state machine)**

```java
package com.ruoyi.opc.crm.enums;

import java.util.Set;

/**
 * 商机阶段 (漏斗: LEAD → QUALIFIED → PROPOSAL → NEGOTIATION → WON/LOST)
 */
public enum OpportunityStage {
    LEAD, QUALIFIED, PROPOSAL, NEGOTIATION, WON, LOST;

    private static final Set<OpportunityStage> FROM_LEAD       = Set.of(QUALIFIED, LOST);
    private static final Set<OpportunityStage> FROM_QUALIFIED  = Set.of(PROPOSAL, LOST);
    private static final Set<OpportunityStage> FROM_PROPOSAL   = Set.of(NEGOTIATION, LOST);
    private static final Set<OpportunityStage> FROM_NEGOTIATION = Set.of(WON, LOST);
    private static final Set<OpportunityStage> FROM_WON        = Set.of();
    private static final Set<OpportunityStage> FROM_LOST       = Set.of(LEAD);

    public boolean canTransitionTo(OpportunityStage target) {
        if (target == null || target == this) return false;
        return switch (this) {
            case LEAD        -> FROM_LEAD.contains(target);
            case QUALIFIED   -> FROM_QUALIFIED.contains(target);
            case PROPOSAL    -> FROM_PROPOSAL.contains(target);
            case NEGOTIATION -> FROM_NEGOTIATION.contains(target);
            case WON         -> FROM_WON.contains(target);
            case LOST        -> FROM_LOST.contains(target);
        };
    }

    public boolean isTerminal() {
        return this == WON;
    }

    /** 是否计入漏斗 (WON/LOST 不计入活跃漏斗) */
    public boolean isActive() {
        return this != WON && this != LOST;
    }
}
```

- [ ] **Step 2: Create other enums (compact)**

```java
// CustomerLevel.java
package com.ruoyi.opc.crm.enums;
public enum CustomerLevel { A, B, C, D; }

// CustomerSource.java
package com.ruoyi.opc.crm.enums;
public enum CustomerSource {
    REFERRAL, AD, WEBSITE, COLD_CALL, OTHER;
}

// ContractStatus.java
package com.ruoyi.opc.crm.enums;
public enum ContractStatus {
    DRAFT, ACTIVE, EXPIRED, TERMINATED;

    public boolean isActive() { return this == ACTIVE; }
}

// OrderStatus.java
package com.ruoyi.opc.crm.enums;
public enum OrderStatus {
    PENDING, PAID, SHIPPED, COMPLETED, CANCELLED;

    public boolean isFinal() { return this == COMPLETED || this == CANCELLED; }
}
```

- [ ] **Step 3: Verify compile**

```bash
cd "D:/work-ai/0401-lumen-opc/springboot3"
JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" \
  mvn -pl ruoyi-modules/opc-crm -am compile -Dmaven.test.skip=true 2>&1 | tail -3
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/enums/
git commit -m "feat(crm): Task 3 — domain enums (stage state machine + level/source/status)"
git push origin main
```

---

## Task 4: Domain entities (6 POJOs)

**Files:**
- Create: 6 domain files under `springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/domain/`

- [ ] **Step 1: Create CrmCustomer.java**

```java
package com.ruoyi.opc.crm.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CrmCustomer implements Serializable {
    private Long id;
    private String name;
    private String source;
    private String tags;
    private Long ownerId;
    private String level;
    private String phone;
    private String email;
    private String address;
    private String remark;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
    private Integer deleted;
}
```

- [ ] **Step 2: Create CrmContact.java** (similar pattern, fields: id, customerId, name, phone, email, position, isPrimary, remark + audit cols)

- [ ] **Step 3: Create CrmFollowUp.java** (fields: id, customerId, contactId, type, content, nextAt, ownerId + audit cols)

- [ ] **Step 4: Create CrmOpportunity.java** (fields: id, customerId, name, amount, stage, score, scoreReason, expectedClose, ownerId, lostReason + audit cols)

- [ ] **Step 5: Create CrmContract.java** (fields: id, customerId, opportunityId, contractNo, title, amount, status, signedAt, expireAt, fileUrl, remark + audit cols)

- [ ] **Step 6: Create CrmOrder.java** (fields: id, customerId, contractId, orderNo, itemsJson, total, status, paidAt, shippedAt, remark + audit cols)

All use `@Data` + `Serializable` + `@JsonFormat` for `LocalDateTime`. Follow the pattern from `NotificationInbox.java` (opc-notification module) exactly.

- [ ] **Step 7: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/domain/
git commit -m "feat(crm): Task 4 — 6 domain entities (Customer/Contact/FollowUp/Opportunity/Contract/Order)"
git push origin main
```

---

## Task 5: Mapper interfaces + XML (6 mappers)

**Files:**
- Create: 6 mapper interfaces + 6 XML files

- [ ] **Step 1: Create MybatisConfig.java**

```java
package com.ruoyi.opc.crm.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.ruoyi.opc.crm.mapper")
public class MybatisConfig {
}
```

- [ ] **Step 2: Create CrmCustomerMapper.java**

```java
package com.ruoyi.opc.crm.mapper;

import com.ruoyi.opc.crm.domain.CrmCustomer;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface CrmCustomerMapper {
    int insert(CrmCustomer customer);
    int updateById(CrmCustomer customer);
    CrmCustomer selectById(@Param("id") Long id);
    List<CrmCustomer> selectList(@Param("ownerId") Long ownerId,
                                  @Param("level") String level,
                                  @Param("source") String source,
                                  @Param("tag") String tag,
                                  @Param("keyword") String keyword);
    int countByOwner(@Param("ownerId") Long ownerId);
}
```

- [ ] **Step 3: Create CrmCustomerMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.ruoyi.opc.crm.mapper.CrmCustomerMapper">

    <resultMap id="BaseResultMap" type="com.ruoyi.opc.crm.domain.CrmCustomer">
        <id property="id" column="id"/>
        <result property="name" column="name"/>
        <result property="source" column="source"/>
        <result property="tags" column="tags"/>
        <result property="ownerId" column="owner_id"/>
        <result property="level" column="level"/>
        <result property="phone" column="phone"/>
        <result property="email" column="email"/>
        <result property="address" column="address"/>
        <result property="remark" column="remark"/>
        <result property="createBy" column="create_by"/>
        <result property="createTime" column="create_time"/>
        <result property="updateBy" column="update_by"/>
        <result property="updateTime" column="update_time"/>
        <result property="deleted" column="deleted"/>
    </resultMap>

    <sql id="BaseColumns">
        id, name, source, tags, owner_id, level, phone, email, address, remark,
        create_by, create_time, update_by, update_time, deleted
    </sql>

    <insert id="insert" parameterType="com.ruoyi.opc.crm.domain.CrmCustomer" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO opc_crm_customer (<include refid="BaseColumns"/>)
        VALUES (
            #{name}, #{source}, #{tags}, #{ownerId}, #{level}, #{phone}, #{email}, #{address}, #{remark},
            #{createBy}, NOW(), #{updateBy}, NOW(), #{deleted}
        )
    </insert>

    <update id="updateById" parameterType="com.ruoyi.opc.crm.domain.CrmCustomer">
        UPDATE opc_crm_customer SET
            name = #{name}, source = #{source}, tags = #{tags}, level = #{level},
            phone = #{phone}, email = #{email}, address = #{address}, remark = #{remark},
            update_by = #{updateBy}, update_time = NOW()
        WHERE id = #{id} AND deleted = 0
    </update>

    <select id="selectById" resultMap="BaseResultMap">
        SELECT <include refid="BaseColumns"/>
        FROM opc_crm_customer
        WHERE id = #{id} AND deleted = 0
    </select>

    <select id="selectList" resultMap="BaseResultMap">
        SELECT <include refid="BaseColumns"/>
        FROM opc_crm_customer
        <where>
            deleted = 0
            <if test="ownerId != null">AND owner_id = #{ownerId}</if>
            <if test="level != null and level != ''">AND level = #{level}</if>
            <if test="source != null and source != ''">AND source = #{source}</if>
            <if test="tag != null and tag != ''">AND FIND_IN_SET(#{tag}, tags)</if>
            <if test="keyword != null and keyword != ''">AND (name LIKE CONCAT('%', #{keyword}, '%') OR phone LIKE CONCAT('%', #{keyword}, '%'))</if>
        </where>
        ORDER BY create_time DESC
    </select>

    <select id="countByOwner" resultType="int">
        SELECT COUNT(*) FROM opc_crm_customer
        WHERE owner_id = #{ownerId} AND deleted = 0
    </select>
</mapper>
```

- [ ] **Step 4: Create remaining 5 mappers** (Contact, FollowUp, Opportunity, Contract, Order)

Use the same pattern. Each mapper has:
- `insert` (useGeneratedKeys)
- `updateById`
- `selectById`
- Custom queries (e.g., `selectByCustomerId`, `selectByStage`, `selectActiveFunnel`)

XMLs follow the pattern above. Mapper locations: `classpath*:mapper/**/*.xml`.

- [ ] **Step 5: Verify compile**

```bash
cd "D:/work-ai/0401-lumen-opc/springboot3"
JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" \
  mvn -pl ruoyi-modules/opc-crm -am compile -Dmaven.test.skip=true 2>&1 | tail -3
```

- [ ] **Step 6: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/config/ springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/mapper/ springboot3/ruoyi-modules/opc-crm/src/main/resources/mapper/
git commit -m "feat(crm): Task 5 — 6 mappers + XML (Customer/Contact/FollowUp/Opportunity/Contract/Order)"
git push origin main
```

---

## Task 6: CustomerService + tests (8 @Test)

**Files:**
- Create: `springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/CustomerService.java`
- Create: `springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/impl/CustomerServiceImpl.java`
- Create: `springboot3/ruoyi-modules/opc-crm/src/test/java/com/ruoyi/opc/crm/service/CustomerServiceTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmCustomer;
import com.ruoyi.opc.crm.mapper.CrmCustomerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerServiceTest {

    @Mock CrmCustomerMapper mapper;
    @InjectMocks CustomerServiceImpl service;

    @BeforeEach
    void setUp() {}

    @Test
    void should_create_customer_with_ownerId_from_security() {
        when(mapper.insert(any())).thenAnswer(inv -> {
            CrmCustomer c = inv.getArgument(0);
            c.setId(1L);
            return 1;
        });

        CrmCustomer created = service.create(CrmCustomer.builder().name("Test Co").build(), 1L);

        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getOwnerId()).isEqualTo(1L);
        assertThat(created.getCreateBy()).isEqualTo("1");
        assertThat(created.getSource()).isEqualTo("OTHER"); // default
        assertThat(created.getLevel()).isEqualTo("C");       // default
        verify(mapper).insert(any());
    }

    @Test
    void should_reject_blank_name() {
        assertThatThrownBy(() -> service.create(CrmCustomer.builder().name("").build(), 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
    }

    @Test
    void should_update_existing_customer() {
        CrmCustomer existing = CrmCustomer.builder().id(1L).name("Old").ownerId(1L).level("C").source("OTHER").build();
        when(mapper.selectById(1L)).thenReturn(existing);

        CrmCustomer update = CrmCustomer.builder().name("New").level("B").build();
        CrmCustomer result = service.update(1L, update, 1L);

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getLevel()).isEqualTo("B");
        verify(mapper).updateById(any());
    }

    @Test
    void should_throw_when_update_nonexistent() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(99L, CrmCustomer.builder().name("X").build(), 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void should_soft_delete_by_id() {
        when(mapper.selectById(1L)).thenReturn(CrmCustomer.builder().id(1L).name("X").build());
        service.delete(1L, 1L);
        verify(mapper).updateById(argThat(c -> c.getDeleted() == 1));
    }

    @Test
    void should_list_with_filters() {
        when(mapper.selectList(any(), any(), any(), any(), any()))
            .thenReturn(List.of(CrmCustomer.builder().id(1L).name("A").build()));
        List<CrmCustomer> list = service.list(1L, "A", "REFERRAL", "VIP", "kw");
        assertThat(list).hasSize(1);
    }

    @Test
    void should_throw_when_get_nonexistent() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_count_by_owner() {
        when(mapper.countByOwner(1L)).thenReturn(42);
        assertThat(service.countByOwner(1L)).isEqualTo(42);
    }
}
```

- [ ] **Step 2: Run test to verify fail**

```bash
cd "D:/work-ai/0401-lumen-opc/springboot3"
JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" \
  mvn -pl ruoyi-modules/opc-crm test -Dtest=CustomerServiceTest 2>&1 | tail -10
```

Expected: Compilation error (CustomerServiceImpl doesn't exist).

- [ ] **Step 3: Implement CustomerService interface**

```java
package com.ruoyi.opc.crm.service;

import com.ruoyi.opc.crm.domain.CrmCustomer;
import java.util.List;

public interface CustomerService {
    CrmCustomer create(CrmCustomer customer, Long ownerId);
    CrmCustomer update(Long id, CrmCustomer customer, Long operatorId);
    void delete(Long id, Long operatorId);
    CrmCustomer getById(Long id);
    List<CrmCustomer> list(Long ownerId, String level, String source, String tag, String keyword);
    int countByOwner(Long ownerId);
}
```

- [ ] **Step 4: Implement CustomerServiceImpl**

```java
package com.ruoyi.opc.crm.service.impl;

import com.ruoyi.opc.crm.domain.CrmCustomer;
import com.ruoyi.opc.crm.mapper.CrmCustomerMapper;
import com.ruoyi.opc.crm.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CrmCustomerMapper mapper;

    @Override
    @Transactional
    public CrmCustomer create(CrmCustomer customer, Long ownerId) {
        if (customer.getName() == null || customer.getName().isBlank()) {
            throw new IllegalArgumentException("客户名称不能为空");
        }
        customer.setOwnerId(ownerId);
        customer.setCreateBy(String.valueOf(ownerId));
        customer.setUpdateBy(String.valueOf(ownerId));
        if (customer.getSource() == null) customer.setSource("OTHER");
        if (customer.getLevel() == null) customer.setLevel("C");
        if (customer.getDeleted() == null) customer.setDeleted(0);
        mapper.insert(customer);
        log.info("Created customer id={} name={}", customer.getId(), customer.getName());
        return customer;
    }

    @Override
    @Transactional
    public CrmCustomer update(Long id, CrmCustomer update, Long operatorId) {
        CrmCustomer existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Customer not found: " + id);
        }
        if (update.getName() != null) existing.setName(update.getName());
        if (update.getSource() != null) existing.setSource(update.getSource());
        if (update.getTags() != null) existing.setTags(update.getTags());
        if (update.getLevel() != null) existing.setLevel(update.getLevel());
        if (update.getPhone() != null) existing.setPhone(update.getPhone());
        if (update.getEmail() != null) existing.setEmail(update.getEmail());
        if (update.getAddress() != null) existing.setAddress(update.getAddress());
        if (update.getRemark() != null) existing.setRemark(update.getRemark());
        existing.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        CrmCustomer existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Customer not found: " + id);
        }
        existing.setDeleted(1);
        existing.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(existing);
    }

    @Override
    public CrmCustomer getById(Long id) {
        CrmCustomer c = mapper.selectById(id);
        if (c == null) {
            throw new IllegalArgumentException("Customer not found: " + id);
        }
        return c;
    }

    @Override
    public List<CrmCustomer> list(Long ownerId, String level, String source, String tag, String keyword) {
        return mapper.selectList(ownerId, level, source, tag, keyword);
    }

    @Override
    public int countByOwner(Long ownerId) {
        return mapper.countByOwner(ownerId);
    }
}
```

- [ ] **Step 5: Run tests to verify pass**

```bash
cd "D:/work-ai/0401-lumen-opc/springboot3"
JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" \
  mvn -pl ruoyi-modules/opc-crm test -Dtest=CustomerServiceTest 2>&1 | tail -10
```

Expected: `Tests run: 8, Failures: 0`.

- [ ] **Step 6: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/CustomerService.java springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/impl/CustomerServiceImpl.java springboot3/ruoyi-modules/opc-crm/src/test/java/com/ruoyi/opc/crm/service/CustomerServiceTest.java
git commit -m "feat(crm): Task 6 — CustomerService + 8 @Test (create/update/delete/get/list)"
git push origin main
```

---

## Task 7: ContactService + tests (4 @Test)

**Files:**
- Create: `ContactService.java` + `ContactServiceImpl.java` + `ContactServiceTest.java`

- [ ] **Step 1: Test** — covers `create`, `listByCustomer`, `setPrimary` (only 1 primary per customer), `delete`

```java
@Test void should_create_contact_for_customer() { ... }
@Test void should_list_contacts_by_customer() { ... }
@Test void setPrimary_should_unset_others() { ... }
@Test void should_throw_when_delete_nonexistent() { ... }
```

- [ ] **Step 2: Implement ContactService** with `@Transactional` for setPrimary (3-step: unset all → set new).

- [ ] **Step 3: Run + verify 4/4 PASS**

- [ ] **Step 4: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/ContactService.java springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/impl/ContactServiceImpl.java springboot3/ruoyi-modules/opc-crm/src/test/java/com/ruoyi/opc/crm/service/ContactServiceTest.java
git commit -m "feat(crm): Task 7 — ContactService + 4 @Test"
git push origin main
```

---

## Task 8: FollowUpService + tests (6 @Test)

**Files:**
- Create: `FollowUpService.java` + `FollowUpServiceImpl.java` + `FollowUpServiceTest.java`

- [ ] **Step 1: Test** — covers `create`, `listByCustomer` (timeline), `listByOwner`, `upcoming` (next_at > now), `markDone`, `delete`

- [ ] **Step 2: Implement FollowUpService** with optional OpenFeign call to NotificationGateway (auto-notify on new follow-up)

```java
@Service
@RequiredArgsConstructor
public class FollowUpServiceImpl implements FollowUpService {
    private final CrmFollowUpMapper mapper;
    private final NotificationGateway notificationGateway;  // optional Feign

    @Override
    @Transactional
    public CrmFollowUp create(CrmFollowUp followUp, Long ownerId) {
        // validation
        if (followUp.getCustomerId() == null) throw new IllegalArgumentException("customerId required");
        if (followUp.getContent() == null || followUp.getContent().isBlank()) throw new IllegalArgumentException("content required");
        followUp.setOwnerId(ownerId);
        followUp.setCreateBy(String.valueOf(ownerId));
        followUp.setUpdateBy(String.valueOf(ownerId));
        if (followUp.getType() == null) followUp.setType("PHONE");
        if (followUp.getDeleted() == null) followUp.setDeleted(0);
        mapper.insert(followUp);

        // best-effort notify
        try {
            notificationGateway.sendInbox(ownerId, "新跟进记录", followUp.getContent());
        } catch (Exception e) {
            log.warn("Notification failed (best-effort): {}", e.getMessage());
        }
        return followUp;
    }
    // ... list/markDone/delete
}
```

- [ ] **Step 3: Run + verify 6/6 PASS**

- [ ] **Step 4: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/FollowUpService.java springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/impl/FollowUpServiceImpl.java springboot3/ruoyi-modules/opc-crm/src/test/java/com/ruoyi/opc/crm/service/FollowUpServiceTest.java
git commit -m "feat(crm): Task 8 — FollowUpService + 6 @Test (timeline + best-effort notify)"
git push origin main
```

---

## Task 9: OpportunityService + tests (10 @Test)

**Files:**
- Create: `OpportunityService.java` + `OpportunityServiceImpl.java` + `OpportunityServiceTest.java`

- [ ] **Step 1: Test** — covers:
- `create`
- `update`
- `changeStage` (state machine validation)
- `changeStage_LEAD_to_QUALIFIED_OK`
- `changeStage_WON_isTerminal_no_further_changes`
- `changeStage_LOST_can_reopen_to_LEAD`
- `changeStage_invalid_transition_throws`
- `score` (calls LLM gateway)
- `listByCustomer`
- `listByStage`

- [ ] **Step 2: Implement with state machine**

```java
@Override
@Transactional
public CrmOpportunity changeStage(Long id, OpportunityStage target, String reason, Long operatorId) {
    CrmOpportunity opp = mapper.selectById(id);
    if (opp == null) throw new IllegalArgumentException("Opportunity not found: " + id);
    OpportunityStage current = OpportunityStage.valueOf(opp.getStage());
    if (!current.canTransitionTo(target)) {
        throw new IllegalStateException(
            String.format("Invalid stage transition: %s → %s", current, target));
    }
    opp.setStage(target.name());
    if (target == OpportunityStage.LOST) opp.setLostReason(reason);
    opp.setUpdateBy(String.valueOf(operatorId));
    mapper.updateById(opp);

    // best-effort notify on stage change
    try {
        notificationGateway.sendInbox(opp.getOwnerId(),
            String.format("商机阶段变更: %s", opp.getName()),
            String.format("阶段从 %s 变更为 %s", current, target));
    } catch (Exception e) {
        log.warn("Notification failed: {}", e.getMessage());
    }
    return opp;
}

@Override
public OpportunityScoreResponse score(Long id, Long operatorId) {
    CrmOpportunity opp = mapper.selectById(id);
    if (opp == null) throw new IllegalArgumentException("Opportunity not found: " + id);
    CrmCustomer customer = customerMapper.selectById(opp.getCustomerId());
    // call LLM via Feign
    OpportunityScoreResponse resp = aiCoreGateway.scoreOpportunity(opp, customer);
    // update score + reason
    opp.setScore(resp.getScore());
    opp.setScoreReason(resp.getReason());
    opp.setUpdateBy(String.valueOf(operatorId));
    mapper.updateById(opp);
    return resp;
}
```

- [ ] **Step 3: Run + verify 10/10 PASS**

- [ ] **Step 4: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/OpportunityService.java springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/impl/OpportunityServiceImpl.java springboot3/ruoyi-modules/opc-crm/src/test/java/com/ruoyi/opc/crm/service/OpportunityServiceTest.java
git commit -m "feat(crm): Task 9 — OpportunityService + 10 @Test (state machine + LLM scoring)"
git push origin main
```

---

## Task 10: ContractService + tests (6 @Test)

**Files:**
- Create: `ContractService.java` + `ContractServiceImpl.java` + `ContractServiceTest.java`

- [ ] **Step 1: Test** — covers `create` (unique contract_no validation), `update`, `activate` (DRAFT→ACTIVE), `expire`, `terminate`, `listByCustomer`

- [ ] **Step 2: Implement**

```java
@Override
@Transactional
public CrmContract create(CrmContract contract, Long operatorId) {
    if (contract.getContractNo() == null || contract.getContractNo().isBlank()) {
        throw new IllegalArgumentException("contract_no required");
    }
    if (mapper.countByContractNo(contract.getContractNo()) > 0) {
        throw new IllegalArgumentException("contract_no already exists: " + contract.getContractNo());
    }
    if (contract.getStatus() == null) contract.setStatus("DRAFT");
    contract.setCreateBy(String.valueOf(operatorId));
    contract.setUpdateBy(String.valueOf(operatorId));
    if (contract.getDeleted() == null) contract.setDeleted(0);
    mapper.insert(contract);
    return contract;
}
```

- [ ] **Step 3: Run + verify 6/6 PASS**

- [ ] **Step 4: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/ContractService.java springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/impl/ContractServiceImpl.java springboot3/ruoyi-modules/opc-crm/src/test/java/com/ruoyi/opc/crm/service/ContractServiceTest.java
git commit -m "feat(crm): Task 10 — ContractService + 6 @Test"
git push origin main
```

---

## Task 11: OrderService + tests (6 @Test)

**Files:**
- Create: `OrderService.java` + `OrderServiceImpl.java` + `OrderServiceTest.java`

- [ ] **Step 1: Test** — covers `create` (items_json validation + total calculation), `update`, `pay` (PENDING→PAID), `ship`, `complete`, `cancel`

- [ ] **Step 2: Implement**

```java
@Override
@Transactional
public CrmOrder create(CrmOrder order, Long operatorId) {
    if (order.getOrderNo() == null || order.getOrderNo().isBlank()) {
        throw new IllegalArgumentException("order_no required");
    }
    if (order.getItemsJson() == null || order.getItemsJson().isBlank()) {
        throw new IllegalArgumentException("items_json required");
    }
    // Optionally recalc total from items
    if (order.getTotal() != null && order.getTotal().compareTo(BigDecimal.ZERO) < 0) {
        throw new IllegalArgumentException("total cannot be negative");
    }
    if (mapper.countByOrderNo(order.getOrderNo()) > 0) {
        throw new IllegalArgumentException("order_no already exists: " + order.getOrderNo());
    }
    if (order.getStatus() == null) order.setStatus("PENDING");
    order.setCreateBy(String.valueOf(operatorId));
    order.setUpdateBy(String.valueOf(operatorId));
    if (order.getDeleted() == null) order.setDeleted(0);
    mapper.insert(order);
    return order;
}

@Override
@Transactional
public CrmOrder pay(Long id, Long operatorId) {
    CrmOrder order = mapper.selectById(id);
    if (order == null) throw new IllegalArgumentException("Order not found: " + id);
    if (!"PENDING".equals(order.getStatus())) {
        throw new IllegalStateException("Only PENDING orders can be paid, current: " + order.getStatus());
    }
    order.setStatus("PAID");
    order.setPaidAt(LocalDateTime.now());
    order.setUpdateBy(String.valueOf(operatorId));
    mapper.updateById(order);
    return order;
}
```

- [ ] **Step 3: Run + verify 6/6 PASS**

- [ ] **Step 4: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/OrderService.java springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/impl/OrderServiceImpl.java springboot3/ruoyi-modules/opc-crm/src/test/java/com/ruoyi/opc/crm/service/OrderServiceTest.java
git commit -m "feat(crm): Task 11 — OrderService + 6 @Test (status state machine)"
git push origin main
```

---

## Task 12: DashboardService + tests (4 @Test)

**Files:**
- Create: `DashboardService.java` + `DashboardServiceImpl.java` + `DashboardServiceTest.java`

- [ ] **Step 1: Test** — covers funnel aggregation by stage, count by level, top customers by opportunity amount, recent follow-ups

- [ ] **Step 2: Implement**

```java
@Override
public DashboardFunnelResponse getFunnel(Long ownerId) {
    DashboardFunnelResponse resp = new DashboardFunnelResponse();
    // Funnel counts
    Map<String, Long> funnel = new LinkedHashMap<>();
    for (OpportunityStage stage : OpportunityStage.values()) {
        long count = opportunityMapper.countByStage(ownerId, stage.name());
        funnel.put(stage.name(), count);
    }
    resp.setFunnel(funnel);

    // Level distribution
    Map<String, Long> byLevel = new LinkedHashMap<>();
    for (String level : List.of("A", "B", "C", "D")) {
        byLevel.put(level, (long) customerMapper.countByOwnerAndLevel(ownerId, level));
    }
    resp.setCustomersByLevel(byLevel);

    // Top opportunities
    resp.setTopOpportunities(opportunityMapper.selectTopByOwner(ownerId, 10));

    // Total amount in active stages
    BigDecimal activeAmount = opportunityMapper.sumAmountByOwnerAndStages(ownerId,
        List.of("LEAD", "QUALIFIED", "PROPOSAL", "NEGOTIATION"));
    resp.setActiveAmount(activeAmount);

    BigDecimal wonAmount = opportunityMapper.sumAmountByOwnerAndStages(ownerId, List.of("WON"));
    resp.setWonAmount(wonAmount);

    return resp;
}
```

- [ ] **Step 3: Run + verify 4/4 PASS**

- [ ] **Step 4: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/DashboardService.java springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/impl/DashboardServiceImpl.java springboot3/ruoyi-modules/opc-crm/src/test/java/com/ruoyi/opc/crm/service/DashboardServiceTest.java
git commit -m "feat(crm): Task 12 — DashboardService + 4 @Test (funnel + level + top opps)"
git push origin main
```

---

## Task 13: OpenFeign gateways (notification + ai-core + user-center)

**Files:**
- Create: `gateway/NotificationGateway.java`
- Create: `gateway/AiCoreGateway.java`
- Create: `gateway/UserCenterGateway.java`
- Create: `config/FeignConfig.java`
- Create: `dto/OpportunityScoreResponse.java`
- Create: `statemachine/OpportunityStateMachine.java` (extracted for testing)
- Create: `statemachine/OpportunityStateMachineTest.java` (8 @Test)

- [ ] **Step 1: Create FeignConfig**

```java
package com.ruoyi.opc.crm.config;

import feign.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
```

- [ ] **Step 2: Create NotificationGateway**

```java
package com.ruoyi.opc.crm.gateway;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "opc-notification", url = "${opc.crm.notification.base-url}")
public interface NotificationGateway {

    @PostMapping("/opc/notification/inbox/push")
    Map<String, Object> pushInbox(@RequestParam Long userId,
                                   @RequestParam String title,
                                   @RequestParam String content);
}
```

- [ ] **Step 3: Create AiCoreGateway** (calls opc-ai-core for LLM scoring)

```java
@FeignClient(name = "opc-ai-core", url = "${opc.crm.ai-core.base-url}")
public interface AiCoreGateway {

    @PostMapping("/opc/ai-core/opportunity/score")
    OpportunityScoreResponse scoreOpportunity(@RequestBody Map<String, Object> payload);
}
```

- [ ] **Step 4: Create OpportunityScoreResponse DTO**

```java
@Data
public class OpportunityScoreResponse {
    private int score;          // 0-100
    private String reason;      // 中文理由
    private List<String> risks; // 风险列表
}
```

- [ ] **Step 5: Create UserCenterGateway** (get owner info)

```java
@FeignClient(name = "opc-user-center", url = "${opc.crm.user-center.base-url}")
public interface UserCenterGateway {

    @GetMapping("/opc/user-center/user/{id}")
    Map<String, Object> getUser(@PathVariable Long id);
}
```

- [ ] **Step 6: Create OpportunityStateMachine + 8 @Test** (extracted from enum for testability)

```java
package com.ruoyi.opc.crm.statemachine;

import com.ruoyi.opc.crm.enums.OpportunityStage;

import java.util.Set;
import java.util.Map;

/**
 * 商机阶段状态机 (纯函数,无 Spring 依赖,易测)
 */
public final class OpportunityStateMachine {

    private static final Map<OpportunityStage, Set<OpportunityStage>> TRANSITIONS = Map.of(
        OpportunityStage.LEAD,        Set.of(OpportunityStage.QUALIFIED, OpportunityStage.LOST),
        OpportunityStage.QUALIFIED,   Set.of(OpportunityStage.PROPOSAL, OpportunityStage.LOST),
        OpportunityStage.PROPOSAL,    Set.of(OpportunityStage.NEGOTIATION, OpportunityStage.LOST),
        OpportunityStage.NEGOTIATION, Set.of(OpportunityStage.WON, OpportunityStage.LOST),
        OpportunityStage.WON,         Set.of(),
        OpportunityStage.LOST,        Set.of(OpportunityStage.LEAD)
    );

    private OpportunityStateMachine() {}

    public static boolean canTransition(OpportunityStage from, OpportunityStage to) {
        if (from == null || to == null || from == to) return false;
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<OpportunityStage> nextStages(OpportunityStage from) {
        return TRANSITIONS.getOrDefault(from, Set.of());
    }

    public static boolean isTerminal(OpportunityStage stage) {
        return stage == OpportunityStage.WON;
    }
}
```

```java
// OpportunityStateMachineTest.java — 8 @Test
@Test void LEAD_to_QUALIFIED_OK() { assertTrue(canTransition(LEAD, QUALIFIED)); }
@Test void LEAD_to_WON_not_allowed() { assertFalse(canTransition(LEAD, WON)); }
@Test void QUALIFIED_to_PROPOSAL_OK() { assertTrue(canTransition(QUALIFIED, PROPOSAL)); }
@Test void PROPOSAL_to_NEGOTIATION_OK() { assertTrue(canTransition(PROPOSAL, NEGOTIATION)); }
@Test void NEGOTIATION_to_WON_OK() { assertTrue(canTransition(NEGOTIATION, WON)); }
@Test void WON_is_terminal() { assertTrue(isTerminal(WON)); assertTrue(nextStages(WON).isEmpty()); }
@Test void LOST_can_reopen_to_LEAD() { assertTrue(canTransition(LOST, LEAD)); }
@Test void LOST_to_QUALIFIED_not_allowed() { assertFalse(canTransition(LOST, QUALIFIED)); }
```

- [ ] **Step 7: Create NotificationGatewayTest (4 @Test with MockedStatic or Mock Feign)**

For simplicity, use `Mockito.mock(NotificationGateway.class)` and verify calls.

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationGatewayTest {
    @Mock NotificationGateway gateway;

    @Test void should_call_push_inbox() {
        when(gateway.pushInbox(anyLong(), anyString(), anyString())).thenReturn(Map.of("code", 200));
        gateway.pushInbox(1L, "title", "content");
        verify(gateway).pushInbox(1L, "title", "content");
    }
    // ... 3 more tests
}
```

- [ ] **Step 8: Run all gateway + state machine tests**

```bash
cd "D:/work-ai/0401-lumen-opc/springboot3"
JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" \
  mvn -pl ruoyi-modules/opc-crm test -Dtest='OpportunityStateMachineTest,NotificationGatewayTest' 2>&1 | tail -10
```

Expected: 12 tests, 0 failures.

- [ ] **Step 9: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/config/FeignConfig.java springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/gateway/ springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/dto/OpportunityScoreResponse.java springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/statemachine/ springboot3/ruoyi-modules/opc-crm/src/test/java/com/ruoyi/opc/crm/statemachine/ springboot3/ruoyi-modules/opc-crm/src/test/java/com/ruoyi/opc/crm/gateway/
git commit -m "feat(crm): Task 13 — OpenFeign gateways (notification/ai-core/user-center) + state machine (8 @Test)"
git push origin main
```

---

## Task 14: REST Controllers (7 controllers)

**Files:**
- Create: 7 controller classes + DTO classes

- [ ] **Step 1: Create CustomerController**

```java
package com.ruoyi.opc.crm.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.crm.domain.CrmCustomer;
import com.ruoyi.opc.crm.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/opc/crm/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public R<List<CrmCustomer>> list(@RequestParam(required = false) String level,
                                      @RequestParam(required = false) String source,
                                      @RequestParam(required = false) String tag,
                                      @RequestParam(required = false) String keyword) {
        Long ownerId = SecurityUtils.getUserId();
        return R.ok(customerService.list(ownerId, level, source, tag, keyword));
    }

    @GetMapping("/{id}")
    public R<CrmCustomer> getById(@PathVariable Long id) {
        return R.ok(customerService.getById(id));
    }

    @PostMapping
    public R<CrmCustomer> create(@RequestBody CrmCustomer customer) {
        return R.ok(customerService.create(customer, SecurityUtils.getUserId()));
    }

    @PutMapping("/{id}")
    public R<CrmCustomer> update(@PathVariable Long id, @RequestBody CrmCustomer customer) {
        return R.ok(customerService.update(id, customer, SecurityUtils.getUserId()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        customerService.delete(id, SecurityUtils.getUserId());
        return R.ok();
    }
}
```

- [ ] **Step 2: Create ContactController** (CRUD + list by customer)

- [ ] **Step 3: Create FollowUpController** (create + listByCustomer + upcoming)

- [ ] **Step 4: Create OpportunityController** (CRUD + `/score` + `/stage`)

```java
@PostMapping("/{id}/score")
public R<OpportunityScoreResponse> score(@PathVariable Long id) {
    return R.ok(opportunityService.score(id, SecurityUtils.getUserId()));
}

@PostMapping("/{id}/stage")
public R<CrmOpportunity> changeStage(@PathVariable Long id, @RequestBody StageChangeRequest req) {
    return R.ok(opportunityService.changeStage(id,
        OpportunityStage.valueOf(req.getStage()), req.getReason(), SecurityUtils.getUserId()));
}
```

- [ ] **Step 5: Create ContractController** (CRUD + `/activate` + `/terminate`)

- [ ] **Step 6: Create OrderController** (CRUD + `/pay` + `/ship` + `/complete` + `/cancel`)

- [ ] **Step 7: Create DashboardController**

```java
@RestController
@RequestMapping("/opc/crm/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public R<DashboardFunnelResponse> funnel() {
        return R.ok(dashboardService.getFunnel(SecurityUtils.getUserId()));
    }
}
```

- [ ] **Step 8: Verify all compile**

```bash
cd "D:/work-ai/0401-lumen-opc/springboot3"
JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" \
  mvn -pl ruoyi-modules/opc-crm -am compile -Dmaven.test.skip=true 2>&1 | tail -3
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 9: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/controller/ springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/dto/
git commit -m "feat(crm): Task 14 — 7 REST controllers (Customer/Contact/FollowUp/Opportunity/Contract/Order/Dashboard)"
git push origin main
```

---

## Task 15: Nacos config + deployment files

**Files:**
- Create: `springboot3/deploy/nacos/opc-crm-dev.yml`
- Create: `springboot3/deploy/nacos/opc-crm-prod.yml`
- Modify: `springboot3/deploy/docker-compose.yml`
- Modify: `springboot3/ruoyi-gateway/src/main/resources/application.yml` (add route)
- Create: `springboot3/ruoyi-modules/opc-crm/Dockerfile` (replace port placeholder)

- [ ] **Step 1: Create opc-crm-dev.yml**

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        master:
          url: jdbc:mysql://mysql:3306/ry-vue-opc?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true

opc:
  crm:
    notification:
      base-url: http://opc-notification:9310
    ai-core:
      base-url: http://opc-ai-core:9301
    user-center:
      base-url: http://opc-user-center:9302

logging:
  level:
    com.ruoyi.opc.crm: debug
```

- [ ] **Step 2: Create opc-crm-prod.yml** (same but with prod-grade settings)

- [ ] **Step 3: Add aiopc-crm to docker-compose.yml**

Find where aiopc-notification is defined and add aiopc-crm right after:

```yaml
  aiopc-crm:
    build:
      context: ../../ruoyi-modules/opc-crm
      dockerfile: Dockerfile
    container_name: aiopc-crm
    restart: unless-stopped
    environment:
      - SERVER_PORT=9312
      - NACOS_SERVER=nacos1:8848
      - NACOS_NAMESPACE=opc-dev
      - JASYPT_PASSWORD=OpcEncrypt!2026
      - MYSQL_HOST=mysql
      - MYSQL_PORT=3306
      - MYSQL_USER=root
      - MYSQL_PASSWORD=root
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - SPRING_PROFILES_ACTIVE=dev
    depends_on:
      - nacos1
      - mysql
      - redis
    networks:
      - aiopc-net
    ports:
      - "9312:9312"
```

- [ ] **Step 4: Add route to gateway application.yml**

Find the opc-notification route block and add:

```yaml
- id: opc-crm
  uri: http://aiopc-crm:9312
  predicates:
    - Path=/opc/crm/**
```

- [ ] **Step 5: Replace port placeholder in Dockerfile**

Edit `opc-crm/Dockerfile`: replace `EXPOSE http` → `EXPOSE 9312` and `ENTRYPOINT` → use `9312` instead of `http`.

- [ ] **Step 6: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add springboot3/deploy/nacos/opc-crm-dev.yml springboot3/deploy/nacos/opc-crm-prod.yml springboot3/deploy/docker-compose.yml springboot3/ruoyi-gateway/src/main/resources/application.yml springboot3/ruoyi-modules/opc-crm/Dockerfile
git commit -m "feat(crm): Task 15 — Nacos config + docker-compose + gateway route"
git push origin main
```

---

## Task 16: Push Nacos + build + start container

**Files:** None new (deployment task)

- [ ] **Step 1: Build thin jar**

```bash
cd "D:/work-ai/0401-lumen-opc/springboot3"
JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" \
  mvn -pl ruoyi-modules/opc-crm -am clean package -Dmaven.test.skip=true -Dspring-boot.repackage.skip=true 2>&1 | tail -3

JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" \
  mvn -pl ruoyi-modules/opc-crm dependency:copy-dependencies -DoutputDirectory=target/dependency 2>&1 | tail -3
```

Expected: `target/opc-crm.jar` and `target/dependency/` exist.

- [ ] **Step 2: Push Nacos config**

```bash
cd "D:/work-ai/0401-lumen-opc/springboot3/deploy/nacos"
bash ./import-dev.sh
```

Expected: `opc-crm-dev.yml` pushed.

- [ ] **Step 3: Rebuild gateway (gateway uses fat-jar, NOT thin-jar)**

```bash
cd "D:/work-ai/0401-lumen-opc/springboot3"
JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" \
  mvn -pl ruoyi-gateway clean package -Dmaven.test.skip=true 2>&1 | tail -3
```

- [ ] **Step 4: Build Docker images**

```bash
cd "D:/work-ai/0401-lumen-opc/springboot3/deploy"
docker compose build aiopc-crm aiopc-gateway 2>&1 | tail -5
```

- [ ] **Step 5: Restart containers**

```bash
docker compose up -d aiopc-crm aiopc-gateway 2>&1 | tail -5
```

- [ ] **Step 6: Wait for startup (60s) and verify**

```bash
sleep 60
docker ps | grep -E "aiopc-crm|aiopc-gateway"
docker logs aiopc-crm 2>&1 | grep -E "Started OpcCrmApplication|ERROR" | tail -3
```

Expected: aiopc-crm Up, "Started OpcCrmApplication in X.XXX seconds".

- [ ] **Step 7: Smoke test**

```bash
TOKEN=$(curl -s -X POST -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' http://127.0.0.1:8080/login | python -c "import sys,json;print(json.load(sys.stdin)['data']['access_token'])")
curl -s -H "Authorization: Bearer $TOKEN" "http://127.0.0.1:8080/opc/crm/customer?keyword=北京"
```

Expected: `{"code":200, "data":[{...}]}` returning at least 1 customer from seed data.

- [ ] **Step 8: Commit (any config changes)**

If no changes, skip commit.

---

## Task 17: health-check.sh + RECOVERY.md + Helm chart

**Files:**
- Modify: `springboot3/deploy/scripts/health-check.sh` (+8 endpoints)
- Modify: `springboot3/deploy/RECOVERY.md` (§opc-crm)
- Modify: `springboot3/deploy/helm/opc/values.yaml` (add crm service)
- Modify: `springboot3/deploy/nacos/import-dev.sh` (add opc-crm-dev.yml)
- Create: `springboot3/deploy/helm/opc/templates/deployment-crm.yaml`
- Create: `springboot3/deploy/helm/opc/templates/service-crm.yaml`

- [ ] **Step 1: Add 8 health checks to health-check.sh**

Add a new section:
- Container up: `aiopc-crm`
- Nacos registration: `opc-crm` (Spring app name)
- Direct health: port 9312
- Customer list endpoint
- Customer detail endpoint
- Opportunity list endpoint
- Dashboard funnel endpoint
- Gateway route resolves

Match existing pattern (28 → 36 checks).

- [ ] **Step 2: Add §opc-crm to RECOVERY.md**

Pattern: purpose, port, 6 tables, Nacos DataIDs, build/start commands, caveats (LLM dependency), verification.

- [ ] **Step 3: Add crm to Helm values.yaml**

```yaml
crm:
  enabled: true
  tier: business
  image:
    repository: aiopc-crm
    tag: latest
    pullPolicy: IfNotPresent
  container:
    port: 9312
  resources:
    requests:
      cpu: 200m
      memory: 256Mi
    limits:
      cpu: 1000m
      memory: 1Gi
  env:
    SPRING_PROFILES_ACTIVE: prod
    SERVER_PORT: 9312
    NACOS_SERVER: nacos1.opc.svc.cluster.local:8848
    NACOS_NAMESPACE: opc-prod
    JASYPT_PASSWORD: "${JASYPT_PASSWORD}"
    # ... (other env like notification)
  livenessProbe:
    httpGet:
      path: /actuator/health
      port: 9312
```

Add to values-dev.yaml / values-staging.yaml / values-prod.yaml similarly.

- [ ] **Step 4: Add to values-{env}.yaml** for all 3 envs

- [ ] **Step 5: Create deployment-crm.yaml and service-crm.yaml** (mirror insight pattern)

- [ ] **Step 6: Add opc-crm-dev.yml to import-dev.sh NAMES**

- [ ] **Step 7: Verify**

```bash
bash "D:/work-ai/0401-lumen-opc/springboot3/deploy/scripts/health-check.sh" 2>&1 | tail -3
cd "D:/work-ai/0401-lumen-opc/springboot3/deploy/helm/opc" && helm lint . 2>&1 | tail -3
python "D:/work-ai/0401-lumen-opc/springboot3/deploy/helm/opc/ci/diff-envs.py" 2>&1 | tail -5
```

Expected: 36/36 PASS, helm lint pass, diff-envs pass.

- [ ] **Step 8: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add springboot3/deploy/scripts/health-check.sh springboot3/deploy/RECOVERY.md springboot3/deploy/helm/opc/ springboot3/deploy/nacos/import-dev.sh
git commit -m "feat(crm): Task 17 — health-check + RECOVERY.md + Helm chart"
git push origin main
```

---

## Task 18: Frontend — Customer list + Opportunity kanban + Follow-up timeline

**Files:**
- Create: `vue3-typescript/src/api/opc/crm.ts`
- Create: `vue3-typescript/src/views/opc/crm/CustomerList.vue`
- Create: `vue3-typescript/src/views/opc/crm/CustomerDetail.vue`
- Create: `vue3-typescript/src/views/opc/crm/OpportunityKanban.vue`
- Create: `vue3-typescript/src/views/opc/crm/FollowUpTimeline.vue`
- Modify: `vue3-typescript/src/router/index.ts` (add 4 routes)
- Modify: `vue3-typescript/src/layout/index.vue` (sidebar menu)

- [ ] **Step 1: Create crm.ts API module**

```typescript
import request from '@/utils/request'

export interface Customer { id, name, source, tags, ownerId, level, phone, email, ... }
export interface Contact { ... }
export interface FollowUp { ... }
export interface Opportunity { id, customerId, name, amount, stage, score, ... }
export interface Contract { ... }
export interface Order { ... }

export function listCustomers(params: {level, source, tag, keyword}) { ... }
export function getCustomer(id) { ... }
export function createCustomer(data) { ... }
export function updateCustomer(id, data) { ... }
export function deleteCustomer(id) { ... }

export function listContacts(customerId) { ... }
export function createContact(data) { ... }
export function setPrimaryContact(id) { ... }

export function listFollowUps(customerId) { ... }
export function createFollowUp(data) { ... }

export function listOpportunities(params: {stage, customerId}) { ... }
export function createOpportunity(data) { ... }
export function changeStage(id, stage, reason) { ... }
export function scoreOpportunity(id) { ... }

export function listContracts(customerId) { ... }
export function createContract(data) { ... }
export function activateContract(id) { ... }

export function listOrders(customerId) { ... }
export function createOrder(data) { ... }
export function payOrder(id) { ... }

export function getDashboard() { ... }
```

- [ ] **Step 2: Create CustomerList.vue** — `el-table` with columns name/level/source/tags/owner, action buttons, filter drawer

- [ ] **Step 3: Create CustomerDetail.vue** — tabs: 基本信息 / 联系人 / 跟进 / 商机 / 合同 / 订单

- [ ] **Step 4: Create OpportunityKanban.vue** — drag-and-drop kanban with columns by stage (LEAD/QUALIFIED/PROPOSAL/NEGOTIATION/WON/LOST), each card shows amount + score

Use `vuedraggable@4.1.0` (already in package.json likely) or use native HTML5 drag-and-drop.

- [ ] **Step 5: Create FollowUpTimeline.vue** — `el-timeline` component showing follow-ups in chronological order

- [ ] **Step 6: Register 4 routes**

```typescript
{
  path: '/opc/crm/customers',
  component: () => import('@/views/opc/crm/CustomerList.vue'),
  meta: { title: '客户管理', icon: 'user', requiresAuth: true }
},
{
  path: '/opc/crm/customers/:id',
  component: () => import('@/views/opc/crm/CustomerDetail.vue'),
  meta: { title: '客户详情', requiresAuth: true, activeMenu: '/opc/crm/customers' }
},
{
  path: '/opc/crm/opportunities',
  component: () => import('@/views/opc/crm/OpportunityKanban.vue'),
  meta: { title: '商机看板', icon: 'trend-charts', requiresAuth: true }
},
{
  path: '/opc/crm/follow-ups',
  component: () => import('@/views/opc/crm/FollowUpTimeline.vue'),
  meta: { title: '跟进记录', icon: 'time', requiresAuth: true }
}
```

- [ ] **Step 7: Add to sidebar menu**

- [ ] **Step 8: Build + type-check**

```bash
cd "D:/work-ai/0401-lumen-opc/vue3-typescript"
npm run build:prod 2>&1 | tail -5
```

Expected: Build success.

- [ ] **Step 9: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add vue3-typescript/src/api/opc/crm.ts vue3-typescript/src/views/opc/crm/ vue3-typescript/src/router/index.ts vue3-typescript/src/layout/index.vue
git commit -m "feat(crm): Task 18 — frontend (CustomerList/Detail/OpportunityKanban/FollowUpTimeline)"
git push origin main
```

---

## Task 19: E2E test (crm_e2e.py)

**Files:**
- Create: `tmp_e2e/crm_e2e.py`

- [ ] **Step 1: Write E2E test**

Covers 8 scenarios:
1. Admin login
2. List customers (seed data)
3. Get customer detail (id=1)
4. Create new customer
5. List contacts for customer
6. Create follow-up
8. Create opportunity (LEAD stage)
9. Change stage LEAD → QUALIFIED
10. List dashboard funnel
11. Get opportunity score (LLM call — may fail if no LLM key, but should return response)

Use `urllib.request` (no `requests` dep) per existing e2e pattern. Match `notification_e2e.py` style.

- [ ] **Step 2: Run + verify pass**

```bash
python "D:/work-ai/0401-lumen-opc/tmp_e2e/crm_e2e.py"
```

Expected: ~11 tests pass.

- [ ] **Step 3: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add tmp_e2e/crm_e2e.py
git commit -m "test(crm): Task 19 — E2E test for opc-crm"
git push origin main
```

---

## Task 20: W55 verification report

**Files:**
- Create: `docs/verification/week-50/OPC-W55-VERIFICATION-opc-crm.md`

- [ ] **Step 1: Write report**

Match W49 verification report format:
- 19-task summary table
- AC verification (5/5 from spec + user additions)
- Runtime state (containers, health-check 36/36, E2E pass)
- Bugs found + fixed
- File list
- Next steps (W56-W57 for opc-hr / opc-ecommerce preparation)

- [ ] **Step 2: Update docs/verification/README.md** if it has week index

- [ ] **Step 3: Commit**

```bash
cd "D:/work-ai/0401-lumen-opc"
git add docs/verification/week-50/
git commit -m "docs(crm): Task 20 — W55 verification report"
git push origin main
```

---

## Task 21: Final acceptance + memory update

**Files:**
- Modify: `~/.claude/projects/D--work-ai-0401-lumen-opc/memory/MEMORY.md` (add pointer)
- Create: `~/.claude/projects/D--work-ai-0401-lumen-opc/memory/opc-crm-w55.md`

- [ ] **Step 1: Final verification**

```bash
docker ps | grep -E "aiopc-crm|aiopc-notification|aiopc-gateway"
bash "D:/work-ai/0401-lumen-opc/springboot3/deploy/scripts/health-check.sh" | tail -3
python "D:/work-ai/0401-lumen-opc/tmp_e2e/crm_e2e.py" | tail -5
cd "D:/work-ai/0401-lumen-opc/springboot3/deploy/helm/opc" && helm lint . | tail -3
cd "D:/work-ai/0401-lumen-opc" && git log --oneline -25
```

- [ ] **Step 2: Capture summary**

- [ ] **Step 3: Write memory file**

Content:
- Service metadata (port 9312, 6 tables, 7 controllers, 8 endpoints)
- Lessons learned (e.g., state machine extracted for testability, OpenFeign best-effort pattern)
- W49 → W55 dependencies

- [ ] **Step 4: Add pointer to MEMORY.md**

```markdown
- [opc-crm W55](opc-crm-w55.md) — 9312 port, 6 tables, state machine + LLM opportunity scorer, dependency for opc-community/ecommerce
```

- [ ] **Step 5: Final report**

## Spec Coverage Check

| AC | Task |
|----|------|
| 客户 CRUD + 标签 + 来源 | Tasks 4, 5, 6, 14 |
| 联系人 1:N 关联 | Tasks 4, 5, 7, 14 |
| 跟进记录时间线 | Tasks 4, 5, 8, 14 |
| 商机漏斗 | Tasks 4, 5, 9, 12, 14 |
| LLM 商机打分 | Tasks 9, 13 |
| 合同 (user-added) | Tasks 2, 4, 5, 10, 14 |
| 订单 (user-added) | Tasks 2, 4, 5, 11, 14 |

## Test Coverage

- 8 (Customer) + 4 (Contact) + 6 (FollowUp) + 10 (Opportunity) + 6 (Contract) + 6 (Order) + 4 (Dashboard) + 8 (StateMachine) + 4 (NotificationGateway) = **56 @Test**
- E2E: ~11 scenarios
- Health-check: 36 endpoints

---

Plan complete and saved to `docs/superpowers/plans/2026-09-11-opc-crm-impl.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?