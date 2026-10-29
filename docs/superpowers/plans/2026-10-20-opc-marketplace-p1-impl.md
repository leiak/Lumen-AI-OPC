# opc-marketplace P1 (MVP 商业闭环) Implementation Plan

> **审计说明（2026-10-29）**
>
> 这份 plan 的 checkbox 已按 `/tmp/opc-plan-audit-rubric.md` 的判据逐条核实过（只读仓库 + git 历史）。
>
> - **审计日期**：2026-10-29
> - **plan 目标一句话**：建 `opc-marketplace` 微服务（端口 9317）P1 MVP —— 作者入驻 + Agent/Module 上架 + 安装（只支持免费 Agent）+ 评价 + 类目 + 标签，配 Vue 3 公开店面。
> - **结果**：**勾了 66 / 没勾 19 / 共 85**。
> - **没勾的 19 条卡在哪**：16 条是「跑一次看输出」类（编译 / 单测 / 启动 / smoke / helm lint / 前端 build / health-check / e2e）—— 仓库里没有 CI 记录、没有 surefire 报告、没有测试报告文件，按规约一律不勾；3 条是 Task 14（memory 文件 / MEMORY.md / 最终提交），**确实一件都没做**。
> - **数字对不上**：plan 标称 **28 endpoints**，P1 实际交付 **29**（6 个 controller），今天仓库里是 **34**（P2 又加了 `SubscriptionController` 5 个）；plan 标称 **60+ 单测**，实际 **172 个 `@Test`**（"60+" 这个下限成立，但验收表写的 "63 全 PASS" 与 Task 8.5 写的 "68 tests pass" 都已严重过期）。
> - 完整逐条审计见 `docs/superpowers/plans/audit/2026-10-20-opc-marketplace-p1-impl.md`。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `opc-marketplace` micro-service (port 9317) Phase 1 MVP — author onboarding + Agent/Module listing + install (free Agent only) + reviews + categories + tags. Public browsing via Vue 3 storefront. 6 new tables, 6 controllers, 28 endpoints, 60+ unit tests, 12+ e2e tests.

**Architecture:** Spring Boot 3 + MyBatis thin jar in `springboot3/ruoyi-modules/opc-marketplace/`. Follows opc-crm W50 + opc-community W52 patterns: `@EnableCustomConfig` + `@ComponentScan("com.ruoyi.opc")` + `@EnableRyFeignClients(basePackages = {"com.ruoyi.system.api","com.ruoyi.opc"})`. Lombok 4 件套 + `@JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone="GMT+8")` + `LocalDateTime`. Multi-tenant via `author_company_id` (作者方) 与 `tenant_company_id` (订阅方) 强隔离. SnowflakeId PK. Public GET wrappers in `ruoyi-gateway` whitelist; POST requires `@PreAuthorize`.

**Tech Stack:** Spring Boot 3.2 + Spring Cloud 2023 + Nacos + MyBatis 3.5 + Feign + Element Plus 2.13 + Vue 3.5 + TypeScript 5.6 + Vite 6.

**Design Doc:** `docs/superpowers/specs/2026-10-20-opc-marketplace-design.md` §6 P1

---

## File Structure

```
springboot3/ruoyi-modules/opc-marketplace/
├── Dockerfile                                        # Task 11 (thin jar)
├── pom.xml                                           # Task 1
└── src/main/
    ├── java/com/ruoyi/opc/marketplace/
    │   ├── OpcMarketplaceApplication.java            # Task 1
    │   ├── controller/                               # Task 7-9 (6 controllers)
    │   │   ├── PublicationController.java            # 10 endpoints (list/search/get/my/{id}/my/list/create/edit/submit-audit/online/offline)
    │   │   ├── InstallController.java                # 5 endpoints
    │   │   ├── AuthorController.java                 # 6 endpoints
    │   │   ├── ReviewController.java                 # 4 endpoints
    │   │   ├── CategoryController.java               # 3 endpoints
    │   │   └── TagController.java                    # 1 endpoint
    │   ├── service/                                  # Task 4-6 (6 services)
    │   │   ├── PublicationService.java               # + impl
    │   │   ├── InstallService.java                   # + impl (calls AgentHubClient)
    │   │   ├── AuthorService.java                    # + impl
    │   │   ├── ReviewService.java                    # + impl
    │   │   ├── CategoryService.java                  # + impl
    │   │   └── TagService.java                       # + impl
    │   ├── domain/                                   # Task 2 (6 entities)
    │   │   ├── MpAuthor.java
    │   │   ├── MpPublication.java
    │   │   ├── MpInstall.java
    │   │   ├── MpReview.java
    │   │   ├── MpCategory.java
    │   │   └── MpTag.java
    │   ├── mapper/                                   # Task 2 (6 mappers)
    │   ├── dto/                                      # Task 3 (10 DTOs)
    │   ├── enums/                                    # Task 2 (3 enums)
    │   ├── feign/                                    # Task 4
    │   │   └── AgentHubClient.java                   # P1 only: createAgentInstance
    │   └── util/
    │       └── AuthorGuard.java                      # 验 author_company_id
    └── resources/
        ├── application.yml                           # Task 1
        ├── bootstrap.yml                             # Task 1
        ├── logback.xml                               # Task 1
        └── mapper/marketplace/                       # Task 2 (6 XMLs)
            ├── MpAuthorMapper.xml
            ├── MpPublicationMapper.xml
            ├── MpInstallMapper.xml
            ├── MpReviewMapper.xml
            ├── MpCategoryMapper.xml
            └── MpTagMapper.xml
└── src/test/java/com/ruoyi/opc/marketplace/           # Task 10 (9 test classes)
    ├── controller/
    │   ├── PublicationControllerTest.java            # 14 tests
    │   ├── InstallControllerTest.java                # 8 tests
    │   ├── AuthorControllerTest.java                 # 10 tests
    │   ├── ReviewControllerTest.java                 # 8 tests
    │   ├── CategoryControllerTest.java               # 5 tests
    │   └── TagControllerTest.java                    # 4 tests
    ├── service/impl/
    │   ├── PublicationServiceTest.java               # 8 tests (核心)
    │   └── AuthorServiceRedactionTest.java           # 5 tests (公开端点脱敏;service 层,非 controller 层)
    └── MpMapperXmlParseTest.java                     # 1 test (6 mapper XML 解析 + statement 齐全)

springboot3/sql/migrations/
└── V20261020__opc_marketplace_schema.sql             # Task 1 (6 tables + indices + seeds)

springboot3/deploy/
├── nacos/
│   └── opc-marketplace-dev.yml                       # Task 11
├── docker-compose.yml                                # Task 11 (add aiopc-marketplace)
├── helm/opc/
│   ├── values.yaml                                   # Task 11 (add services.marketplace)
│   └── templates/
│       ├── deployment-marketplace.yaml               # Task 11
│       └── service-marketplace.yaml                  # Task 11
├── scripts/health-check.sh                           # Task 12 (add marketplace endpoints)
└── RECOVERY.md                                       # Task 11 (add 9317 section)

springboot3/ruoyi-gateway/src/main/resources/
└── application.yml                                   # Task 11 (route + whitelist)

RuoYi-Cloud-Vue3-typescript/src/
├── api/opc/marketplace.ts                            # Task 13 (28 endpoints)
└── views/opc/marketplace/
    ├── index.vue                                     # Marketplace.vue
    ├── detail.vue                                    # AgentDetail.vue
    ├── author-center.vue                             # AuthorCenter.vue (basic)
    └── author-apply.vue                              # AuthorApply.vue

docs/verification/week-80/
└── OPC-W80-VERIFICATION-opc-marketplace.md           # Task 12

tmp_e2e/
└── e2e_marketplace.py                                # Task 12 (12-step E2E)
```

---

### Task 1: 模块骨架 + Application + 6 表 SQL

**Files:**
- Create: `springboot3/ruoyi-modules/opc-marketplace/pom.xml`
- Create: `springboot3/ruoyi-modules/opc-marketplace/src/main/resources/application.yml`
- Create: `springboot3/ruoyi-modules/opc-marketplace/src/main/resources/bootstrap.yml`
- Create: `springboot3/ruoyi-modules/opc-marketplace/src/main/resources/logback.xml`
- Create: `springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/OpcMarketplaceApplication.java`
- Create: `springboot3/sql/migrations/V20261020__opc_marketplace_schema.sql`

- [x] **Step 1: Create pom.xml (thin jar pattern, opc-common + ruoyi-job)** （部分：pom 里没有按 plan 加 opc-agent-hub 依赖，也没有 ruoyi-job；pom 内注释说明 Agent 侧全走 Feign HTTP。artifactId=opc-marketplace 与 <phase>none</phase> 都对）

Copy `springboot3/ruoyi-modules/opc-community/pom.xml` exactly. Replace `artifactId`: `opc-community` → `opc-marketplace`. Port in comments → `9317`. Add dependency on `opc-agent-hub` for `OpcAgentInstance` references. Keep `<phase>none</phase>` on spring-boot:repackage (W48 thin jar 经验).

- [x] **Step 2: Create application.yml (port 9317)**

```yaml
server:
  port: 9317
  servlet:
    encoding:
      charset: UTF-8
      force: true

spring:
  application:
    name: opc-marketplace
  profiles:
    active: dev
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8
    default-property-inclusion: non_null
  main:
    allow-bean-definition-overriding: true
  cloud:
    allow-bean-definition-overriding: true

mybatis:
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: com.ruoyi.opc.marketplace.domain
  configuration:
    map-underscore-to-camel-case: true

opc:
  nacos:
    namespace: opc-dev
```

- [x] **Step 3: Create bootstrap.yml (Nacos opc-dev, fail-fast)**

```yaml
spring:
  cloud:
    nacos:
      server-addr: ${NACOS_HOST:nacos1}:8848
      config:
        namespace: opc-dev
        file-extension: yml
        refresh-enabled: true
        fail-fast: true
      discovery:
        namespace: opc-dev
        fail-fast: true
```

- [x] **Step 4: Create logback.xml**

Copy `opc-community/src/main/resources/logback.xml` exactly. Replace package `com.ruoyi.opc.community` → `com.ruoyi.opc.marketplace`.

- [x] **Step 5: Create OpcMarketplaceApplication.java**

```java
package com.ruoyi.opc.marketplace;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@EnableCustomConfig
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.ruoyi.system.api", "com.ruoyi.opc"})
@ComponentScan({"com.ruoyi.opc", "com.ruoyi.system"})
public class OpcMarketplaceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpcMarketplaceApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OPC MARKETPLACE 启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
```

- [x] **Step 6: Create V20261020__opc_marketplace_schema.sql**

6 tables per spec §3.1-3.2 (P1 subset: `mp_author`, `mp_publication`, `mp_install`, `mp_review`, `mp_category`, `mp_tag`). All with `BIGINT id` PK + `create_time/update_time/create_by/update_by/deleted`. Use `utf8mb4_unicode_ci`. Seed 6 categories (3 AGENT: 办公/财务/营销；3 MODULE: 数据/集成/工具) + 10 tags. Use MySQL 8 stored procedure for `IF NOT EXISTS` (W3 经验).

```sql
-- header
SET NAMES utf8mb4;

DELIMITER $$
CREATE PROCEDURE create_table_if_not_exists(IN tbl_name VARCHAR(64), IN ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = tbl_name) THEN
        SET @sql_text = ddl;
        PREPARE stmt FROM @sql_text;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- mp_author
CALL create_table_if_not_exists('mp_author', '
CREATE TABLE mp_author (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    author_company_id BIGINT NOT NULL,
    author_name VARCHAR(128) NOT NULL,
    contact_email VARCHAR(128) NOT NULL,
    contact_phone VARCHAR(32),
    intro TEXT,
    avatar_url VARCHAR(512),
    level VARCHAR(16) NOT NULL DEFAULT ''NORMAL'',
    total_agents INT NOT NULL DEFAULT 0,
    total_subscribers INT NOT NULL DEFAULT 0,
    total_revenue DECIMAL(15,4) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT ''ACTIVE'',
    create_by VARCHAR(64),
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_author_company (author_company_id, deleted),
    KEY idx_level_status (level, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''作者档案''');

-- mp_publication
CALL create_table_if_not_exists('mp_publication', '
CREATE TABLE mp_publication (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_type VARCHAR(16) NOT NULL,
    agent_definition_id BIGINT,
    module_id BIGINT,
    author_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    category_code VARCHAR(32) NOT NULL,
    sub_category_code VARCHAR(32),
    summary VARCHAR(512),
    long_description TEXT,
    icon_url VARCHAR(512),
    screenshots JSON,
    tags VARCHAR(512),
    status VARCHAR(16) NOT NULL DEFAULT ''DRAFT'',
    published_at DATETIME(3),
    audit_message VARCHAR(512),
    view_count INT NOT NULL DEFAULT 0,
    install_count INT NOT NULL DEFAULT 0,
    subscription_count INT NOT NULL DEFAULT 0,
    avg_rating DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    rating_count INT NOT NULL DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_pub_code (code, deleted),
    KEY idx_status_pub (status, published_at DESC),
    KEY idx_author (author_id),
    KEY idx_category (category_code, status, published_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''上架商品''');

-- mp_install
CALL create_table_if_not_exists('mp_install', '
CREATE TABLE mp_install (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    publication_id BIGINT NOT NULL,
    tenant_company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    agent_instance_id BIGINT,
    install_source VARCHAR(32) NOT NULL DEFAULT ''MARKET'',
    status VARCHAR(16) NOT NULL DEFAULT ''ACTIVE'',
    installed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    uninstalled_at DATETIME(3),
    create_by VARCHAR(64),
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_install (publication_id, tenant_company_id, user_id, deleted),
    KEY idx_user_status (user_id, status),
    KEY idx_tenant (tenant_company_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''安装记录''');

-- mp_review
CALL create_table_if_not_exists('mp_review', '
CREATE TABLE mp_review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    publication_id BIGINT NOT NULL,
    tenant_company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating TINYINT NOT NULL,
    content VARCHAR(1024),
    status VARCHAR(16) NOT NULL DEFAULT ''VISIBLE'',
    create_by VARCHAR(64),
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_review (publication_id, user_id, deleted),
    KEY idx_pub_status (publication_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''评价''');

-- mp_category
CALL create_table_if_not_exists('mp_category', '
CREATE TABLE mp_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT,
    product_type VARCHAR(16) NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    icon VARCHAR(64),
    create_by VARCHAR(64),
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_cat_code (product_type, code, deleted),
    KEY idx_parent (parent_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''类目树''');

-- mp_tag
CALL create_table_if_not_exists('mp_tag', '
CREATE TABLE mp_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(32) NOT NULL,
    category VARCHAR(32),
    sort_order INT NOT NULL DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tag_name (name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''标签''');

-- 种子数据 (幂等 INSERT IGNORE)
INSERT IGNORE INTO mp_category (product_type, code, name, sort_order, icon) VALUES
  (''AGENT'', ''office'',  ''办公助手'',  1, ''Briefcase''),
  (''AGENT'', ''finance'', ''财务助手'',  2, ''Money''),
  (''AGENT'', ''sales'',   ''销售助手'',  3, ''TrendCharts''),
  (''MODULE'',''data'',    ''数据集成'',  1, ''DataLine''),
  (''MODULE'',''integration'', ''系统集成'', 2, ''Connection''),
  (''MODULE'',''tool'',    ''效率工具'',  3, ''Tools'');

INSERT IGNORE INTO mp_tag (name, category, sort_order) VALUES
  (''高评分'', ''quality'', 1),
  (''新发布'', ''quality'', 2),
  (''中文'', ''lang'', 1),
  (''多语言'', ''lang'', 2),
  (''企业级'', ''scale'', 1),
  (''轻量'', ''scale'', 2),
  (''免费试用'', ''billing'', 1),
  (''付费订阅'', ''billing'', 2),
  (''代码生成'', ''domain'', 1),
  (''内容创作'', ''domain'', 2);

DROP PROCEDURE IF EXISTS create_table_if_not_exists;
```

- [ ] **Step 7: Verify SQL runs** （未完成：仓库里查不到运行结果；旁证是 springboot3/deploy/mysql-initdb.d/17-opc-marketplace-schema.sql 把同一份 DDL 放进 MySQL 容器首启目录，但没有执行记录）

```bash
docker exec -i aiopc-mysql mysql --default-character-set=utf8mb4 opc < springboot3/sql/migrations/V20261020__opc_marketplace_schema.sql
docker exec aiopc-mysql mysql opc -e "SHOW TABLES LIKE 'mp_%';"
```

Expected: 6 tables listed (`mp_author`, `mp_publication`, `mp_install`, `mp_review`, `mp_category`, `mp_tag`).

- [ ] **Step 8: Verify module compiles** （未完成：仓库里查不到运行结果；旁证是 gitignore 的 target/classes 有 68 个 .class、target/opc-marketplace.jar 存在，但不是仓库文件也没有构建日志）

```bash
cd springboot3/ruoyi-modules
mvn -pl opc-marketplace -am clean compile -DskipTests
```

Expected: BUILD SUCCESS.

- [x] **Step 9: Commit**

```bash
cd D:/work-ai/0401-lumen-opc
git add springboot3/ruoyi-modules/opc-marketplace springboot3/sql/migrations/V20261020__opc_marketplace_schema.sql
git commit -m "feat(marketplace): 模块骨架 + 6 表 SQL (P1)"
```

---

### Task 2: 6 实体类 + 6 Mapper + 3 枚举

**Files:**
- Create: `springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/domain/{MpAuthor,MpPublication,MpInstall,MpReview,MpCategory,MpTag}.java`
- Create: `springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/mapper/{MpAuthorMapper,MpPublicationMapper,MpInstallMapper,MpReviewMapper,MpCategoryMapper,MpTagMapper}.java`
- Create: `springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/enums/{ProductType,PublicationStatus,AuthorLevel}.java`
- Create: `springboot3/ruoyi-modules/opc-marketplace/src/main/resources/mapper/marketplace/{MpAuthorMapper,MpPublicationMapper,MpInstallMapper,MpReviewMapper,MpCategoryMapper,MpTagMapper}.xml`

- [x] **Step 1: Create enums**

`ProductType.java`:
```java
package com.ruoyi.opc.marketplace.enums;

public enum ProductType {
    AGENT, MODULE
}
```

`PublicationStatus.java`:
```java
package com.ruoyi.opc.marketplace.enums;

public enum PublicationStatus {
    DRAFT, PENDING, PUBLISHED, SUSPENDED, DEPRECATED
}
```

`AuthorLevel.java`:
```java
package com.ruoyi.opc.marketplace.enums;

public enum AuthorLevel {
    NORMAL, PREMIUM
}
```

- [x] **Step 2: Create 6 domain entities**

`MpAuthor.java`:
```java
package com.ruoyi.opc.marketplace.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MpAuthor implements Serializable {
    private Long id;
    private Long authorCompanyId;
    private String authorName;
    private String contactEmail;
    private String contactPhone;
    private String intro;
    private String avatarUrl;
    private String level;
    private Integer totalAgents;
    private Integer totalSubscribers;
    private BigDecimal totalRevenue;
    private String status;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
```

`MpPublication.java`:
```java
package com.ruoyi.opc.marketplace.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MpPublication implements Serializable {
    private Long id;
    private String productType;
    private Long agentDefinitionId;
    private Long moduleId;
    private Long authorId;
    private String code;
    private String name;
    private String categoryCode;
    private String subCategoryCode;
    private String summary;
    private String longDescription;
    private String iconUrl;
    private String screenshots;        // JSON string
    private String tags;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishedAt;
    private String auditMessage;
    private Integer viewCount;
    private Integer installCount;
    private Integer subscriptionCount;
    private BigDecimal avgRating;
    private Integer ratingCount;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
```

`MpInstall.java`:
```java
package com.ruoyi.opc.marketplace.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class MpInstall implements Serializable {
    private Long id;
    private Long publicationId;
    private Long tenantCompanyId;
    private Long userId;
    private Long agentInstanceId;
    private String installSource;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime installedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime uninstalledAt;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
```

`MpReview.java`:
```java
package com.ruoyi.opc.marketplace.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class MpReview implements Serializable {
    private Long id;
    private Long publicationId;
    private Long tenantCompanyId;
    private Long userId;
    private Integer rating;
    private String content;
    private String status;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
```

`MpCategory.java`:
```java
package com.ruoyi.opc.marketplace.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class MpCategory implements Serializable {
    private Long id;
    private Long parentId;
    private String productType;
    private String code;
    private String name;
    private Integer sortOrder;
    private String icon;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
```

`MpTag.java`:
```java
package com.ruoyi.opc.marketplace.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class MpTag implements Serializable {
    private Long id;
    private String name;
    private String category;
    private Integer sortOrder;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
```

- [x] **Step 3: Create 6 mapper interfaces**

`MpAuthorMapper.java`:
```java
package com.ruoyi.opc.marketplace.mapper;

import com.ruoyi.opc.marketplace.domain.MpAuthor;
import java.util.Collection;
import java.util.List;

public interface MpAuthorMapper {
    int insert(MpAuthor a);
    MpAuthor selectById(Long id);
    MpAuthor selectByCompanyId(Long authorCompanyId);
    int updateById(MpAuthor a);
    int incrementTotalAgents(Long id);
    /**
     * 批量按 id 查询作者,用于列表页一次性填充 authorName/authorAvatar,避免 N+1.
     * 调用方契约: 传入空集合时不要调用本方法 —— MyBatis 会发出非法 SQL IN (),需在 service 层短路.
     */
    List<MpAuthor> selectByIds(Collection<Long> ids);
}
```

`MpPublicationMapper.java`:
```java
package com.ruoyi.opc.marketplace.mapper;

import com.ruoyi.opc.marketplace.domain.MpPublication;
import java.util.List;
import java.util.Map;

public interface MpPublicationMapper {
    int insert(MpPublication p);
    MpPublication selectById(Long id);
    MpPublication selectByCode(String code);
    List<MpPublication> selectList(Map<String, Object> params);
    int countList(Map<String, Object> params);
    List<MpPublication> selectByAuthorId(Long authorId);
    int updateById(MpPublication p);
    int incrementViewCount(Long id);
    int incrementInstallCount(Long id);
    int incrementSubscriptionCount(Long id);
    int updateRating(Long id, java.math.BigDecimal avg, Integer count);
    int updateStatus(Long id, String status);
}
```

`MpInstallMapper.java`:
```java
package com.ruoyi.opc.marketplace.mapper;

import com.ruoyi.opc.marketplace.domain.MpInstall;
import java.util.List;
import java.util.Map;

public interface MpInstallMapper {
    int insert(MpInstall i);
    MpInstall selectById(Long id);
    MpInstall selectUnique(Long publicationId, Long tenantCompanyId, Long userId);
    List<MpInstall> selectByUser(Long userId);
    List<MpInstall> selectByTenant(Long tenantCompanyId);
    int updateStatus(Long id, String status, java.time.LocalDateTime uninstalledAt);
    /** 重新安装：复活既有软删除行（uk_install 不含 deleted，重复 INSERT 会撞唯一键） */
    int reactivate(@org.apache.ibatis.annotations.Param("id") Long id,
                   @org.apache.ibatis.annotations.Param("agentInstanceId") Long agentInstanceId,
                   @org.apache.ibatis.annotations.Param("installedAt") java.time.LocalDateTime installedAt,
                   @org.apache.ibatis.annotations.Param("updateBy") String updateBy);
}
```

> ⚠️ **唯一键与软删除的交互**（Task 2 实现期发现）：
> `uk_install(publication_id, tenant_company_id, user_id)` **不含** `deleted`（Task 1 审查 I1 修复，
> 与 crm/hr/erp/community 同惯例——硬删除语义）。因此软删除行仍占用唯一键。
> `selectUnique` / `selectByPublicationAndUser` **不能**加 `deleted = 0` 过滤，否则：
> - 「卸载后再安装」查不到既有行 → INSERT 撞 1062
> - 「已评价(被软删)」查不到 → 重复评价通过
>
> 对应地，`install()` 必须走**复活**分支而非 INSERT，`uninstall()` 必须校验 `ACTIVE`。
> 复活时需能**清空** `uninstalled_at`——`updateStatus` 的 `<set>` 在参数为 null 时跳过该列，
> 所以单独提供 `reactivate`（用 `<set>` + `<if>` 之外显式赋值 `uninstalled_at = NULL`）。
>
> **评价侧（Task 7）的对应结论**：`uk_review(publication_id, user_id)` 同样不含 `deleted`，
> `selectByPublicationAndUser` 不过滤 `deleted`，因此**被软删（HIDDEN）的评价仍会挡住再次评价**。
> 这是**有意的**——防止「删评价刷分」，`ReviewServiceImpl.create()` 的 `existing != null → 409` 保持不变，
> 且 HIDDEN 评价不参与 `averageForPublication`（该查询按 `status = 'VISIBLE'` 过滤）。

`MpReviewMapper.java`:
```java
package com.ruoyi.opc.marketplace.mapper;

import com.ruoyi.opc.marketplace.domain.MpReview;
import java.util.List;
import java.util.Map;

public interface MpReviewMapper {
    int insert(MpReview r);
    MpReview selectById(Long id);
    MpReview selectByPublicationAndUser(Long publicationId, Long userId);
    List<MpReview> selectList(Map<String, Object> params);
    int countList(Map<String, Object> params);
    BigDecimal averageForPublication(Long publicationId);  // avg rating
    int countForPublication(Long publicationId);
    int updateById(MpReview r);
    int updateStatus(Long id, String status);
}
```

(注意 `BigDecimal` import: `import java.math.BigDecimal;`)

`MpCategoryMapper.java`:
```java
package com.ruoyi.opc.marketplace.mapper;

import com.ruoyi.opc.marketplace.domain.MpCategory;
import java.util.List;

public interface MpCategoryMapper {
    List<MpCategory> selectAll();
    List<MpCategory> selectByProductType(String productType);
    MpCategory selectByCode(String productType, String code);
    MpCategory selectById(Long id);
}
```

`MpTagMapper.java`:
```java
package com.ruoyi.opc.marketplace.mapper;

import com.ruoyi.opc.marketplace.domain.MpTag;
import java.util.List;

public interface MpTagMapper {
    List<MpTag> selectAll();
    List<MpTag> selectByCategory(String category);
}
```

- [x] **Step 4: Create 6 mapper XML files (skeleton with required statements)**

Each XML has the same structure. `MpPublicationMapper.xml` (most complex):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.ruoyi.opc.marketplace.mapper.MpPublicationMapper">
    <resultMap id="BaseResultMap" type="com.ruoyi.opc.marketplace.domain.MpPublication">
        <id column="id" property="id"/>
        <result column="product_type" property="productType"/>
        <result column="agent_definition_id" property="agentDefinitionId"/>
        <result column="module_id" property="moduleId"/>
        <result column="author_id" property="authorId"/>
        <result column="code" property="code"/>
        <result column="name" property="name"/>
        <result column="category_code" property="categoryCode"/>
        <result column="sub_category_code" property="subCategoryCode"/>
        <result column="summary" property="summary"/>
        <result column="long_description" property="longDescription"/>
        <result column="icon_url" property="iconUrl"/>
        <result column="screenshots" property="screenshots"/>
        <result column="tags" property="tags"/>
        <result column="status" property="status"/>
        <result column="published_at" property="publishedAt"/>
        <result column="audit_message" property="auditMessage"/>
        <result column="view_count" property="viewCount"/>
        <result column="install_count" property="installCount"/>
        <result column="subscription_count" property="subscriptionCount"/>
        <result column="avg_rating" property="avgRating"/>
        <result column="rating_count" property="ratingCount"/>
        <result column="create_by" property="createBy"/>
        <result column="create_time" property="createTime"/>
        <result column="update_by" property="updateBy"/>
        <result column="update_time" property="updateTime"/>
    </resultMap>

    <sql id="Base_Columns">id, product_type, agent_definition_id, module_id, author_id, code, name,
        category_code, sub_category_code, summary, long_description, icon_url, screenshots, tags, status,
        published_at, audit_message, view_count, install_count, subscription_count, avg_rating, rating_count,
        create_by, create_time, update_by, update_time</sql>

    <insert id="insert" parameterType="MpPublication" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO mp_publication (product_type, agent_definition_id, module_id, author_id, code, name,
            category_code, sub_category_code, summary, long_description, icon_url, screenshots, tags, status,
            create_by, update_by)
        VALUES (#{productType}, #{agentDefinitionId}, #{moduleId}, #{authorId}, #{code}, #{name},
            #{categoryCode}, #{subCategoryCode}, #{summary}, #{longDescription}, #{iconUrl}, #{screenshots}, #{tags}, #{status},
            #{createBy}, #{updateBy})
    </insert>

    <select id="selectById" resultMap="BaseResultMap">
        SELECT <include refid="Base_Columns"/> FROM mp_publication
        WHERE id = #{id} AND deleted = 0
    </select>

    <select id="selectByCode" resultMap="BaseResultMap">
        SELECT <include refid="Base_Columns"/> FROM mp_publication
        WHERE code = #{code} AND deleted = 0 LIMIT 1
    </select>

    <select id="selectList" resultMap="BaseResultMap">
        SELECT <include refid="Base_Columns"/> FROM mp_publication
        <where>
            deleted = 0
            <if test="category != null and category != ''">AND category_code = #{category}</if>
            <if test="productType != null and productType != ''">AND product_type = #{productType}</if>
            <if test="status != null and status != ''">AND status = #{status}</if>
            <if test="keyword != null and keyword != ''">AND (name LIKE CONCAT('%', #{keyword}, '%') OR summary LIKE CONCAT('%', #{keyword}, '%'))</if>
            <if test="authorId != null">AND author_id = #{authorId}</if>
        </where>
        ORDER BY
            <choose>
                <when test="sortBy == 'rating'">avg_rating DESC, rating_count DESC</when>
                <when test="sortBy == 'installs'">install_count DESC</when>
                <when test="sortBy == 'newest'">published_at DESC</when>
                <otherwise>published_at DESC</otherwise>
            </choose>
        LIMIT #{offset}, #{limit}
    </select>

    <select id="countList" resultType="int">
        SELECT COUNT(*) FROM mp_publication
        <where>
            deleted = 0
            <if test="category != null and category != ''">AND category_code = #{category}</if>
            <if test="productType != null and productType != ''">AND product_type = #{productType}</if>
            <if test="status != null and status != ''">AND status = #{status}</if>
            <if test="keyword != null and keyword != ''">AND (name LIKE CONCAT('%', #{keyword}, '%') OR summary LIKE CONCAT('%', #{keyword}, '%'))</if>
            <if test="authorId != null">AND author_id = #{authorId}</if>
        </where>
    </select>

    <select id="selectByAuthorId" resultMap="BaseResultMap">
        SELECT <include refid="Base_Columns"/> FROM mp_publication
        WHERE author_id = #{authorId} AND deleted = 0 ORDER BY create_time DESC
    </select>

    <update id="updateById" parameterType="MpPublication">
        UPDATE mp_publication
        <set>
            <if test="name != null">name = #{name},</if>
            <if test="categoryCode != null">category_code = #{categoryCode},</if>
            <if test="subCategoryCode != null">sub_category_code = #{subCategoryCode},</if>
            <if test="summary != null">summary = #{summary},</if>
            <if test="longDescription != null">long_description = #{longDescription},</if>
            <if test="iconUrl != null">icon_url = #{iconUrl},</if>
            <if test="screenshots != null">screenshots = #{screenshots},</if>
            <if test="tags != null">tags = #{tags},</if>
            <if test="status != null">status = #{status},</if>
            <if test="publishedAt != null">published_at = #{publishedAt},</if>
            <if test="auditMessage != null">audit_message = #{auditMessage},</if>
            <if test="updateBy != null">update_by = #{updateBy},</if>
        </set>
        WHERE id = #{id} AND deleted = 0
    </update>

    <update id="incrementViewCount">
        UPDATE mp_publication SET view_count = view_count + 1 WHERE id = #{id}
    </update>
    <update id="incrementInstallCount">
        UPDATE mp_publication SET install_count = install_count + 1 WHERE id = #{id}
    </update>
    <update id="incrementSubscriptionCount">
        UPDATE mp_publication SET subscription_count = subscription_count + 1 WHERE id = #{id}
    </update>
    <update id="updateRating">
        UPDATE mp_publication SET avg_rating = #{arg1}, rating_count = #{arg2} WHERE id = #{arg0}
    </update>
    <update id="updateStatus">
        UPDATE mp_publication SET status = #{arg1} WHERE id = #{arg0}
    </update>
</mapper>
```

For other 5 XMLs, follow the same pattern. Truncated patterns:
- `MpAuthorMapper.xml`: insert, selectById, selectByCompanyId, updateById, incrementTotalAgents, selectByIds
  - `selectByIds` 用 `<foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>` 拼 IN 子句
  - **调用方契约**: 不要传入空集合 —— MyBatis 不能生成合法 IN (),service 层必须先短路 (`if (!authorIds.isEmpty())`)
- `MpInstallMapper.xml`: insert, selectById, selectUnique, selectByUser, selectByTenant, updateStatus, reactivate
  - `reactivate` 必须显式写 `uninstalled_at = NULL`（`updateStatus` 的 `<set>` 无法清空该列）
  - `selectUnique` / `selectByPublicationAndUser` **不加** `deleted = 0`（见上文唯一键交互说明）
- `MpReviewMapper.xml`: insert, selectById, selectByPublicationAndUser, selectList, countList, averageForPublication, countForPublication, updateById, updateStatus
- `MpCategoryMapper.xml`: selectAll, selectByProductType, selectByCode, selectById
- `MpTagMapper.xml`: selectAll, selectByCategory

(Generate these 5 XMLs by mirroring the structure above. Each is 50-100 lines.)

- [ ] **Step 5: Verify compile** （未完成：仓库里查不到运行结果）

```bash
cd springboot3/ruoyi-modules
mvn -pl opc-marketplace -am clean compile -DskipTests
```

Expected: BUILD SUCCESS.

- [x] **Step 6: Commit**

```bash
git add springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/{domain,mapper,enums}
git add springboot3/ruoyi-modules/opc-marketplace/src/main/resources/mapper/marketplace
git commit -m "feat(marketplace): 6 实体 + 6 mapper + 3 枚举 (P1)"
```

---

### Task 3: DTOs（10 个）

**Files:**
- Create: `springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/dto/{PublicationCreateRequest,PublicationEditRequest,AuthorApplyRequest,ReviewCreateRequest,ReviewUpdateRequest,PublicationListQuery,AuthorListQuery,PublicationVO,AuthorVO,ReviewVO}.java`

- [x] **Step 1: Create request DTOs**

`PublicationCreateRequest.java`:
```java
package com.ruoyi.opc.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class PublicationCreateRequest {
    @NotBlank @Size(max = 16)
    private String productType;            // AGENT or MODULE

    private Long agentDefinitionId;        // required when productType=AGENT
    private Long moduleId;                 // required when productType=MODULE

    @NotBlank @Size(max = 64)
    private String code;                   // unique

    @NotBlank @Size(max = 128)
    private String name;

    @NotBlank @Size(max = 32)
    private String categoryCode;

    @Size(max = 32)
    private String subCategoryCode;

    @Size(max = 512)
    private String summary;

    private String longDescription;

    @Size(max = 512)
    private String iconUrl;

    private List<String> screenshots;      // will be JSON serialized

    @Size(max = 512)
    private String tags;                   // comma-separated
}
```

`PublicationEditRequest.java`:
```java
package com.ruoyi.opc.marketplace.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class PublicationEditRequest {
    @Size(max = 128) private String name;
    @Size(max = 32) private String categoryCode;
    @Size(max = 32) private String subCategoryCode;
    @Size(max = 512) private String summary;
    private String longDescription;
    @Size(max = 512) private String iconUrl;
    private List<String> screenshots;
    @Size(max = 512) private String tags;
}
```

`AuthorApplyRequest.java`:
```java
package com.ruoyi.opc.marketplace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthorApplyRequest {
    // authorCompanyId 已移除:一律取 SecurityUtils.getCompanyId()。
    // 原设计从请求体接收,任何登录用户可代他人申请,而 uk_author_company 是硬唯一键,
    // 受害者将永远拿不到自己的作者资格。Task4 实施期发现并修正。

    @NotBlank @Size(max = 128)
    private String authorName;

    @NotBlank @Email @Size(max = 128)
    private String contactEmail;

    @Size(max = 32)
    private String contactPhone;

    @Size(max = 1024)
    private String intro;

    @Size(max = 512)
    private String avatarUrl;
}
```

`ReviewCreateRequest.java`:
```java
package com.ruoyi.opc.marketplace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewCreateRequest {
    @NotNull
    private Long publicationId;

    @NotNull
    @Min(1) @Max(5)
    private Integer rating;

    @Size(max = 1024)
    private String content;
}
```

`ReviewUpdateRequest.java`:
```java
package com.ruoyi.opc.marketplace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewUpdateRequest {
    @NotNull @Min(1) @Max(5)
    private Integer rating;

    @Size(max = 1024)
    private String content;
}
```

`PublicationListQuery.java`:
```java
package com.ruoyi.opc.marketplace.dto;

import lombok.Data;

@Data
public class PublicationListQuery {
    private String category;
    private String productType;
    private String keyword;
    private String status;          // PUBLISHED by default for public
    private String sortBy;          // newest/rating/installs
    private Integer page = 1;
    private Integer pageSize = 12;
}
```

`AuthorListQuery.java`:
```java
package com.ruoyi.opc.marketplace.dto;

import lombok.Data;

@Data
public class AuthorListQuery {
    private String keyword;
    private String level;
    private Integer page = 1;
    private Integer pageSize = 12;
}
```

- [x] **Step 2: Create response VOs**

`PublicationVO.java`:
```java
package com.ruoyi.opc.marketplace.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PublicationVO implements Serializable {
    private Long id;
    private String productType;
    private Long agentDefinitionId;
    private Long moduleId;
    private Long authorId;
    private String authorName;
    private String authorAvatar;
    private String code;
    private String name;
    private String categoryCode;
    private String categoryName;
    private String subCategoryCode;
    private String summary;
    private String longDescription;
    private String iconUrl;
    private String screenshots;
    private String tags;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishedAt;
    private Integer viewCount;
    private Integer installCount;
    private Integer subscriptionCount;
    private BigDecimal avgRating;
    private Integer ratingCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
```

`AuthorVO.java`:
```java
package com.ruoyi.opc.marketplace.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
public class AuthorVO implements Serializable {
    private Long id;
    private Long authorCompanyId;
    private String authorName;
    private String contactEmail;
    private String intro;
    private String avatarUrl;
    private String level;
    private Integer totalAgents;
    private Integer totalSubscribers;
    private BigDecimal totalRevenue;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
```

`ReviewVO.java`:
```java
package com.ruoyi.opc.marketplace.dto;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
public class ReviewVO implements Serializable {
    private Long id;
    private Long publicationId;
    private Long userId;
    private String userName;
    private String userAvatar;
    private Integer rating;
    private String content;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
```

- [ ] **Step 3: Verify compile** （未完成：仓库里查不到运行结果）

```bash
cd springboot3/ruoyi-modules
mvn -pl opc-marketplace -am clean compile -DskipTests
```

Expected: BUILD SUCCESS.

- [x] **Step 4: Commit**

```bash
git add springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/dto
git commit -m "feat(marketplace): 10 DTOs (P1)"
```

---

### Task 4: AuthorService + 测试 + Controller

**Files:**
- Create: `springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/service/AuthorService.java`
- Create: `springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/service/impl/AuthorServiceImpl.java`
- Create: `springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/controller/AuthorController.java`
- Test: `springboot3/ruoyi-modules/opc-marketplace/src/test/java/com/ruoyi/opc/marketplace/controller/AuthorControllerTest.java`

- [x] **Step 1: Write AuthorService interface** （部分：apply() 实际签名是 3 参 apply(req, companyId, operatorId)；plan 代码块写的 2 参 apply(req, operatorId) 已过期——authorCompanyId 改从登录态取，见 Task 3 的 DTO 注释）

```java
package com.ruoyi.opc.marketplace.service;

import com.ruoyi.opc.marketplace.domain.MpAuthor;
import com.ruoyi.opc.marketplace.dto.AuthorApplyRequest;
import com.ruoyi.opc.marketplace.dto.AuthorListQuery;
import com.ruoyi.opc.marketplace.dto.AuthorVO;
import java.util.List;
import java.util.Map;

public interface AuthorService {
    MpAuthor apply(AuthorApplyRequest req, Long operatorId);
    MpAuthor getByCompanyId(Long companyId);
    MpAuthor getById(Long id);
    /**
     * 完整 VO —— 含 contactEmail / totalRevenue。
     * 只允许本人(/me、/apply)或内部流程调用。任何公开端点必须用 toPublicVO。
     */
    AuthorVO toVO(MpAuthor a);
    /**
     * 公开脱敏 VO —— 清空 contactEmail / contactPhone / totalRevenue。
     *
     * 脱敏放在 service 层而不是 controller 层:controller 里那行
     * vo.setContactEmail(null) 一次重构就会被顺手删掉,而
     * /opc/marketplace/author/{id:[0-9]+} 在 Task 10 会进网关白名单(公开),
     * 删掉即等于全网泄露企业邮箱与营收。
     */
    AuthorVO toPublicVO(MpAuthor a);
    Map<String, Object> list(AuthorListQuery query);
    Map<String, Object> stats(Long id);
}
```

- [x] **Step 2: Write AuthorServiceImpl**

```java
package com.ruoyi.opc.marketplace.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpAuthor;
import com.ruoyi.opc.marketplace.dto.AuthorApplyRequest;
import com.ruoyi.opc.marketplace.dto.AuthorListQuery;
import com.ruoyi.opc.marketplace.dto.AuthorVO;
import com.ruoyi.opc.marketplace.mapper.MpAuthorMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.service.AuthorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorServiceImpl implements AuthorService {

    private final MpAuthorMapper authorMapper;
    private final MpPublicationMapper publicationMapper;

    @Override
    public MpAuthor apply(AuthorApplyRequest req, Long operatorId) {
        MpAuthor existing = authorMapper.selectByCompanyId(req.getAuthorCompanyId());
        if (existing != null) {
            throw new OpcException(409, "该企业已是作者，不可重复申请", "MARKETPLACE");
        }
        MpAuthor a = new MpAuthor();
        a.setAuthorCompanyId(req.getAuthorCompanyId());
        a.setAuthorName(req.getAuthorName());
        a.setContactEmail(req.getContactEmail());
        a.setContactPhone(req.getContactPhone());
        a.setIntro(req.getIntro());
        a.setAvatarUrl(req.getAvatarUrl());
        a.setLevel("NORMAL");
        a.setTotalAgents(0);
        a.setTotalSubscribers(0);
        a.setTotalRevenue(BigDecimal.ZERO);
        a.setStatus("ACTIVE");
        a.setCreateBy(String.valueOf(operatorId));
        a.setUpdateBy(String.valueOf(operatorId));
        authorMapper.insert(a);
        log.info("[Author] apply success companyId={} authorId={}", req.getAuthorCompanyId(), a.getId());
        return a;
    }

    @Override
    public MpAuthor getByCompanyId(Long companyId) {
        return authorMapper.selectByCompanyId(companyId);
    }

    @Override
    public MpAuthor getById(Long id) {
        MpAuthor a = authorMapper.selectById(id);
        if (a == null) throw new OpcException(404, "作者不存在: " + id, "MARKETPLACE");
        return a;
    }

    @Override
    public AuthorVO toVO(MpAuthor a) {
        AuthorVO vo = new AuthorVO();
        vo.setId(a.getId());
        vo.setAuthorCompanyId(a.getAuthorCompanyId());
        vo.setAuthorName(a.getAuthorName());
        vo.setContactEmail(a.getContactEmail());
        vo.setIntro(a.getIntro());
        vo.setAvatarUrl(a.getAvatarUrl());
        vo.setLevel(a.getLevel());
        vo.setTotalAgents(a.getTotalAgents());
        vo.setTotalSubscribers(a.getTotalSubscribers());
        vo.setTotalRevenue(a.getTotalRevenue());
        vo.setStatus(a.getStatus());
        vo.setCreateTime(a.getCreateTime());
        return vo;
    }

    @Override
    public AuthorVO toPublicVO(MpAuthor a) {
        AuthorVO vo = toVO(a);
        if (vo == null) return null;
        // ⚠️ AuthorVO 当前没有 contactPhone 字段(domain 有),该字段不会进响应;
        //    若日后给 VO 补 contactPhone,必须在此补一行 vo.setContactPhone(null)。
        vo.setContactEmail(null);
        vo.setTotalRevenue(null);
        return vo;
    }

    @Override
    public Map<String, Object> list(AuthorListQuery query) {
        // P1: simple full list (no paging yet); filter in-memory by keyword/level
        List<MpAuthor> all = List.of();  // not implemented in mapper yet; return empty
        Map<String, Object> data = new HashMap<>();
        data.put("rows", all);
        data.put("total", 0);
        data.put("page", query.getPage());
        data.put("pageSize", query.getPageSize());
        return data;
    }

    @Override
    public Map<String, Object> stats(Long id) {
        MpAuthor a = getById(id);
        Map<String, Object> data = new HashMap<>();
        // 嵌套的 author 同样脱敏 —— stats 的 id 由调用方任意指定,用 toVO 会让
        // 任意登录用户读到别家企业的邮箱与营收
        data.put("author", toPublicVO(a));
        data.put("publicationCount", publicationMapper.countList(Map.of("authorId", id)));
        return data;
    }
}
```

- [x] **Step 3: Write AuthorController**

```java
package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.marketplace.dto.AuthorApplyRequest;
import com.ruoyi.opc.marketplace.dto.AuthorListQuery;
import com.ruoyi.opc.marketplace.dto.AuthorVO;
import com.ruoyi.opc.marketplace.service.AuthorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/opc/marketplace/author")
@RequiredArgsConstructor
public class AuthorController {

    private final AuthorService authorService;

    @PostMapping("/apply")
    public R<AuthorVO> apply(@Valid @RequestBody AuthorApplyRequest req) {
        Long userId = SecurityUtils.getUserId();
        var author = authorService.apply(req, userId);
        return R.ok(authorService.toVO(author));
    }

    @GetMapping("/me")
    public R<AuthorVO> me() {
        Long companyId = SecurityUtils.getCompanyId();
        // fail-closed: 未关联企业的账号不得进入租户维度操作,禁止用 0 兜底(见 Task6 前置说明)
        if (companyId == null) throw new OpcException(400, "当前账号未关联企业,请先加入或创建企业", "MARKETPLACE");
        var author = authorService.getByCompanyId(companyId);
        if (author == null) return R.ok(null);
        return R.ok(authorService.toVO(author));
    }

    /**
     * 公开资料 —— 走 toPublicVO 脱敏。
     * Task 10 会把 /opc/marketplace/author/{id:[0-9]+} 放进网关白名单(允许匿名),
     * 因此本端点绝不能返回 contactEmail / totalRevenue。
     * "me" 不匹配 [0-9]+,所以白名单不会把 /me 一起放过。
     */
    @GetMapping("/{id}")
    public R<AuthorVO> get(@PathVariable Long id) {
        return R.ok(authorService.toPublicVO(authorService.getById(id)));
    }

    @GetMapping("/list")
    public R<Map<String, Object>> list(AuthorListQuery query) {
        return R.ok(authorService.list(query));
    }

    @GetMapping("/{id}/publications")
    public R<Object> publications(@PathVariable Long id) {
        // delegated — Phase 2 will add this; P1 returns empty
        return R.ok(Map.of("rows", List.of(), "total", 0));
    }

    @GetMapping("/{id}/stats")
    public R<Map<String, Object>> stats(@PathVariable Long id) {
        return R.ok(authorService.stats(id));
    }
}
```

- [x] **Step 4: Write failing test**

```java
package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.marketplace.domain.MpAuthor;
import com.ruoyi.opc.marketplace.dto.AuthorApplyRequest;
import com.ruoyi.opc.marketplace.service.AuthorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthorController.class)
class AuthorControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AuthorService authorService;
    MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getUserId).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    void apply_returnsAuthor() throws Exception {
        MpAuthor a = MpAuthor.builder()
                .id(10L).authorCompanyId(100L).authorName("TestAuthor")
                .level("NORMAL").totalAgents(0).totalSubscribers(0)
                .totalRevenue(BigDecimal.ZERO).status("ACTIVE").build();
        when(authorService.apply(any(), anyLong(), anyLong())).thenReturn(a);
        when(authorService.toVO(any())).thenReturn(new AuthorVO());

        String body = """
            {
              "authorName": "TestAuthor",
              "contactEmail": "a@b.com"
            }
            """;

        mvc.perform(post("/opc/marketplace/author/apply")
                .contentType("application/json").content(body))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void get_returnsAuthor() throws Exception {
        when(authorService.getById(10L)).thenReturn(MpAuthor.builder().id(10L).build());
        when(authorService.toVO(any())).thenReturn(new AuthorVO());

        mvc.perform(get("/opc/marketplace/author/10"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void apply_409_whenAlreadyAuthor() throws Exception {
        when(authorService.apply(any(), anyLong(), anyLong()))
            .thenThrow(new com.ruoyi.opc.common.exception.OpcException(409, "已申请", "MARKETPLACE"));

        String body = """
            {"authorName": "X", "contactEmail": "a@b.com"}
            """;

        mvc.perform(post("/opc/marketplace/author/apply")
                .contentType("application/json").content(body))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(409));
    }
}
```

- [ ] **Step 5: Run test to verify it passes** （未完成：仓库里查不到运行结果；target/ 下没有 surefire-reports，也没有 CI 记录）

```bash
cd springboot3/ruoyi-modules
mvn -pl opc-marketplace test -Dtest=AuthorControllerTest
```

Expected: 3 tests pass.

- [x] **Step 6: Commit**

```bash
git add springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/{service/AuthorService.java,service/impl/AuthorServiceImpl.java,controller/AuthorController.java}
git add springboot3/ruoyi-modules/opc-marketplace/src/test/java/com/ruoyi/opc/marketplace/controller/AuthorControllerTest.java
git commit -m "feat(marketplace): AuthorService + Controller + 3 tests (P1)"
```

---

### Task 5: PublicationService + 测试 + Controller

**Files:**
- Create: `springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/service/PublicationService.java`
- Create: `.../service/impl/PublicationServiceImpl.java`
- Create: `.../controller/PublicationController.java`
- Test: `.../test/.../PublicationControllerTest.java` (8 tests)
- Test: `.../test/.../PublicationServiceTest.java` (8 tests)

- [x] **Step 1: Write PublicationService interface**

```java
package com.ruoyi.opc.marketplace.service;

import com.ruoyi.opc.marketplace.domain.MpPublication;
import com.ruoyi.opc.marketplace.dto.PublicationCreateRequest;
import com.ruoyi.opc.marketplace.dto.PublicationEditRequest;
import com.ruoyi.opc.marketplace.dto.PublicationListQuery;
import com.ruoyi.opc.marketplace.dto.PublicationVO;
import java.util.List;
import java.util.Map;

public interface PublicationService {
    MpPublication create(PublicationCreateRequest req, Long authorId, Long operatorId);
    MpPublication edit(Long id, PublicationEditRequest req, Long authorId);
    MpPublication submitAudit(Long id, Long authorId);
    MpPublication online(Long id, Long authorId);   // P1: auto-publish (no audit yet)
    MpPublication offline(Long id, Long authorId);
    /**
     * 作者侧任意状态读取:不过滤 status,用于编辑/上下架/我的列表.
     * 调用方需自行校验 authorId 与 author 归属 (见 edit/submitAudit/online/offline).
     */
    MpPublication getById(Long id);
    /**
     * 匿名公开详情用:非 PUBLISHED 一律按 404 处理,不泄露草稿存在性。
     * 与 {@code getById} 语义不同 —— 网关白名单 {@code /opc/marketplace/publication/{id:[0-9]+}}
     * 会绕过鉴权,必须走本方法.
     */
    MpPublication getPublishedById(Long id);
    PublicationVO toVO(MpPublication p);
    Map<String, Object> list(PublicationListQuery query);
    /**
     * 作者自己的全部状态行(供 /my/list 等内部用) —— 不过滤 status.
     * 返回原始行,不做 VO 装配;VO 装配请走 {@link #listByAuthorVOs(Long)}.
     */
    List<MpPublication> listByAuthor(Long authorId);
    /**
     * 作者自己全部状态的 VO 列表,一次性批量填充 authorName + categoryName (避免 N+1).
     * 走 {@link MpAuthorMapper#selectByIds} + {@link MpCategoryMapper#selectByCodes} 两次查询,
     * 替代原本每行 2 次单查.
     */
    List<PublicationVO> listByAuthorVOs(Long authorId);
}
```

- [x] **Step 2: Write PublicationServiceImpl**

```java
package com.ruoyi.opc.marketplace.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpAuthor;
import com.ruoyi.opc.marketplace.domain.MpCategory;
import com.ruoyi.opc.marketplace.domain.MpPublication;
import com.ruoyi.opc.marketplace.dto.PublicationCreateRequest;
import com.ruoyi.opc.marketplace.dto.PublicationEditRequest;
import com.ruoyi.opc.marketplace.dto.PublicationListQuery;
import com.ruoyi.opc.marketplace.dto.PublicationVO;
import com.ruoyi.opc.marketplace.mapper.MpAuthorMapper;
import com.ruoyi.opc.marketplace.mapper.MpCategoryMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.service.PublicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicationServiceImpl implements PublicationService {

    private final MpPublicationMapper publicationMapper;
    private final MpCategoryMapper categoryMapper;
    /** 注入以支持列表页一次性批量填充 authorName/authorAvatar (避免 N+1) */
    private final MpAuthorMapper authorMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public MpPublication create(PublicationCreateRequest req, Long authorId, Long operatorId) {
        // 1. validate
        if ("AGENT".equals(req.getProductType()) && req.getAgentDefinitionId() == null) {
            throw new OpcException(400, "AGENT 类型必须传 agentDefinitionId", "MARKETPLACE");
        }
        if ("MODULE".equals(req.getProductType()) && req.getModuleId() == null) {
            throw new OpcException(400, "MODULE 类型必须传 moduleId", "MARKETPLACE");
        }
        MpCategory cat = categoryMapper.selectByCode(req.getProductType(), req.getCategoryCode());
        if (cat == null) {
            throw new OpcException(400, "类目不存在: " + req.getCategoryCode(), "MARKETPLACE");
        }
        MpPublication existing = publicationMapper.selectByCode(req.getCode());
        if (existing != null) {
            throw new OpcException(409, "商品 code 已存在: " + req.getCode(), "MARKETPLACE");
        }
        // 2. persist
        MpPublication p = new MpPublication();
        p.setProductType(req.getProductType());
        p.setAgentDefinitionId(req.getAgentDefinitionId());
        p.setModuleId(req.getModuleId());
        p.setAuthorId(authorId);
        p.setCode(req.getCode());
        p.setName(req.getName());
        p.setCategoryCode(req.getCategoryCode());
        p.setSubCategoryCode(req.getSubCategoryCode());
        p.setSummary(req.getSummary());
        p.setLongDescription(req.getLongDescription());
        p.setIconUrl(req.getIconUrl());
        p.setTags(req.getTags());
        p.setStatus("DRAFT");
        p.setViewCount(0);
        p.setInstallCount(0);
        p.setSubscriptionCount(0);
        p.setAvgRating(java.math.BigDecimal.ZERO);
        p.setRatingCount(0);
        p.setCreateBy(String.valueOf(operatorId));
        p.setUpdateBy(String.valueOf(operatorId));
        // serialize screenshots JSON
        if (req.getScreenshots() != null) {
            try {
                p.setScreenshots(objectMapper.writeValueAsString(req.getScreenshots()));
            } catch (JsonProcessingException e) {
                throw new OpcException(400, "screenshots JSON 序列化失败", "MARKETPLACE");
            }
        }
        publicationMapper.insert(p);
        log.info("[Publication] created id={} code={} authorId={}", p.getId(), p.getCode(), authorId);
        return p;
    }

    @Override
    public MpPublication edit(Long id, PublicationEditRequest req, Long authorId) {
        MpPublication p = getById(id);
        if (!p.getAuthorId().equals(authorId)) {
            throw new OpcException(403, "只有作者本人可编辑", "MARKETPLACE");
        }
        if (req.getName() != null) p.setName(req.getName());
        if (req.getCategoryCode() != null) p.setCategoryCode(req.getCategoryCode());
        if (req.getSubCategoryCode() != null) p.setSubCategoryCode(req.getSubCategoryCode());
        if (req.getSummary() != null) p.setSummary(req.getSummary());
        if (req.getLongDescription() != null) p.setLongDescription(req.getLongDescription());
        if (req.getIconUrl() != null) p.setIconUrl(req.getIconUrl());
        if (req.getTags() != null) p.setTags(req.getTags());
        if (req.getScreenshots() != null) {
            try {
                p.setScreenshots(objectMapper.writeValueAsString(req.getScreenshots()));
            } catch (JsonProcessingException e) {
                throw new OpcException(400, "screenshots JSON 序列化失败", "MARKETPLACE");
            }
        }
        p.setUpdateBy(String.valueOf(authorId));
        publicationMapper.updateById(p);
        return publicationMapper.selectById(id);
    }

    @Override
    public MpPublication submitAudit(Long id, Long authorId) {
        MpPublication p = getById(id);
        if (!p.getAuthorId().equals(authorId)) {
            throw new OpcException(403, "只有作者本人可提交", "MARKETPLACE");
        }
        if (!"DRAFT".equals(p.getStatus())) {
            throw new OpcException(400, "只有 DRAFT 状态可提交审核", "MARKETPLACE");
        }
        // P1: auto-approve (P3 will add real audit)
        p.setStatus("PUBLISHED");
        p.setPublishedAt(LocalDateTime.now());
        p.setUpdateBy(String.valueOf(authorId));
        publicationMapper.updateById(p);
        return p;
    }

    @Override
    public MpPublication online(Long id, Long authorId) {
        return submitAudit(id, authorId);  // P1 same path
    }

    @Override
    public MpPublication offline(Long id, Long authorId) {
        MpPublication p = getById(id);
        if (!p.getAuthorId().equals(authorId)) {
            throw new OpcException(403, "只有作者本人可下架", "MARKETPLACE");
        }
        p.setStatus("DEPRECATED");
        p.setUpdateBy(String.valueOf(authorId));
        publicationMapper.updateById(p);
        return p;
    }

    @Override
    public MpPublication getById(Long id) {
        MpPublication p = publicationMapper.selectById(id);
        if (p == null) throw new OpcException(404, "商品不存在: " + id, "MARKETPLACE");
        return p;
    }

    @Override
    public MpPublication getPublishedById(Long id) {
        MpPublication p = publicationMapper.selectPublishedById(id);
        if (p == null) throw new OpcException(404, "商品不存在: " + id, "MARKETPLACE");
        return p;
    }

    @Override
    public PublicationVO toVO(MpPublication p) {
        PublicationVO vo = new PublicationVO();
        vo.setId(p.getId());
        vo.setProductType(p.getProductType());
        vo.setAgentDefinitionId(p.getAgentDefinitionId());
        vo.setModuleId(p.getModuleId());
        vo.setAuthorId(p.getAuthorId());
        vo.setCode(p.getCode());
        vo.setName(p.getName());
        vo.setCategoryCode(p.getCategoryCode());
        vo.setSubCategoryCode(p.getSubCategoryCode());
        vo.setSummary(p.getSummary());
        vo.setLongDescription(p.getLongDescription());
        vo.setIconUrl(p.getIconUrl());
        vo.setScreenshots(p.getScreenshots());
        vo.setTags(p.getTags());
        vo.setStatus(p.getStatus());
        vo.setPublishedAt(p.getPublishedAt());
        vo.setViewCount(p.getViewCount());
        vo.setInstallCount(p.getInstallCount());
        vo.setSubscriptionCount(p.getSubscriptionCount());
        vo.setAvgRating(p.getAvgRating());
        vo.setRatingCount(p.getRatingCount());
        vo.setCreateTime(p.getCreateTime());

        // 单条路径: author + category 各 1 次 DB 命中
        if (p.getAuthorId() != null) {
            MpAuthor a = authorMapper.selectById(p.getAuthorId());
            if (a != null) {
                vo.setAuthorName(a.getAuthorName());
                vo.setAuthorAvatar(a.getAvatarUrl());
            }
        }
        if (p.getCategoryCode() != null) {
            MpCategory cat = categoryMapper.selectByCode(p.getProductType(), p.getCategoryCode());
            if (cat != null) vo.setCategoryName(cat.getName());
        }
        return vo;
    }

    /**
     * 列表路径专用 VO 转换 — 作者 + 类目 都批量预加载 (2N → 2).
     * categoryName 通过 {@code Map<String, MpCategory>} 一次批量加载 (按 productType+code 索引),
     * 替代单行 {@code selectByCode} 的 N 次查询.
     */
    private PublicationVO toVO(MpPublication p, Map<Long, MpAuthor> authors,
                                Map<String, MpCategory> categories) {
        PublicationVO vo = new PublicationVO();
        vo.setId(p.getId());
        vo.setProductType(p.getProductType());
        vo.setAgentDefinitionId(p.getAgentDefinitionId());
        vo.setModuleId(p.getModuleId());
        vo.setAuthorId(p.getAuthorId());
        vo.setCode(p.getCode());
        vo.setName(p.getName());
        vo.setCategoryCode(p.getCategoryCode());
        vo.setSubCategoryCode(p.getSubCategoryCode());
        vo.setSummary(p.getSummary());
        vo.setLongDescription(p.getLongDescription());
        vo.setIconUrl(p.getIconUrl());
        vo.setScreenshots(p.getScreenshots());
        vo.setTags(p.getTags());
        vo.setStatus(p.getStatus());
        vo.setPublishedAt(p.getPublishedAt());
        vo.setViewCount(p.getViewCount());
        vo.setInstallCount(p.getInstallCount());
        vo.setSubscriptionCount(p.getSubscriptionCount());
        vo.setAvgRating(p.getAvgRating());
        vo.setRatingCount(p.getRatingCount());
        vo.setCreateTime(p.getCreateTime());

        MpAuthor a = authors.get(p.getAuthorId());
        if (a != null) {
            vo.setAuthorName(a.getAuthorName());
            vo.setAuthorAvatar(a.getAvatarUrl());
        }
        if (p.getProductType() != null && p.getCategoryCode() != null) {
            MpCategory cat = categories.get(p.getProductType() + "|" + p.getCategoryCode());
            if (cat != null) vo.setCategoryName(cat.getName());
        }
        return vo;
    }

    @Override
    public Map<String, Object> list(PublicationListQuery query) {
        Map<String, Object> params = new HashMap<>();
        params.put("category", query.getCategory());
        params.put("productType", query.getProductType());
        params.put("keyword", query.getKeyword());
        // 公开端点(网关白名单)恒定只返回 PUBLISHED。
        // 若允许调用方传 status,匿名用户可枚举 DRAFT/PENDING/SUSPENDED —— 见 Task3 审查 Critical#1。
        // 作者查看自己的全部状态请走 /my/list (需登录),由 listByAuthor 提供。
        params.put("status", "PUBLISHED");
        params.put("sortBy", query.getSortBy() != null ? query.getSortBy() : "newest");
        params.put("offset", (query.getPage() - 1) * query.getPageSize());
        params.put("limit", query.getPageSize());

        List<MpPublication> rows = publicationMapper.selectList(params);
        int total = publicationMapper.countList(params);

        // 一次性批量加载作者 + 类目,避免 2N+1 (N 行 author/category 各 1 次单查)
        Map<Long, MpAuthor> authors = Collections.emptyMap();
        Map<String, MpCategory> categories = Collections.emptyMap();
        if (!rows.isEmpty()) {
            Set<Long> authorIds = new HashSet<>();
            // 类目按 productType 分桶,每桶 1 次 IN 查询(避免 AGENT+MODULE code 冲突)
            Map<String, Set<String>> codesByType = new HashMap<>();
            for (MpPublication p : rows) {
                if (p.getAuthorId() != null) authorIds.add(p.getAuthorId());
                if (p.getProductType() != null && p.getCategoryCode() != null) {
                    codesByType.computeIfAbsent(p.getProductType(), k -> new HashSet<>())
                            .add(p.getCategoryCode());
                }
            }
            if (!authorIds.isEmpty()) {
                authors = authorMapper.selectByIds(authorIds).stream()
                        .collect(Collectors.toMap(MpAuthor::getId, a -> a));
            }
            if (!codesByType.isEmpty()) {
                Map<String, MpCategory> catMap = new HashMap<>();
                for (Map.Entry<String, Set<String>> e : codesByType.entrySet()) {
                    List<MpCategory> cats = categoryMapper.selectByCodes(e.getKey(), e.getValue());
                    for (MpCategory c : cats) {
                        catMap.put(e.getKey() + "|" + c.getCode(), c);
                    }
                }
                categories = catMap;
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("rows", rows.stream().map(p -> toVO(p, authors, categories)).collect(Collectors.toList()));
        data.put("total", total);
        data.put("page", query.getPage());
        data.put("pageSize", query.getPageSize());
        return data;
    }

    @Override
    public List<MpPublication> listByAuthor(Long authorId) {
        return publicationMapper.selectByAuthorId(authorId);
    }

    @Override
    public List<PublicationVO> listByAuthorVOs(Long authorId) {
        List<MpPublication> rows = publicationMapper.selectByAuthorId(authorId);
        if (rows.isEmpty()) return List.of();

        // 一次性批量加载作者 + 类目,替代原本每行 2 次单查 (2N → 2)
        Set<Long> authorIds = new HashSet<>();
        Map<String, Set<String>> codesByType = new HashMap<>();
        for (MpPublication p : rows) {
            if (p.getAuthorId() != null) authorIds.add(p.getAuthorId());
            if (p.getProductType() != null && p.getCategoryCode() != null) {
                codesByType.computeIfAbsent(p.getProductType(), k -> new HashSet<>())
                        .add(p.getCategoryCode());
            }
        }

        Map<Long, MpAuthor> authors = authorMapper.selectByIds(authorIds).stream()
                .collect(Collectors.toMap(MpAuthor::getId, a -> a));
        Map<String, MpCategory> categories = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : codesByType.entrySet()) {
            List<MpCategory> cats = categoryMapper.selectByCodes(e.getKey(), e.getValue());
            for (MpCategory c : cats) {
                categories.put(e.getKey() + "|" + c.getCode(), c);
            }
        }

        return rows.stream().map(p -> toVO(p, authors, categories)).collect(Collectors.toList());
    }
}
```

- [x] **Step 3: Write PublicationController**

```java
package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.marketplace.domain.MpAuthor;
import com.ruoyi.opc.marketplace.dto.PublicationCreateRequest;
import com.ruoyi.opc.marketplace.dto.PublicationEditRequest;
import com.ruoyi.opc.marketplace.dto.PublicationListQuery;
import com.ruoyi.opc.marketplace.dto.PublicationVO;
import com.ruoyi.opc.marketplace.service.AuthorService;
import com.ruoyi.opc.marketplace.service.PublicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/opc/marketplace/publication")
@RequiredArgsConstructor
public class PublicationController {

    private final PublicationService publicationService;
    private final AuthorService authorService;

    @GetMapping("/list")
    public R<Map<String, Object>> list(PublicationListQuery query) {
        return R.ok(publicationService.list(query));
    }

    @GetMapping("/search")
    public R<Map<String, Object>> search(PublicationListQuery query) {
        return R.ok(publicationService.list(query));
    }

    @GetMapping("/{id}")
    public R<PublicationVO> get(@PathVariable Long id) {
        // 公开 GET(网关白名单 /opc/marketplace/publication/{id:[0-9]+} 允许匿名),
        // 必须走 status='PUBLISHED' 过滤版本,否则匿名调用方可遍历 id 枚举 DRAFT/PENDING/SUSPENDED 草稿.
        // 作者要读自己 DRAFT 用于编辑,请走 GET /my/{id} (见下).
        var p = publicationService.getPublishedById(id);
        return R.ok(publicationService.toVO(p));
    }

    /**
     * 作者读取自己任一状态商品(含 DRAFT/PENDING/SUSPENDED)用于编辑表单填充。
     * 需登录,不在白名单中,且会校验 authorId 归属,非作者本人 → 403.
     */
    @GetMapping("/my/{id}")
    public R<PublicationVO> myGet(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCompanyId();
        // fail-closed: 未关联企业的账号不得进入租户维度操作,禁止用 0 兜底(见 Task6 前置说明)
        if (companyId == null) throw new OpcException(400, "当前账号未关联企业,请先加入或创建企业", "MARKETPLACE");
        MpAuthor author = authorService.getByCompanyId(companyId);
        if (author == null) {
            throw new com.ruoyi.opc.common.exception.OpcException(403, "请先申请成为作者", "MARKETPLACE");
        }
        MpPublication p = publicationService.getById(id);
        if (!p.getAuthorId().equals(author.getId())) {
            throw new com.ruoyi.opc.common.exception.OpcException(403, "只能读取自己的商品", "MARKETPLACE");
        }
        return R.ok(publicationService.toVO(p));
    }

    @GetMapping("/my/list")
    public R<List<PublicationVO>> myList() {
        Long companyId = SecurityUtils.getCompanyId();
        // fail-closed: 未关联企业的账号不得进入租户维度操作,禁止用 0 兜底(见 Task6 前置说明)
        if (companyId == null) throw new OpcException(400, "当前账号未关联企业,请先加入或创建企业", "MARKETPLACE");
        MpAuthor author = authorService.getByCompanyId(companyId);
        if (author == null) return R.ok(List.of());
        // 走批量 VO 装配,避免 2N+1 (作者侧 listByAuthor 原本每行 author+category 各 1 次单查).
        return R.ok(publicationService.listByAuthorVOs(author.getId()));
    }

    @PostMapping
    public R<PublicationVO> create(@Valid @RequestBody PublicationCreateRequest req) {
        Long companyId = SecurityUtils.getCompanyId();
        // fail-closed: 未关联企业的账号不得进入租户维度操作,禁止用 0 兜底(见 Task6 前置说明)
        if (companyId == null) throw new OpcException(400, "当前账号未关联企业,请先加入或创建企业", "MARKETPLACE");
        MpAuthor author = authorService.getByCompanyId(companyId);
        if (author == null) {
            throw new com.ruoyi.opc.common.exception.OpcException(403, "请先申请成为作者", "MARKETPLACE");
        }
        Long userId = SecurityUtils.getUserId();
        var p = publicationService.create(req, author.getId(), userId);
        return R.ok(publicationService.toVO(p));
    }

    @PutMapping("/{id}")
    public R<PublicationVO> edit(@PathVariable Long id, @Valid @RequestBody PublicationEditRequest req) {
        Long companyId = SecurityUtils.getCompanyId();
        // fail-closed: 未关联企业的账号不得进入租户维度操作,禁止用 0 兜底(见 Task6 前置说明)
        if (companyId == null) throw new OpcException(400, "当前账号未关联企业,请先加入或创建企业", "MARKETPLACE");
        MpAuthor author = authorService.getByCompanyId(companyId);
        if (author == null) throw new com.ruoyi.opc.common.exception.OpcException(403, "非作者", "MARKETPLACE");
        var p = publicationService.edit(id, req, author.getId());
        return R.ok(publicationService.toVO(p));
    }

    @PostMapping("/{id}/submit-audit")
    public R<PublicationVO> submitAudit(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCompanyId();
        // fail-closed: 未关联企业的账号不得进入租户维度操作,禁止用 0 兜底(见 Task6 前置说明)
        if (companyId == null) throw new OpcException(400, "当前账号未关联企业,请先加入或创建企业", "MARKETPLACE");
        MpAuthor author = authorService.getByCompanyId(companyId);
        var p = publicationService.submitAudit(id, author.getId());
        return R.ok(publicationService.toVO(p));
    }

    @PostMapping("/{id}/online")
    public R<PublicationVO> online(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCompanyId();
        // fail-closed: 未关联企业的账号不得进入租户维度操作,禁止用 0 兜底(见 Task6 前置说明)
        if (companyId == null) throw new OpcException(400, "当前账号未关联企业,请先加入或创建企业", "MARKETPLACE");
        MpAuthor author = authorService.getByCompanyId(companyId);
        var p = publicationService.online(id, author.getId());
        return R.ok(publicationService.toVO(p));
    }

    @PostMapping("/{id}/offline")
    public R<PublicationVO> offline(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCompanyId();
        // fail-closed: 未关联企业的账号不得进入租户维度操作,禁止用 0 兜底(见 Task6 前置说明)
        if (companyId == null) throw new OpcException(400, "当前账号未关联企业,请先加入或创建企业", "MARKETPLACE");
        MpAuthor author = authorService.getByCompanyId(companyId);
        var p = publicationService.offline(id, author.getId());
        return R.ok(publicationService.toVO(p));
    }
}
```

- [x] **Step 4: Write 8 unit tests for PublicationController** （部分：plan 列的 11 个用例里 10 个在，get_returnsVO 改名成 get_usesPublishedOnlyLookup；该文件实际 28 个 @Test）

Pattern: 正常 / 鉴权失败 / 业务异常三类. 复制 AuthorControllerTest 的 `@WebMvcTest` + `MockedStatic<SecurityUtils>` 模式。

Test cases:
- `list_returnsPage` — list returns only PUBLISHED (硬编码,见 Task3 review Critical#1)
- `get_returnsVO` — get existing PUBLISHED id
- `get_404_whenNotFound` — id=999 (selectPublishedById 返回 null → service 抛 404)
- `create_returnsVO` — create request valid
- `create_403_whenNotAuthor` — author null
- `create_409_whenCodeExists` — code dup
- `submitAudit_returnsPublished` — DRAFT → PUBLISHED
- `offline_returnsDeprecated` — PUBLISHED → DEPRECATED
- `myGet_returnsVO` — 作者读取自己的 DRAFT (走 getById,不受 PUBLISHED 过滤)
- `myGet_403_whenNotOwner` — 别人家的 DRAFT → 403
- `myList_returnsVOs` — 作者列表 (走 listByAuthorVOs 批量填充)

(Generate all 8 tests by mirroring AuthorControllerTest structure. Each test 15-30 lines.)

- [x] **Step 5: Write 10 unit tests for PublicationService** （部分：plan 列的 10 个用例里 9 个在，edit_success 改名成 edit_success_onOwnDraft；该文件实际 42 个 @Test）

Test cases using JUnit5 + Mockito (mock all mappers):
- `create_success_AGENT`
- `create_success_MODULE`
- `create_400_whenAgentMissingId`
- `create_409_whenCodeExists`
- `edit_success`
- `edit_403_whenNotAuthor`
- `submitAudit_success`
- `submitAudit_400_whenNotDraft`
- `getPublishedById_404_whenMissing` — selectPublishedById 返回 null → service 抛 404 (P1 review 新增)
- `listByAuthorVOs_batchesAuthorAndCategory` — 验证 selectByIds + selectByCodes 各 1 次,不再 N+1 (P1 review 新增)

(Generate these 10 tests by following opc-community W52 `ModuleServiceImplTest` pattern. Each test 20-40 lines.)

- [ ] **Step 6: Run all publication tests** （未完成：仓库里查不到运行结果；target/ 下没有 surefire-reports，也没有 CI 记录）

```bash
cd springboot3/ruoyi-modules
mvn -pl opc-marketplace test -Dtest='PublicationControllerTest,PublicationServiceTest'
```

Expected: 18 tests pass.

- [x] **Step 7: Commit**

```bash
git add springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/{service/PublicationService.java,service/impl/PublicationServiceImpl.java,controller/PublicationController.java}
git add springboot3/ruoyi-modules/opc-marketplace/src/test/java/com/ruoyi/opc/marketplace/{controller/PublicationControllerTest.java,service/impl/PublicationServiceTest.java}
git commit -m "feat(marketplace): PublicationService + Controller + 18 tests (P1)"
```

---

> ## ⚠️ 租户身份硬性约束 — Task 6 / 7 / 8 必读
>
> W80.1 起，平台登录态携带租户身份：`SecurityUtils.getCompanyId()`
> （定义在 `ruoyi-common-security`，租户关系由登录时按 `opc_company_member` 解析后
> 写入 `LoginUser.companyId`，Redis 下发给所有服务）。
>
> **`getCompanyId()` 返回 null 表示该账号未关联任何企业（未接受邀请）。**
>
> 所有租户维度的操作（install / review / 订阅）**必须在入口显式校验 companyId 非 null**，
> 为 null 时返回 400「当前账号未关联企业，请先加入或创建企业」——
>
> **禁止用 0 或任何默认值兜底**，否则会静默写入跨租户脏数据
> （`mp_install.tenant_company_id` 是 `NOT NULL`，兜底 0 会被 DB 接受，
> 生成一条归属「公司 0」的比对不出来的记录，事后无法与真实公司对账）。
>
> 补充说明：
> - `tenant_company_id` **一律取自 `SecurityUtils.getCompanyId()`，不得从请求体 / query 取值**。
>   之前的设计里 `AuthorApplyRequest.authorCompanyId` 来自请求体，那是「申请成为作者」
>   的业务字段，可以保留；但 install / review 的租户维度字段只能来自登录态。
> - 「无租户」(null) 与「租户状态未知」(表缺失等) 两种情况在 resolver 里都会返回 null，
>   靠 ERROR 日志区分；因此 400 的文案统一用「当前账号未关联企业」，排查时先看
>   ruoyi-system 日志里有没有 `[UserCompanyResolver]` 的 ERROR。

### Task 6: InstallService + Feign + Controller

**Files:**
- Create: `springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/feign/AgentHubClient.java`
- Create: `springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/service/InstallService.java`
- Create: `.../service/impl/InstallServiceImpl.java`
- Create: `.../controller/InstallController.java`
- Test: `.../test/.../InstallControllerTest.java` (8 tests)

- [x] **Step 1: Write AgentHubClient Feign interface** （部分：返回类型是 R<Map<String,Object>>、用 contextId 而非 name、body 里没有 from-source 头；plan 的写法后来在 e701ca7 修过）

```java
package com.ruoyi.opc.marketplace.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "opc-agent-hub", fallbackFactory = AgentHubClientFallback.class)
public interface AgentHubClient {

    @PostMapping("/opc/agent/instance/create")
    Map<String, Object> createAgentInstance(
        @RequestHeader("from-source") String fromSource,
        @RequestBody Map<String, Object> body
    );
}
```

`AgentHubClientFallback.java`:
```java
package com.ruoyi.opc.marketplace.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class AgentHubClientFallback implements FallbackFactory<AgentHubClient> {
    @Override
    public AgentHubClient create(Throwable cause) {
        log.error("[AgentHubClient] fallback triggered: {}", cause.getMessage());
        return (fromSource, body) -> {
            throw new com.ruoyi.opc.common.exception.OpcException(503,
                "opc-agent-hub 调用失败: " + cause.getMessage(), "MARKETPLACE");
        };
    }
}
```

- [x] **Step 2: Write InstallService interface**

```java
package com.ruoyi.opc.marketplace.service;

import com.ruoyi.opc.marketplace.domain.MpInstall;
import java.util.List;
import java.util.Map;

public interface InstallService {
    MpInstall install(Long publicationId, Long tenantCompanyId, Long userId);
    void uninstall(Long publicationId, Long tenantCompanyId, Long userId);
    MpInstall getInstallStatus(Long publicationId, Long tenantCompanyId, Long userId);
    List<MpInstall> listByUser(Long userId);
    List<MpInstall> listByTenant(Long tenantCompanyId);
}
```

- [x] **Step 3: Write InstallServiceImpl**

```java
package com.ruoyi.opc.marketplace.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpInstall;
import com.ruoyi.opc.marketplace.domain.MpPublication;
import com.ruoyi.opc.marketplace.feign.AgentHubClient;
import com.ruoyi.opc.marketplace.mapper.MpInstallMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.service.InstallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstallServiceImpl implements InstallService {

    private final MpInstallMapper installMapper;
    private final MpPublicationMapper publicationMapper;
    private final AgentHubClient agentHubClient;

    @Override
    public MpInstall install(Long publicationId, Long tenantCompanyId, Long userId) {
        MpPublication p = publicationMapper.selectById(publicationId);
        if (p == null) throw new OpcException(404, "商品不存在: " + publicationId, "MARKETPLACE");
        if (!"PUBLISHED".equals(p.getStatus())) {
            throw new OpcException(400, "商品未发布，无法安装", "MARKETPLACE");
        }

        MpInstall existing = installMapper.selectUnique(publicationId, tenantCompanyId, userId);
        if (existing != null && "ACTIVE".equals(existing.getStatus())) {
            throw new OpcException(409, "已安装，请勿重复", "MARKETPLACE");
        }

        Long instanceId = null;
        if ("AGENT".equals(p.getProductType())) {
            Map<String, Object> body = new HashMap<>();
            body.put("agentDefinitionId", p.getAgentDefinitionId());
            body.put("tenantCompanyId", tenantCompanyId);
            body.put("userId", userId);
            try {
                Map<String, Object> resp = agentHubClient.createAgentInstance("marketplace", body);
                instanceId = ((Number) resp.get("id")).longValue();
            } catch (Exception e) {
                throw new OpcException(503, "创建 AgentInstance 失败: " + e.getMessage(), "MARKETPLACE");
            }
        }

        // uk_install 不含 deleted → 既有行（含 UNINSTALLED 软删除）仍占唯一键，必须复活而非 INSERT
        if (existing != null) {
            installMapper.reactivate(existing.getId(), instanceId, LocalDateTime.now(), String.valueOf(userId));
            existing.setAgentInstanceId(instanceId);
            existing.setStatus("ACTIVE");
            existing.setInstalledAt(LocalDateTime.now());
            log.info("[Install] reinstalled (revived row) publicationId={} userId={} instanceId={}",
                    publicationId, userId, instanceId);
            return existing;
        }

        MpInstall install = new MpInstall();
        install.setPublicationId(publicationId);
        install.setTenantCompanyId(tenantCompanyId);
        install.setUserId(userId);
        install.setAgentInstanceId(instanceId);
        install.setInstallSource("MARKET");
        install.setStatus("ACTIVE");
        install.setInstalledAt(LocalDateTime.now());
        install.setCreateBy(String.valueOf(userId));
        install.setUpdateBy(String.valueOf(userId));
        installMapper.insert(install);

        publicationMapper.incrementInstallCount(publicationId);
        log.info("[Install] installed publicationId={} userId={} instanceId={}",
                publicationId, userId, instanceId);
        return install;
    }

    @Override
    public void uninstall(Long publicationId, Long tenantCompanyId, Long userId) {
        MpInstall existing = installMapper.selectUnique(publicationId, tenantCompanyId, userId);
        if (existing == null) throw new OpcException(404, "未安装", "MARKETPLACE");
        if (!"ACTIVE".equals(existing.getStatus())) {
            throw new OpcException(409, "该安装记录已卸载", "MARKETPLACE");
        }
        installMapper.updateStatus(existing.getId(), "UNINSTALLED", LocalDateTime.now());
        log.info("[uninstall] publicationId={} userId={}", publicationId, userId);
    }

    @Override
    public MpInstall getInstallStatus(Long publicationId, Long tenantCompanyId, Long userId) {
        return installMapper.selectUnique(publicationId, tenantCompanyId, userId);
    }

    @Override
    public List<MpInstall> listByUser(Long userId) {
        return installMapper.selectByUser(userId);
    }

    @Override
    public List<MpInstall> listByTenant(Long tenantCompanyId) {
        return installMapper.selectByTenant(tenantCompanyId);
    }
}
```

- [x] **Step 4: Write InstallController**

```java
package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.marketplace.domain.MpInstall;
import com.ruoyi.opc.marketplace.service.InstallService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/opc/marketplace/install")
@RequiredArgsConstructor
public class InstallController {

    private final InstallService installService;

    @PostMapping("/{publicationId}")
    public R<MpInstall> install(@PathVariable Long publicationId) {
        Long userId = SecurityUtils.getUserId();
        Long companyId = SecurityUtils.getCompanyId();
        // fail-closed: 未关联企业的账号不得进入租户维度操作,禁止用 0 兜底(见 Task6 前置说明)
        if (companyId == null) throw new OpcException(400, "当前账号未关联企业,请先加入或创建企业", "MARKETPLACE");
        return R.ok(installService.install(publicationId, companyId, userId));
    }

    @DeleteMapping("/{publicationId}")
    public R<Void> uninstall(@PathVariable Long publicationId) {
        Long userId = SecurityUtils.getUserId();
        Long companyId = SecurityUtils.getCompanyId();
        // fail-closed: 未关联企业的账号不得进入租户维度操作,禁止用 0 兜底(见 Task6 前置说明)
        if (companyId == null) throw new OpcException(400, "当前账号未关联企业,请先加入或创建企业", "MARKETPLACE");
        installService.uninstall(publicationId, companyId, userId);
        return R.ok();
    }

    @GetMapping("/my")
    public R<List<MpInstall>> my() {
        Long userId = SecurityUtils.getUserId();
        return R.ok(installService.listByUser(userId));
    }

    @GetMapping("/{publicationId}/status")
    public R<MpInstall> status(@PathVariable Long publicationId) {
        Long userId = SecurityUtils.getUserId();
        Long companyId = SecurityUtils.getCompanyId();
        // fail-closed: 未关联企业的账号不得进入租户维度操作,禁止用 0 兜底(见 Task6 前置说明)
        if (companyId == null) throw new OpcException(400, "当前账号未关联企业,请先加入或创建企业", "MARKETPLACE");
        return R.ok(installService.getInstallStatus(publicationId, companyId, userId));
    }

    @PostMapping("/{publicationId}/upgrade")
    public R<Void> upgrade(@PathVariable Long publicationId) {
        // P1: no-op (P2 will add real upgrade)
        return R.ok();
    }
}
```

- [x] **Step 5: Write 8 unit tests for InstallController**

Tests using `@WebMvcTest` + `MockedStatic<SecurityUtils>` + `@MockBean InstallService`:
- `install_returnsOk`
- `install_409_whenAlreadyInstalled`
- `install_400_whenNotPublished`
- `uninstall_returnsOk`
- `uninstall_404_whenNotInstalled`
- `my_returnsList`
- `status_returnsInstall`
- `upgrade_returnsOk_noop`

(Generate by mirroring AuthorControllerTest. Each test 15-25 lines.)

- [ ] **Step 6: Run all install tests** （未完成：仓库里查不到运行结果；target/ 下没有 surefire-reports，也没有 CI 记录）

```bash
cd springboot3/ruoyi-modules
mvn -pl opc-marketplace test -Dtest=InstallControllerTest
```

Expected: 8 tests pass.

- [x] **Step 7: Commit**

```bash
git add springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/{feign,service/InstallService.java,service/impl/InstallServiceImpl.java,controller/InstallController.java}
git add springboot3/ruoyi-modules/opc-marketplace/src/test/java/com/ruoyi/opc/marketplace/controller/InstallControllerTest.java
git commit -m "feat(marketplace): InstallService + Feign + Controller + 8 tests (P1)"
```

---

### Task 7: ReviewService + Controller

**Files:**
- Create: `.../service/ReviewService.java`
- Create: `.../service/impl/ReviewServiceImpl.java`
- Create: `.../controller/ReviewController.java`
- Test: `.../test/.../ReviewControllerTest.java` (8 tests)

- [x] **Step 1: Write ReviewService interface**

```java
package com.ruoyi.opc.marketplace.service;

import com.ruoyi.opc.marketplace.domain.MpReview;
import com.ruoyi.opc.marketplace.dto.ReviewCreateRequest;
import com.ruoyi.opc.marketplace.dto.ReviewUpdateRequest;
import java.util.List;
import java.util.Map;

public interface ReviewService {
    MpReview create(ReviewCreateRequest req, Long userId, Long tenantCompanyId);
    MpReview update(Long id, ReviewUpdateRequest req, Long userId);
    void delete(Long id, Long userId);
    Map<String, Object> listByPublication(Long publicationId, int page, int pageSize);
}
```

- [x] **Step 2: Write ReviewServiceImpl**

```java
package com.ruoyi.opc.marketplace.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpPublication;
import com.ruoyi.opc.marketplace.domain.MpReview;
import com.ruoyi.opc.marketplace.dto.ReviewCreateRequest;
import com.ruoyi.opc.marketplace.dto.ReviewUpdateRequest;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.mapper.MpReviewMapper;
import com.ruoyi.opc.marketplace.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final MpReviewMapper reviewMapper;
    private final MpPublicationMapper publicationMapper;

    @Override
    public MpReview create(ReviewCreateRequest req, Long userId, Long tenantCompanyId) {
        MpPublication p = publicationMapper.selectById(req.getPublicationId());
        if (p == null) throw new OpcException(404, "商品不存在", "MARKETPLACE");

        MpReview existing = reviewMapper.selectByPublicationAndUser(req.getPublicationId(), userId);
        if (existing != null) throw new OpcException(409, "已评价，请勿重复", "MARKETPLACE");

        MpReview r = new MpReview();
        r.setPublicationId(req.getPublicationId());
        r.setTenantCompanyId(tenantCompanyId);
        r.setUserId(userId);
        r.setRating(req.getRating());
        r.setContent(req.getContent());
        r.setStatus("VISIBLE");
        r.setCreateBy(String.valueOf(userId));
        r.setUpdateBy(String.valueOf(userId));
        reviewMapper.insert(r);

        // update publication avg rating
        BigDecimal avg = reviewMapper.averageForPublication(req.getPublicationId());
        int count = reviewMapper.countForPublication(req.getPublicationId());
        if (count > 0) {
            publicationMapper.updateRating(req.getPublicationId(),
                avg.setScale(2, RoundingMode.HALF_UP), count);
        }
        log.info("[Review] created publicationId={} userId={} rating={}", req.getPublicationId(), userId, req.getRating());
        return r;
    }

    @Override
    public MpReview update(Long id, ReviewUpdateRequest req, Long userId) {
        MpReview r = reviewMapper.selectById(id);
        if (r == null) throw new OpcException(404, "评价不存在", "MARKETPLACE");
        if (!r.getUserId().equals(userId)) throw new OpcException(403, "只有本人可修改", "MARKETPLACE");
        if (req.getRating() != null) r.setRating(req.getRating());
        if (req.getContent() != null) r.setContent(req.getContent());
        r.setUpdateBy(String.valueOf(userId));
        reviewMapper.updateById(r);

        // recompute avg
        BigDecimal avg = reviewMapper.averageForPublication(r.getPublicationId());
        int count = reviewMapper.countForPublication(r.getPublicationId());
        if (count > 0) {
            publicationMapper.updateRating(r.getPublicationId(),
                avg.setScale(2, RoundingMode.HALF_UP), count);
        }
        return r;
    }

    @Override
    public void delete(Long id, Long userId) {
        MpReview r = reviewMapper.selectById(id);
        if (r == null) throw new OpcException(404, "评价不存在", "MARKETPLACE");
        if (!r.getUserId().equals(userId)) throw new OpcException(403, "只有本人可删除", "MARKETPLACE");
        reviewMapper.updateStatus(id, "HIDDEN");
    }

    @Override
    public Map<String, Object> listByPublication(Long publicationId, int page, int pageSize) {
        Map<String, Object> params = new HashMap<>();
        params.put("publicationId", publicationId);
        params.put("status", "VISIBLE");
        params.put("offset", (page - 1) * pageSize);
        params.put("limit", pageSize);
        var rows = reviewMapper.selectList(params);
        int total = reviewMapper.countList(params);
        Map<String, Object> data = new HashMap<>();
        data.put("rows", rows);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }
}
```

- [x] **Step 3: Write ReviewController**

```java
package com.ruoyi.opc.marketplace.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.marketplace.domain.MpReview;
import com.ruoyi.opc.marketplace.dto.ReviewCreateRequest;
import com.ruoyi.opc.marketplace.dto.ReviewUpdateRequest;
import com.ruoyi.opc.marketplace.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/opc/marketplace/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public R<MpReview> create(@Valid @RequestBody ReviewCreateRequest req) {
        Long userId = SecurityUtils.getUserId();
        Long companyId = SecurityUtils.getCompanyId();
        // fail-closed: 未关联企业的账号不得进入租户维度操作,禁止用 0 兜底(见 Task6 前置说明)
        if (companyId == null) throw new OpcException(400, "当前账号未关联企业,请先加入或创建企业", "MARKETPLACE");
        return R.ok(reviewService.create(req, userId, companyId));
    }

    @GetMapping("/list/{publicationId}")
    public R<Map<String, Object>> list(@PathVariable Long publicationId,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(reviewService.listByPublication(publicationId, page, pageSize));
    }

    @PutMapping("/{id}")
    public R<MpReview> update(@PathVariable Long id, @Valid @RequestBody ReviewUpdateRequest req) {
        Long userId = SecurityUtils.getUserId();
        return R.ok(reviewService.update(id, req, userId));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getUserId();
        reviewService.delete(id, userId);
        return R.ok();
    }
}
```

- [x] **Step 4: Write 8 unit tests**

Tests:
- `create_returnsReview`
- `create_404_whenPublicationMissing`
- `create_409_whenAlreadyReviewed`
- `update_returnsReview`
- `update_403_whenNotOwner`
- `delete_returnsOk`
- `delete_403_whenNotOwner`
- `list_returnsPage`

(Generate by mirroring AuthorControllerTest. Each 15-25 lines.)

- [ ] **Step 5: Run tests** （未完成：仓库里查不到运行结果；target/ 下没有 surefire-reports，也没有 CI 记录）

```bash
mvn -pl opc-marketplace test -Dtest=ReviewControllerTest
```

Expected: 8 tests pass.

- [x] **Step 6: Commit**

```bash
git add springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/{service/ReviewService.java,service/impl/ReviewServiceImpl.java,controller/ReviewController.java}
git add springboot3/ruoyi-modules/opc-marketplace/src/test/java/com/ruoyi/opc/marketplace/controller/ReviewControllerTest.java
git commit -m "feat(marketplace): ReviewService + Controller + 8 tests (P1)"
```

---

### Task 8: CategoryService + TagService + Controllers

**Files:**
- Create: `.../service/CategoryService.java` + impl + `CategoryController.java` (5 tests)
- Create: `.../service/TagService.java` + impl + `TagController.java` (4 tests)

- [x] **Step 1: Write CategoryService interface + impl**

```java
public interface CategoryService {
    List<MpCategory> tree();
    List<MpCategory> listByType(String productType);
}
```

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final MpCategoryMapper categoryMapper;

    @Override
    public List<MpCategory> tree() {
        // P1: flat list (no parent_id handling)
        return categoryMapper.selectAll();
    }

    @Override
    public List<MpCategory> listByType(String productType) {
        return categoryMapper.selectByProductType(productType);
    }
}
```

- [x] **Step 2: Write CategoryController**

```java
@RestController
@RequestMapping("/opc/marketplace/category")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping("/tree")
    public R<List<MpCategory>> tree() { return R.ok(categoryService.tree()); }

    @GetMapping("/list")
    public R<List<MpCategory>> list(@RequestParam(required = false) String productType) {
        if (productType == null || productType.isEmpty()) return R.ok(categoryService.tree());
        return R.ok(categoryService.listByType(productType));
    }

    @GetMapping("/{id}")
    public R<MpCategory> get(@PathVariable Long id) {
        MpCategory c = categoryService.tree().stream().filter(x -> x.getId().equals(id)).findFirst()
            .orElseThrow(() -> new OpcException(404, "类目不存在", "MARKETPLACE"));
        return R.ok(c);
    }
}
```

- [x] **Step 3: Write TagService interface + impl**

```java
public interface TagService {
    List<MpTag> list();
    List<MpTag> listByCategory(String category);
}
```

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {
    private final MpTagMapper tagMapper;
    @Override public List<MpTag> list() { return tagMapper.selectAll(); }
    @Override public List<MpTag> listByCategory(String category) { return tagMapper.selectByCategory(category); }
}
```

- [x] **Step 4: Write TagController**

```java
@RestController
@RequestMapping("/opc/marketplace/tag")
@RequiredArgsConstructor
public class TagController {
    private final TagService tagService;
    @GetMapping("/list")
    public R<List<MpTag>> list(@RequestParam(required = false) String category) {
        if (category == null || category.isEmpty()) return R.ok(tagService.list());
        return R.ok(tagService.listByCategory(category));
    }
}
```

- [x] **Step 5: Write 5 + 4 unit tests**

CategoryControllerTest (5):
- `tree_returnsList`
- `list_withProductType_returnsList`
- `list_noParam_returnsAll`
- `get_returnsCategory`
- `get_404_whenMissing`

TagControllerTest (4):
- `list_noParam_returnsAll`
- `list_withCategory_returnsFiltered`
- `list_empty_returnsOk`
- (catch-all error log not tested)

- [ ] **Step 6: Run tests** （未完成：仓库里查不到运行结果；target/ 下没有 surefire-reports，也没有 CI 记录）

```bash
mvn -pl opc-marketplace test -Dtest='CategoryControllerTest,TagControllerTest'
```

Expected: 9 tests pass.

- [x] **Step 7: Commit**

```bash
git add springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/{service/CategoryService.java,service/TagService.java,service/impl/CategoryServiceImpl.java,service/impl/TagServiceImpl.java,controller/CategoryController.java,controller/TagController.java}
git add springboot3/ruoyi-modules/opc-marketplace/src/test/java/com/ruoyi/opc/marketplace/controller/{CategoryControllerTest.java,TagControllerTest.java}
git commit -m "feat(marketplace): Category + Tag + Controllers + 9 tests (P1)"
```

---

### Task 8.5: 补充服务测试（凑足 60+ 单测）

**Files:**
- Test: `.../test/.../service/impl/AuthorServiceTest.java` (5 tests)
- Test: `.../test/.../service/impl/InstallServiceTest.java` (5 tests)
- Test: `.../test/.../service/impl/ReviewServiceTest.java` (4 tests)
- Test: `.../test/.../controller/AuthorControllerTest.java` (扩充 5 tests)

- [x] **Step 1: Write AuthorServiceTest (5 tests)**

```java
package com.ruoyi.opc.marketplace.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpAuthor;
import com.ruoyi.opc.marketplace.dto.AuthorApplyRequest;
import com.ruoyi.opc.marketplace.dto.AuthorListQuery;
import com.ruoyi.opc.marketplace.dto.AuthorVO;
import com.ruoyi.opc.marketplace.mapper.MpAuthorMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthorServiceTest {
    @Mock MpAuthorMapper authorMapper;
    @Mock MpPublicationMapper publicationMapper;
    @InjectMocks AuthorServiceImpl authorService;

    @BeforeEach
    void setUp() { MockitoAnnotations.openMocks(this); }

    @Test
    void apply_409_whenCompanyAlreadyAuthor() {
        when(authorMapper.selectByCompanyId(100L)).thenReturn(MpAuthor.builder().id(1L).build());
        assertThatThrownBy(() -> authorService.apply(
            AuthorApplyRequest.builder().authorName("X").contactEmail("a@b.com").build(),
            100L, 1L))
            .isInstanceOf(OpcException.class)
            .hasMessageContaining("已是作者");
    }

    @Test
    void apply_success_returnsAuthor() {
        when(authorMapper.selectByCompanyId(200L)).thenReturn(null);
        when(authorMapper.insert(any(MpAuthor.class))).thenAnswer(inv -> {
            MpAuthor a = inv.getArgument(0);
            a.setId(99L);
            return 1;
        });
        MpAuthor result = authorService.apply(
            AuthorApplyRequest.builder().authorName("X")
                .contactEmail("a@b.com").build(), 200L, 1L);
        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getLevel()).isEqualTo("NORMAL");
    }

    @Test
    void getById_404_whenNotFound() {
        when(authorMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> authorService.getById(999L))
            .isInstanceOf(OpcException.class)
            .hasMessageContaining("作者不存在");
    }

    @Test
    void toVO_copiesFields() {
        MpAuthor a = MpAuthor.builder()
            .id(1L).authorCompanyId(2L).authorName("X")
            .contactEmail("a@b.com").level("PREMIUM")
            .totalAgents(5).totalSubscribers(10)
            .totalRevenue(new BigDecimal("1000.50"))
            .status("ACTIVE").build();
        AuthorVO vo = authorService.toVO(a);
        assertThat(vo.getAuthorName()).isEqualTo("X");
        assertThat(vo.getLevel()).isEqualTo("PREMIUM");
        assertThat(vo.getTotalRevenue()).isEqualByComparingTo("1000.50");
    }

    @Test
    void stats_returnsAuthorAndPublicationCount() {
        MpAuthor a = MpAuthor.builder().id(1L).authorCompanyId(2L).build();
        when(authorMapper.selectById(1L)).thenReturn(a);
        when(publicationMapper.countList(any())).thenReturn(5);
        var stats = authorService.stats(1L);
        assertThat(stats.get("publicationCount")).isEqualTo(5);
    }
}
```

- [x] **Step 2: Write InstallServiceTest (5 tests)**

```java
package com.ruoyi.opc.marketplace.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpInstall;
import com.ruoyi.opc.marketplace.domain.MpPublication;
import com.ruoyi.opc.marketplace.feign.AgentHubClient;
import com.ruoyi.opc.marketplace.mapper.MpInstallMapper;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class InstallServiceTest {
    @Mock MpInstallMapper installMapper;
    @Mock MpPublicationMapper publicationMapper;
    @Mock AgentHubClient agentHubClient;
    @InjectMocks InstallServiceImpl installService;

    @BeforeEach
    void setUp() { MockitoAnnotations.openMocks(this); }

    @Test
    void install_404_whenPublicationMissing() {
        when(publicationMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> installService.install(999L, 1L, 1L))
            .isInstanceOf(OpcException.class);
    }

    @Test
    void install_400_whenNotPublished() {
        MpPublication p = MpPublication.builder().id(1L).status("DRAFT").productType("AGENT").build();
        when(publicationMapper.selectById(1L)).thenReturn(p);
        assertThatThrownBy(() -> installService.install(1L, 1L, 1L))
            .isInstanceOf(OpcException.class)
            .hasMessageContaining("未发布");
    }

    @Test
    void install_409_whenAlreadyInstalled() {
        MpPublication p = MpPublication.builder().id(1L).status("PUBLISHED").productType("MODULE").build();
        when(publicationMapper.selectById(1L)).thenReturn(p);
        MpInstall existing = MpInstall.builder().id(99L).status("ACTIVE").build();
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(existing);
        assertThatThrownBy(() -> installService.install(1L, 1L, 1L))
            .isInstanceOf(OpcException.class)
            .hasMessageContaining("已安装");
    }

    @Test
    void install_revivesRow_whenPreviouslyUninstalled() {
        // uk_install 不含 deleted → 卸载后再装必须 reactivate,不能 INSERT(否则 1062)
        MpPublication p = MpPublication.builder().id(1L).status("PUBLISHED").productType("MODULE").build();
        when(publicationMapper.selectById(1L)).thenReturn(p);
        MpInstall existing = MpInstall.builder().id(99L).status("UNINSTALLED").build();
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(existing);

        MpInstall result = installService.install(1L, 1L, 1L);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(installMapper).reactivate(eq(99L), isNull(), any(LocalDateTime.class), eq("1"));
        verify(installMapper, never()).insert(any(MpInstall.class));
        verify(publicationMapper, never()).incrementInstallCount(anyLong());
    }

    @Test
    void uninstall_409_whenAlreadyUninstalled() {
        MpInstall existing = MpInstall.builder().id(99L).status("UNINSTALLED").build();
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(existing);
        assertThatThrownBy(() -> installService.uninstall(1L, 1L, 1L))
            .isInstanceOf(OpcException.class)
            .hasMessageContaining("已卸载");
    }

    @Test
    void install_AGENT_callsAgentHubAndPersistsInstall() {
        MpPublication p = MpPublication.builder().id(1L).status("PUBLISHED")
            .productType("AGENT").agentDefinitionId(50L).build();
        when(publicationMapper.selectById(1L)).thenReturn(p);
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(null);
        when(agentHubClient.createAgentInstance(anyString(), any())).thenReturn(Map.of("id", 100L));
        when(installMapper.insert(any(MpInstall.class))).thenAnswer(inv -> {
            MpInstall i = inv.getArgument(0);
            i.setId(200L);
            return 1;
        });
        MpInstall result = installService.install(1L, 1L, 1L);
        assertThat(result.getAgentInstanceId()).isEqualTo(100L);
        assertThat(result.getInstallSource()).isEqualTo("MARKET");
        verify(publicationMapper).incrementInstallCount(1L);
    }

    @Test
    void install_MODULE_skipsAgentHub() {
        MpPublication p = MpPublication.builder().id(1L).status("PUBLISHED").productType("MODULE").moduleId(50L).build();
        when(publicationMapper.selectById(1L)).thenReturn(p);
        when(installMapper.selectUnique(1L, 1L, 1L)).thenReturn(null);
        when(installMapper.insert(any(MpInstall.class))).thenAnswer(inv -> {
            MpInstall i = inv.getArgument(0); i.setId(200L); return 1;
        });
        MpInstall result = installService.install(1L, 1L, 1L);
        assertThat(result.getAgentInstanceId()).isNull();
        verify(agentHubClient, never()).createAgentInstance(anyString(), any());
    }
}
```

- [x] **Step 3: Write ReviewServiceTest (4 tests)**

```java
package com.ruoyi.opc.marketplace.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.marketplace.domain.MpReview;
import com.ruoyi.opc.marketplace.dto.ReviewCreateRequest;
import com.ruoyi.opc.marketplace.dto.ReviewUpdateRequest;
import com.ruoyi.opc.marketplace.mapper.MpPublicationMapper;
import com.ruoyi.opc.marketplace.mapper.MpReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewServiceTest {
    @Mock MpReviewMapper reviewMapper;
    @Mock MpPublicationMapper publicationMapper;
    @InjectMocks ReviewServiceImpl reviewService;

    @BeforeEach
    void setUp() { MockitoAnnotations.openMocks(this); }

    @Test
    void create_404_whenPublicationMissing() {
        when(publicationMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> reviewService.create(
            ReviewCreateRequest.builder().publicationId(1L).rating(5).build(), 1L, 1L))
            .isInstanceOf(OpcException.class);
    }

    @Test
    void create_409_whenAlreadyReviewed() {
        when(publicationMapper.selectById(1L)).thenReturn(
            com.ruoyi.opc.marketplace.domain.MpPublication.builder().id(1L).build());
        when(reviewMapper.selectByPublicationAndUser(1L, 1L)).thenReturn(
            MpReview.builder().id(99L).build());
        assertThatThrownBy(() -> reviewService.create(
            ReviewCreateRequest.builder().publicationId(1L).rating(5).build(), 1L, 1L))
            .isInstanceOf(OpcException.class)
            .hasMessageContaining("已评价");
    }

    @Test
    void create_success_updatesAvgRating() {
        when(publicationMapper.selectById(1L)).thenReturn(
            com.ruoyi.opc.marketplace.domain.MpPublication.builder().id(1L).build());
        when(reviewMapper.selectByPublicationAndUser(1L, 1L)).thenReturn(null);
        when(reviewMapper.insert(any(MpReview.class))).thenAnswer(inv -> {
            MpReview r = inv.getArgument(0); r.setId(50L); return 1;
        });
        when(reviewMapper.averageForPublication(1L)).thenReturn(new BigDecimal("4.5"));
        when(reviewMapper.countForPublication(1L)).thenReturn(3);
        MpReview result = reviewService.create(
            ReviewCreateRequest.builder().publicationId(1L).rating(5).content("Great").build(), 1L, 1L);
        assertThat(result.getId()).isEqualTo(50L);
        verify(publicationMapper).updateRating(1L, new BigDecimal("4.50"), 3);
    }

    @Test
    void update_403_whenNotOwner() {
        MpReview r = MpReview.builder().id(1L).userId(99L).build();
        when(reviewMapper.selectById(1L)).thenReturn(r);
        assertThatThrownBy(() -> reviewService.update(1L,
            ReviewUpdateRequest.builder().rating(4).build(), 1L))
            .isInstanceOf(OpcException.class)
            .hasMessageContaining("本人");
    }
}
```

- [x] **Step 4: Add 5 more tests to AuthorControllerTest** （部分：plan 列的 8 个用例里 7 个在，me_returnsNull_whenNotAuthor 改名成 me_returnsNull_whenNotAuthorYet）

Append to existing `AuthorControllerTest.java`:

```java
    /**
     * /me 的 companyId 一律取自登录态;请求里再带 ?companyId=999 也无效(IDOR 回归)。
     * 同时证明「无公司上下文 → fail-closed 400」,而不是回落到 companyId=0。
     */
    @Test
    void me_ignoresRequestParamCompanyId_usesLoginTenant() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(100L);
        when(authorService.getByCompanyId(100L)).thenReturn(MpAuthor.builder().id(10L).build());
        when(authorService.toVO(any())).thenReturn(new AuthorVO());

        mvc.perform(get("/opc/marketplace/author/me").param("companyId", "999"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(200));

        verify(authorService).getByCompanyId(100L);
        verify(authorService, never()).getByCompanyId(999L);
    }

    @Test
    void me_failsClosed_whenNoCompany() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(null);

        mvc.perform(get("/opc/marketplace/author/me"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(400))
           .andExpect(jsonPath("$.data").doesNotExist());

        verify(authorService, never()).getByCompanyId(any());
    }

    @Test
    void me_returnsNull_whenNotAuthor() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(100L);
        when(authorService.getByCompanyId(100L)).thenReturn(null);

        mvc.perform(get("/opc/marketplace/author/me"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(200))
           .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void me_returnsAuthor_whenExists() throws Exception {
        securityUtils.when(SecurityUtils::getCompanyId).thenReturn(100L);
        when(authorService.getByCompanyId(100L)).thenReturn(MpAuthor.builder().id(10L).build());
        when(authorService.toVO(any())).thenReturn(new AuthorVO());

        mvc.perform(get("/opc/marketplace/author/me"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 公开资料脱敏。断言「泄露的值不出现在响应体」,而不是只断言
     * $.data.authorName 存在 —— 后者在泄露发生时依然会通过。
     * ⚠️ controller 测试把 service mock 掉了,证明不了 service 真的脱敏;
     *    对应的真断言在 AuthorServiceRedactionTest。
     */
    @Test
    void get_publicProfile_redactsContactFields() throws Exception {
        MpAuthor a = MpAuthor.builder().id(10L).authorName("TestAuthor")
            .contactEmail("secret-boss@example.com").contactPhone("13800001111")
            .totalRevenue(new BigDecimal("9876543.21")).build();
        AuthorVO redacted = new AuthorVO();
        redacted.setId(10L);
        redacted.setAuthorName("TestAuthor");
        when(authorService.getById(10L)).thenReturn(a);
        when(authorService.toPublicVO(a)).thenReturn(redacted);

        String body = mvc.perform(get("/opc/marketplace/author/10"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.authorName").value("TestAuthor"))
           .andExpect(jsonPath("$.data.contactEmail").doesNotExist())
           .andReturn().getResponse().getContentAsString();

        assertThat(body)
            .doesNotContain("secret-boss@example.com")
            .doesNotContain("13800001111")
            .doesNotContain("9876543.21");
        verify(authorService, never()).toVO(a);
    }

    @Test
    void list_returnsPage() throws Exception {
        when(authorService.list(any())).thenReturn(Map.of("rows", List.of(), "total", 0));

        mvc.perform(get("/opc/marketplace/author/list"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void stats_returnsMap() throws Exception {
        when(authorService.stats(10L)).thenReturn(Map.of("author", new AuthorVO(), "publicationCount", 5));

        mvc.perform(get("/opc/marketplace/author/10/stats"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(200))
           .andExpect(jsonPath("$.data.publicationCount").value(5));
    }

    @Test
    void get_404_whenNotFound() throws Exception {
        when(authorService.getById(999L))
            .thenThrow(new com.ruoyi.opc.common.exception.OpcException(404, "作者不存在", "MARKETPLACE"));

        mvc.perform(get("/opc/marketplace/author/999"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(404));
    }
```

- [ ] **Step 5: Run all tests** （未完成：仓库里查不到运行结果；target/ 下没有 surefire-reports，也没有 CI 记录）

```bash
cd springboot3/ruoyi-modules
mvn -pl opc-marketplace test
```

Expected: 68 tests pass (63 之前 + 2 PublicationService 扩充 [getPublishedById_404_whenMissing, listByAuthorVOs_batchesAuthorAndCategory] + 3 PublicationController 扩充 [myGet_returnsVO, myGet_403_whenNotOwner, myList_returnsVOs]).

- [x] **Step 6: Commit**

```bash
git add springboot3/ruoyi-modules/opc-marketplace/src/test
git commit -m "test(marketplace): 5 more tests → 68 total (P1)"
```

---

### Task 9: 应用启动 + 健康检查

**Files:**
- Modify: `springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/OpcMarketplaceApplication.java`
- Create: `springboot3/ruoyi-modules/opc-marketplace/Dockerfile`

- [ ] **Step 1: Verify application starts locally** （未完成：仓库里查不到运行结果；docs/verification/week-80/OPC-W80-VERIFICATION-opc-marketplace.md §0.2 明确写「未部署」）

```bash
cd springboot3/ruoyi-modules
mvn -pl opc-marketplace package -DskipTests
java -cp "ruoyi-modules/opc-marketplace/target/opc-marketplace.jar:ruoyi-modules/opc-marketplace/target/dependency/*" \
  com.ruoyi.opc.marketplace.OpcMarketplaceApplication
```

Expected: "OPC MARKETPLACE 启动成功" printed, Nacos connected, port 9317 listening.

- [ ] **Step 2: Smoke-test endpoints** （未完成：仓库里查不到运行结果；同一份验证报告 §0.2 明确写「未部署」/「未跑 e2e」）

```bash
# 健康
curl -s http://localhost:9317/actuator/health | python -m json.tool
# 类目
curl -s http://localhost:9317/opc/marketplace/category/tree | python -m json.tool
# 标签
curl -s http://localhost:9317/opc/marketplace/tag/list | python -m json.tool
```

Expected: all return `code: 200`.

- [x] **Step 3: Create thin-jar Dockerfile** （部分：用 eclipse-temurin:17-jre-jammy 而非 plan 写的 :21-jre-alpine，COPY 路径也按本仓库实际布局改了；thin jar 启动 + EXPOSE 9317 都在）

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY opc-marketplace.jar /app/
COPY dependency/ /app/dependency/
EXPOSE 9317
ENTRYPOINT ["java", "-cp", "opc-marketplace.jar:dependency/*", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=dev", \
  "com.ruoyi.opc.marketplace.OpcMarketplaceApplication"]
```

- [x] **Step 4: Commit**

```bash
git add springboot3/ruoyi-modules/opc-marketplace/Dockerfile
git commit -m "feat(marketplace): Dockerfile thin-jar (P1)"
```

---

### Task 10: Gateway 路由 + Nacos + Helm + Docker Compose

**Files:**
- Modify: `springboot3/ruoyi-gateway/src/main/resources/application.yml` (add `/opc/marketplace/**` route + whitelist)
- Create: `springboot3/deploy/nacos/opc-marketplace-dev.yml`
- Create: `springboot3/deploy/helm/opc/templates/deployment-marketplace.yaml`
- Create: `springboot3/deploy/helm/opc/templates/service-marketplace.yaml`
- Modify: `springboot3/deploy/helm/opc/values.yaml` (add `services.marketplace`)
- Modify: `springboot3/deploy/docker-compose.yml` (add `aiopc-marketplace`)

- [x] **Step 1: Add Gateway route + whitelist** （部分：实际白名单用 `GET:` method 前缀收紧，且 category 详情从 `**` 收成 `{id:[0-9]+}`；plan 列的路径全部在，另外多了 `GET:/opc/marketplace/category/{id:[0-9]+}`）

In `application.yml` under `spring.cloud.gateway.server.webflux.routes`, add:
```yaml
- id: opc-marketplace
  uri: http://aiopc-marketplace:9317
  predicates:
    - Path=/opc/marketplace/**
```

In `opc.security.whitelist` list, add public GET paths:
```
/opc/marketplace/category/tree
/opc/marketplace/category/list
/opc/marketplace/category/**
/opc/marketplace/tag/list
/opc/marketplace/publication/list
/opc/marketplace/publication/search
# 公开详情 — 仅返回 PUBLISHED (selectPublishedById 在 SQL 层硬过滤);
# 匿名调用方遍历 id 只能看到 PUBLISHED 行,DRAFT/PENDING/SUSPENDED 一律 404
/opc/marketplace/publication/{id:[0-9]+}
/opc/marketplace/review/list/**
# 公开作者资料 — service 层 toPublicVO 已清空 contactEmail / contactPhone / totalRevenue。
# `[0-9]+` 是必须的: 它保证 "me" 不匹配, 于是 /me 仍需登录态。
# ⚠️ 若该端点改回 toVO, 这条白名单 = 全网可读任意企业邮箱与营收。
/opc/marketplace/author/{id:[0-9]+}
/opc/marketplace/author/list
# 注: /opc/marketplace/publication/my/{id} 与 /my/list 不加白名单 —— 需登录且校验 author 归属
# 注: /opc/marketplace/author/{id}/stats 也不加白名单 —— 但 service 已对嵌套 author 脱敏
# 注: /opc/marketplace/author/{id}/publications 与 /{id}/stats 是多段路径,
#     `{id:[0-9]+}` 只匹配单段, 两者均不会被上面的白名单覆盖
```

(POST/PUT/DELETE 不加白名单，需要 token)

- [x] **Step 2: Create Nacos config**

`opc-marketplace-dev.yml`（**沿用 opc-community-dev.yml 的 dynamic-datasource 模式**，不要用单 datasource，否则项目里的 dynamic-datasource starter 兼容性问题）:

```yaml
# ============================================================
# OPC Marketplace Nacos dev 配置 (W80 P1)
# 端口: 9317
# 结构与 opc-community-dev.yml 完全一致 (避免 dynamic-datasource 兼容问题)
# ============================================================

server:
  port: 9317
  servlet:
    encoding:
      charset: UTF-8
      force: true
  tomcat:
    uri-encoding: UTF-8
spring:
  application:
    name: opc-marketplace
  datasource:
    dynamic:
      primary: master
      datasource:
        master:
          url: jdbc:mysql://${MYSQL_HOST:mysql}:${MYSQL_PORT:3306}/${MYSQL_DB:ry-vue-opc}?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
          username: ${MYSQL_USER:root}
          password: ${MYSQL_PASSWORD:Opc@2026!}

# OPC Marketplace 业务配置
opc:
  marketplace:
    base-url: http://aiopc-marketplace:9317

mybatis:
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: com.ruoyi.opc.marketplace.domain
  configuration:
    map-underscore-to-camel-case: true

logging:
  level:
    com.ruoyi.opc.marketplace: debug
```

Then import to Nacos:
```bash
cd springboot3/deploy/nacos
python import-dev.sh opc-marketplace-dev.yml opc-dev
```

- [x] **Step 3: Create Helm deployment + service**

`deployment-marketplace.yaml`:
```yaml
{{- with .Values.services.marketplace }}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: opc-marketplace
  labels:
    app: opc-marketplace
    {{- include "opc.labels" . | nindent 4 }}
spec:
  replicas: {{ .replicaCount | default 1 }}
  selector:
    matchLabels:
      app: opc-marketplace
  template:
    metadata:
      labels:
        app: opc-marketplace
        tier: business
        {{- include "opc.labels" . | nindent 8 }}
    spec:
      containers:
      - name: opc-marketplace
        image: {{ .image }}
        imagePullPolicy: {{ .pullPolicy | default "IfNotPresent" }}
        ports:
        - containerPort: 9317
        env:
        - name: NACOS_HOST
          value: {{ .nacosHost | default "nacos1" }}
        - name: SPRING_PROFILES_ACTIVE
          value: {{ .profile | default "dev" }}
        {{- with .resources }}
        resources:
          {{- toYaml . | nindent 10 }}
        {{- end }}
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 9317
          initialDelaySeconds: 30
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 9317
          initialDelaySeconds: 60
          periodSeconds: 30
{{- end }}
```

`service-marketplace.yaml`:
```yaml
{{- with .Values.services.marketplace }}
apiVersion: v1
kind: Service
metadata:
  name: opc-marketplace
  labels:
    app: opc-marketplace
spec:
  selector:
    app: opc-marketplace
  ports:
  - port: 9317
    targetPort: 9317
    protocol: TCP
    name: http
{{- end }}
```

- [x] **Step 4: Update values.yaml**

In `services:` section, add:
```yaml
marketplace:
  enabled: true
  replicaCount: 1
  image: aiopc-marketplace:latest
  pullPolicy: IfNotPresent
  nacosHost: nacos1
  profile: dev
  resources:
    requests:
      cpu: 200m
      memory: 512Mi
    limits:
      cpu: 1000m
      memory: 1Gi
```

- [x] **Step 5: Update docker-compose.yml**

Add service:
```yaml
  aiopc-marketplace:
    build: ../ruoyi-modules/opc-marketplace
    container_name: aiopc-marketplace
    ports:
      - "9317:9317"
    environment:
      NACOS_HOST: nacos1
      SPRING_PROFILES_ACTIVE: dev
    depends_on:
      - nacos1
      - aiopc-mysql
      - aiopc-redis
    networks:
      - aiopc
    restart: unless-stopped
```

- [ ] **Step 6: Verify Helm** （未完成：仓库里查不到运行结果；plan 期望的 dev=18/staging=28/prod=29 已过期，同一份验证报告写的是 dev 31/staging 48/prod 50）

```bash
helm lint springboot3/deploy/helm/opc
helm template test springboot3/deploy/helm/opc | grep '^kind:' | sort | uniq -c
```

Expected: dev=18 / staging=28 / prod=29 (per MEMORY convention).

- [x] **Step 7: Commit**

```bash
git add springboot3/ruoyi-gateway springboot3/deploy/nacos/opc-marketplace-dev.yml springboot3/deploy/helm/opc springboot3/deploy/docker-compose.yml
git commit -m "feat(marketplace): Gateway + Nacos + Helm + Compose (P1)"
```

---

### Task 11: 前端 — Vue 3 页面（4 个）

**Files:**
- Create: `RuoYi-Cloud-Vue3-typescript/src/api/opc/marketplace.ts`
- Create: `RuoYi-Cloud-Vue3-typescript/src/views/opc/marketplace/index.vue`
- Create: `RuoYi-Cloud-Vue3-typescript/src/views/opc/marketplace/detail.vue`
- Create: `RuoYi-Cloud-Vue3-typescript/src/views/opc/marketplace/author-center.vue`
- Create: `RuoYi-Cloud-Vue3-typescript/src/views/opc/marketplace/author-apply.vue`
- Modify: `RuoYi-Cloud-Vue3-typescript/src/router/index.ts` (add 4 routes)

- [x] **Step 1: Create API module** （部分：文件在 `vue3-typescript/src/api/opc/marketplace.ts`，不在 plan 写的 `RuoYi-Cloud-Vue3-typescript/` 下——那是开发者本机路径名）

`marketplace.ts`:
```typescript
import request from '@/utils/request'

export interface PublicationVO {
  id: number
  productType: 'AGENT' | 'MODULE'
  name: string
  code: string
  categoryCode: string
  categoryName?: string
  summary?: string
  iconUrl?: string
  avgRating: number
  ratingCount: number
  installCount: number
  subscriptionCount: number
  status: string
  // ...
}

export interface AuthorVO {
  id: number
  authorName: string
  avatarUrl?: string
  intro?: string
  totalAgents: number
  // ...
}

export const listPublications = (params: any) =>
  request({ url: '/opc/marketplace/publication/list', method: 'get', params })

export const searchPublications = (params: any) =>
  request({ url: '/opc/marketplace/publication/search', method: 'get', params })

export const getPublication = (id: number) =>
  request({ url: `/opc/marketplace/publication/${id}`, method: 'get' })

// 作者读取自己任一状态商品(含 DRAFT)用于编辑表单填充 —— 不走公开 GET (会 404).
// 走 getPublication 加载 DRAFT 会失败,因为 /{id} 在公开详情场景下被 selectPublishedById 过滤.
export const myPublication = (id: number) =>
  request({ url: `/opc/marketplace/publication/my/${id}`, method: 'get' })

export const createPublication = (data: any) =>
  request({ url: '/opc/marketplace/publication', method: 'post', data })

export const editPublication = (id: number, data: any) =>
  request({ url: `/opc/marketplace/publication/${id}`, method: 'put', data })

export const submitAudit = (id: number) =>
  request({ url: `/opc/marketplace/publication/${id}/submit-audit`, method: 'post' })

export const onlinePublication = (id: number) =>
  request({ url: `/opc/marketplace/publication/${id}/online`, method: 'post' })

export const offlinePublication = (id: number) =>
  request({ url: `/opc/marketplace/publication/${id}/offline`, method: 'post' })

export const myPublications = () =>
  request({ url: '/opc/marketplace/publication/my/list', method: 'get' })

export const installPublication = (id: number) =>
  request({ url: `/opc/marketplace/install/${id}`, method: 'post' })

export const uninstallPublication = (id: number) =>
  request({ url: `/opc/marketplace/install/${id}`, method: 'delete' })

export const myInstalls = () =>
  request({ url: '/opc/marketplace/install/my', method: 'get' })

export const installStatus = (id: number) =>
  request({ url: `/opc/marketplace/install/${id}/status`, method: 'get' })

export const applyAuthor = (data: any) =>
  request({ url: '/opc/marketplace/author/apply', method: 'post', data })

export const getMyAuthor = () =>
  request({ url: '/opc/marketplace/author/me', method: 'get' })

export const getAuthor = (id: number) =>
  request({ url: `/opc/marketplace/author/${id}`, method: 'get' })

export const getAuthorStats = (id: number) =>
  request({ url: `/opc/marketplace/author/${id}/stats`, method: 'get' })

export const createReview = (data: any) =>
  request({ url: '/opc/marketplace/review', method: 'post', data })

export const listReviews = (publicationId: number, params: any) =>
  request({ url: `/opc/marketplace/review/list/${publicationId}`, method: 'get', params })

export const updateReview = (id: number, data: any) =>
  request({ url: `/opc/marketplace/review/${id}`, method: 'put', data })

export const deleteReview = (id: number) =>
  request({ url: `/opc/marketplace/review/${id}`, method: 'delete' })

export const listCategories = () =>
  request({ url: '/opc/marketplace/category/tree', method: 'get' })

export const listTags = (category?: string) =>
  request({ url: '/opc/marketplace/tag/list', method: 'get', params: { category } })
```

- [x] **Step 2: Create Marketplace.vue (公开首页)**

`index.vue` — 复制 `views/opc/community/index.vue` (W52 Market.vue) 的 hero + filter + grid 模式，**改用 marketplace API**。结构：
- 顶部 hero + stats
- 类目 el-tabs (从 `listCategories()` 拉取)
- 搜索栏 + 排序 (newest/rating/installs)
- 商品卡片网格 (12/page, 响应式 — 复用 `ResponsiveTable.vue`)
- 点击进入 `detail.vue?id=...`

(完整代码 200-300 行，按 `views/opc/community/index.vue` 模式生成)

- [x] **Step 3: Create AgentDetail.vue**

`detail.vue` — 详情页：
- 顶部：图标 + 名称 + 评分 + 安装按钮
- 中部：tabs (简介 / 版本 / 评论)
- 评论 tab：评价列表 + 我要评价按钮（已评价显示编辑）

(200-300 行，复制 `views/opc/community/detail.vue` 模式)

- [x] **Step 4: Create AuthorCenter.vue + AuthorApply.vue**

`author-center.vue`:
- 顶部：作者信息 (avatar/name/total agents)
- 主体：tabs (我的作品 / 新建商品 / 数据看板)
- 新建商品：表单 → `createPublication()` → 跳转列表
- **编辑 DRAFT**: 我的作品 tab 中点击「编辑」按钮 → 弹窗/内嵌表单加载 DRAFT 数据.
  ⚠️ **不要调用 `getPublication(id)`**,它走公开 `GET /publication/{id}` 端点,
  SQL 层过滤 `status='PUBLISHED'`,DRAFT/PENDING/SUSPENDED 会返回 404 (见 `selectPublishedById`).
  **必须调用 `myPublication(id)`** —— 后端走 `GET /publication/my/{id}`(无 PUBLISHED 过滤,
  且校验 authorId 归属). 数据已在 `myPublications()` 列表内时可省一次请求,但若需拉最新
  (后台被别人改过),就用 `myPublication(id)` 重新拉取.

`author-apply.vue`:
- 申请表单：企业名 / 邮箱 / 简介 → `applyAuthor()`

(每个 150-200 行)

- [x] **Step 5: Add routes** （部分：4 条路由里只有 `detail/:id` 在 `router/index.ts`，另外 3 条走 sys_menu（`V20261022__opc_sys_menus.sql`），是 08305b8「Task 11 漏掉菜单配置」补的；`router/index.spec.ts` 断言 4 条都能 resolve）

In `router/index.ts`, add:
```typescript
{
  path: '/opc/marketplace',
  component: Layout,
  redirect: '/opc/marketplace/index',
  children: [
    { path: 'index', component: () => import('@/views/opc/marketplace/index.vue'), meta: { title: '智能体市场', icon: 'shop' } },
    { path: 'detail/:id', component: () => import('@/views/opc/marketplace/detail.vue'), meta: { title: 'Agent 详情' } },
    { path: 'author-center', component: () => import('@/views/opc/marketplace/author-center.vue'), meta: { title: '作者后台' } },
    { path: 'author-apply', component: () => import('@/views/opc/marketplace/author-apply.vue'), meta: { title: '申请作者' } },
  ]
}
```

- [ ] **Step 6: Verify frontend builds** （未完成：仓库里查不到运行结果；旁证是 gitignore 的 `vue3-typescript/dist/static/js/marketplace-DCPNsj__.js` 存在）

```bash
cd RuoYi-Cloud-Vue3-typescript
npm run build
```

Expected: BUILD SUCCESS, dist generated.

- [x] **Step 7: Commit**

```bash
git add RuoYi-Cloud-Vue3-typescript/src/{api/opc/marketplace.ts,views/opc/marketplace,router/index.ts}
git commit -m "feat(marketplace): 4 Vue pages + API + routes (P1)"
```

---

### Task 12: 健康检查 + E2E + 部署验证

**Files:**
- Modify: `springboot3/deploy/scripts/health-check.sh` (add 21 marketplace endpoints)
- Create: `tmp_e2e/e2e_marketplace.py`
- Create: `docs/verification/week-80/OPC-W80-VERIFICATION-opc-marketplace.md`

- [x] **Step 1: Add 21 marketplace health endpoints to health-check.sh** （部分：plan 说加 21 个端点到 `HEALTH_ENDPOINTS` 数组，但该数组在 health-check.sh 里根本不存在；实现改成了 `check_marketplace()` 挂 step 11，脚本自己的表头写「opc-marketplace 10 项健康检查」，另有 step 19/20 两段 marketplace 检查）

Append to the existing endpoints list:
```bash
# Marketplace (port 9317)
HEALTH_ENDPOINTS+=(
  "Marketplace/Category tree|/opc/marketplace/category/tree"
  "Marketplace/Tag list|/opc/marketplace/tag/list"
  "Marketplace/Publication list|/opc/marketplace/publication/list"
  "Marketplace/Publication search|/opc/marketplace/publication/search"
  "Marketplace/Author list|/opc/marketplace/author/list"
  ...
)  # 21 endpoints total
```

- [ ] **Step 2: Run health check** （未完成：仓库里查不到运行结果；验证报告 §4.2 明确写 health-check.sh 从未执行）

```bash
bash springboot3/deploy/scripts/health-check.sh
```

Expected: 43 PASS (22 旧 + 21 新).

- [x] **Step 3: Write e2e_marketplace.py** （部分：脚本实际有 12 步外加一个 Step 4.5「DRAFT 不被公开端点读到」；plan 的 e2e 从未运行过）

12-step E2E (沿用 Playwright + urllib 登录模式):

```python
"""
e2e_marketplace.py - opc-marketplace P1 E2E (12 steps)

1. Login → get token
2. Apply as author
3. Get my author info
4. Create publication (DRAFT)
5. Submit for audit (auto-publish in P1)
6. Get publication list (PUBLIC)
7. Search publication
8. Install publication (as different user)
9. Get my installs
10. Create review
11. List reviews
12. Get publication details with avg_rating updated
"""
import json, urllib.request, urllib.parse, sys
from playwright.sync_api import sync_playwright

BASE = "http://localhost:8079"
GATEWAY = "http://localhost:8080"

def post_json(path, body, token=None):
    headers = {"Content-Type": "application/json"}
    if token: headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{GATEWAY}{path}", data=json.dumps(body).encode(),
                                  headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        return json.loads(e.read())

def get_json(path, token=None):
    headers = {}
    if token: headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{GATEWAY}{path}", headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        return json.loads(e.read())

# Step 1: login
def login(username, password):
    r = post_json("/login", {"username": username, "password": password})
    assert r["code"] == 200, f"login failed: {r}"
    return r["token"]

# Steps 2-12
def run():
    print("Step 1: Login as admin")
    admin_token = login("admin", "admin123")
    print(f"  token={admin_token[:20]}...")
    
    print("Step 2: Apply as author")
    r = post_json("/opc/marketplace/author/apply",
                  {"authorName": "TestAuthor",
                   "contactEmail": "test@example.com", "intro": "Test"},
                  token=admin_token)
    print(f"  apply author: code={r['code']}")
    # may already be author from previous run; accept both 200 and 409
    # 若 code=400 且 msg 含「未关联企业」,说明该账号在 opc_company_member 无 ACTIVE 成员行 —— 需先建公司

    print("Step 3: Get my author")
    r = get_json("/opc/marketplace/author/me", token=admin_token)
    print(f"  me: code={r['code']} data={r.get('data')}")
    
    # Step 4-5: Create + submit publication
    pub_id = None
    if r.get("data") and r["data"].get("id"):
        print("Step 4: Create publication")
        r = post_json("/opc/marketplace/publication",
                      {"productType": "AGENT", "agentDefinitionId": 1,
                       "code": f"test-agent-{int(time.time())}", "name": "Test Agent",
                       "categoryCode": "office", "summary": "Test"},
                      token=admin_token)
        print(f"  create: code={r['code']}")
        if r.get("data"):
            pub_id = r["data"]["id"]
    
    if pub_id:
        print("Step 5: Submit for audit")
        r = post_json(f"/opc/marketplace/publication/{pub_id}/submit-audit", {}, token=admin_token)
        print(f"  submit: code={r['code']} status={r.get('data', {}).get('status')}")
    
    print("Step 6: List publications (public)")
    # 公开 endpoint 恒定 PUBLISHED (Task3 review Critical#1 已删除 status 入参);
    # ?status= 等任意 status 查询参数会被 Spring 忽略,不会越权.
    r = get_json("/opc/marketplace/publication/list")
    print(f"  list: code={r['code']} total={r.get('data', {}).get('total')}")
    
    print("Step 7: Search publications")
    r = get_json("/opc/marketplace/publication/search?keyword=Test")
    print(f"  search: code={r['code']} total={r.get('data', {}).get('total')}")
    
    if pub_id:
        print("Step 8: Install publication")
        r = post_json(f"/opc/marketplace/install/{pub_id}", {}, token=admin_token)
        print(f"  install: code={r['code']}")
        # may be 200 or 409 (already installed)
    
    print("Step 9: My installs")
    r = get_json("/opc/marketplace/install/my", token=admin_token)
    print(f"  my installs: code={r['code']} count={len(r.get('data', []))}")
    
    if pub_id:
        print("Step 10: Create review")
        r = post_json("/opc/marketplace/review",
                      {"publicationId": pub_id, "rating": 5, "content": "Great!"},
                      token=admin_token)
        print(f"  review: code={r['code']}")
        # may be 200 or 409 (already reviewed)
    
    print("Step 11: List reviews")
    if pub_id:
        r = get_json(f"/opc/marketplace/review/list/{pub_id}")
        print(f"  reviews: code={r['code']} total={r.get('data', {}).get('total')}")
    
    print("Step 12: Get publication details")
    if pub_id:
        r = get_json(f"/opc/marketplace/publication/{pub_id}")
        print(f"  detail: code={r['code']} rating={r.get('data', {}).get('avgRating')}")
    
    print("\nE2E complete!")

import time
if __name__ == "__main__":
    run()
```

- [ ] **Step 4: Run e2e** （未完成：仓库里查不到运行结果；验证报告 §4.1 明确写 e2e 从未运行）

```bash
python tmp_e2e/e2e_marketplace.py
```

Expected: 12 steps print `code=200` (or accepted alternates like 409 for idempotent dup), end with "E2E complete!".

- [x] **Step 5: Write VERIFICATION report**

`docs/verification/week-80/OPC-W80-VERIFICATION-opc-marketplace.md` — follow W72/W73/W75 verification template (沿用 W72 opc-erp 验证报告结构):
- §1 交付物 (9317 + 6 表 + 28 endpoint + 60 单测 + 12 e2e)
- §2 健康 21 项 + 老 22 项 = 43 项 PASS
- §3 e2e 12 步
- §4 Helm lint pass + kinds 数
- §5 Nacos 配置 + 文档

- [x] **Step 6: Commit**

```bash
git add springboot3/deploy/scripts/health-check.sh tmp_e2e/e2e_marketplace.py docs/verification/week-80/OPC-W80-VERIFICATION-opc-marketplace.md
git commit -m "feat(marketplace): health-check + e2e + verification report (P1)"
```

---

### Task 14: Memory 文件 + 最终提交

**Files:**
- Create: `C:\Users\wma19\.claude\projects\D--work-ai-0401-lumen-opc\memory\opc-marketplace-w80.md`
- Modify: `C:\Users\wma19\.claude\projects\D--work-ai-0401-lumen-opc\memory\MEMORY.md` (add line)

- [ ] **Step 1: Write memory file** （未完成：memory 目录 /root/.claude/projects/-www-Lumen-AI-OPC/memory/ 下没有 opc-marketplace-w80.md）

```markdown
---
name: opc-marketplace-w80
description: opc-marketplace W80 P1 MVP 商业闭环 — 端口 9317, 6 表, 28 endpoint, 60+ 单测, 12+ e2e
metadata:
  type: project
---

# W80 opc-marketplace P1 (MVP 商业闭环)

**端口**: 9317
**栈**: Spring Boot 3 + vanilla MyBatis 3.5.19 + Vue 3 TS + Element Plus

## 表 (6 张)
- mp_author: B2B 作者档案 (author_company_id UNIQUE)
- mp_publication: 上架商品 (product_type=AGENT/MODULE, code UNIQUE, status=DRAFT/PENDING/PUBLISHED/SUSPENDED/DEPRECATED)
- mp_install: 安装记录 (UNIQUE(publication_id, tenant_company_id, user_id))
- mp_review: 评价 (UNIQUE(publication_id, user_id))
- mp_category: 类目树 (product_type + code UNIQUE, 6 个种子)
- mp_tag: 标签 (10 个种子)

## 29 个 endpoint
- PublicationController 10: list/search/get/my/{id}/my/list/create/edit/submit-audit/online/offline
- InstallController 5: install/uninstall/my/status/upgrade
- AuthorController 6: apply/me/get/list/publications/stats
  - `GET /me` — companyId 取自 `SecurityUtils.getCompanyId()`,**不接受入参**;无公司 → OpcException(400) fail-closed
  - `GET /{id}` — 公开,走 `toPublicVO` 脱敏(contactEmail/contactPhone/totalRevenue → null)
- ReviewController 4: create/list/update/delete
- CategoryController 3: tree/list/get
- TagController 1: list

## 关键 Fix
1. **MySQL 8 没 IF NOT EXISTS** 列：用 stored procedure (沿用 W3 V20260905 模式)
2. **productType 必填校验**：service 层强校验 AGENT 必带 agentDefinitionId / MODULE 必带 moduleId
3. **Screenshots JSON 序列化**：Request DTO 用 List<String>, XML 用 String 字段, service 层 throw 异常 on JsonProcessingException
4. **Avg rating 重算**：每次 create/update/delete review 都重新计算并 UPDATE mp_publication
5. **B2B 作者入驻**：申请时 author_company_id = 当前用户的 companyId (来自 SecurityUtils.getCompanyId())

## 教训
- **`@WebMvcTest` 失败模式**：必须显式 `@MockBean` 所有 Controller 依赖的 service，否则 Bean 找不到
- **PublicationVO 与 MpPublication 区分**：VO 是响应（含 authorName 等 join 字段）, domain 是数据库行

## 下一步
- W81 P2: mp_agent_version / mp_subscription / mp_quota_usage + SubscribeService / UsageService / VersionService + 调 opc-billing + Feign 回调 + 月度结算
- W82 P3: mp_recommendation / mp_audit_log + Admin 审核/封禁 + 推荐位 + 试用会话
- W83 P4: mp_payout / mp_invite_reward / mp_feature_flag / mp_suspension_log + 提现 + 国际化

## Commit
- feat(marketplace): P1 MVP 商业闭环
```

- [ ] **Step 2: Update MEMORY.md** （未完成：/root/.claude/projects/-www-Lumen-AI-OPC/memory/MEMORY.md 里没有 marketplace 相关的行）

Add line:
```markdown
- [opc-marketplace W80 P1](opc-marketplace-w80.md) — 智能体市场 MVP, 端口 9317, 6 表/28 endpoint/60+ 单测/12+ e2e
```

- [ ] **Step 3: Final commit + push** （未完成：git log --all --grep=marketplace 里没有 "docs(memory): opc-marketplace W80 P1 记忆" 这个提交）

```bash
cd D:/work-ai/0401-lumen-opc
git add C:/Users/wma19/.claude/projects/D--work-ai-0401-lumen-opc/memory/
git commit -m "docs(memory): opc-marketplace W80 P1 记忆"
git push origin main
```

Expected: All commits pushed, remote main updated.

---

## 验收标准 (P1 末尾)

| 指标 | 目标 | 验证方式 |
|---|---|---|
| 编译 | BUILD SUCCESS | `cd springboot3/ruoyi-modules && mvn -pl opc-marketplace -am clean package -DskipTests` |
| 单测 | 63 全 PASS | `cd springboot3/ruoyi-modules && mvn -pl opc-marketplace test` |
| 健康检查 | 43 项 PASS (22 旧 + 21 新) | `bash springboot3/deploy/scripts/health-check.sh` |
| e2e | 12 步 PASS | `python tmp_e2e/e2e_marketplace.py` |
| Helm | dev=18 / staging=28 / prod=29 | `helm template test springboot3/deploy/helm/opc | grep '^kind:' | sort | uniq -c` |
| Nacos | 配置导入 OK | `python springboot3/deploy/nacos/import-dev.sh opc-marketplace-dev.yml opc-dev` |
| 前端构建 | dist 生成 | `npm run build` |

---

## 不在 P1 范围（推到后续 Phase）

- 付费订阅（P2）
- 版本管理（P2）
- 用量计费回调（P2）
- 月结 / 提现（P4）
- 推荐位（P3）
- 审核流程（P3，Phase 1 自动审核通过）
- 国际化（P4）
- 沙箱 / 灰度（P3-P4）
- 数据迁移（agent-market/agent-coder 从 community_module 迁到 mp_publication）