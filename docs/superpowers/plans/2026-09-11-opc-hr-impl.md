# opc-hr Implementation Plan

## 审计说明（2026-10-29）

- **审计对象**：本 plan（`docs/superpowers/plans/2026-09-11-opc-hr-impl.md`）
- **plan 目标**：交付 opc-hr 微服务（端口 9322），含 6 表 / 25 REST / 4 Feign / 3 LLM prompt / 5 Vue 页 / Dashboard，3 周交付
- **审计方式**：只读仓库与 git 历史，逐条核实 69 个 checkbox。判据：文件在且内容对得上 / 代码里 grep 得到 / 测试文件与测试方法在 / 提交在 plan 时间窗（2026-09-11 ~ 09-14）内
- **结果**：**勾了 54 / 没勾 15 / 共 69**
- **没勾的 15 个主要卡在哪**：13 个是「跑一次看输出」的验证类步骤（mvn compile ×3 / mvn test ×5 / npm build / helm lint / docker compose config / 重 build 前端 + 启容器 / 跑 health-check / E2E），仓库里查不到运行结果，按规矩一律不勾；1 个是「跑单测 + Commit」混合 step（Commit 部分已由 8ccf15f 完成，跑测试部分查不到结果）；1 个是文件真的没做（`OpcHrFeignConfig.java`）
- **另外要提醒的**：有 9 个 checkbox 勾上了但**只做了一半**，行尾都标了「（部分：…）」——最典型的是 Offer.respond() 不推进 application 状态也不调 Notification/UserCenter 网关、Candidate.search() 是空列表占位、publish() 不调 LLM 生成 JD、面试 PASS/FAIL 不推进状态机、dashboard 没用 ECharts

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver opc-hr 微服务（端口 9322），含 6 表 / 25 REST / 4 Feign / 3 LLM prompt / 5 Vue 页 / Dashboard，3 周交付。

**Architecture:** 完全套用 [[opc-crm-w50]] 模式（Spring Boot 3 + Nacos + thin jar + company_id 强约束）。HR 差异点是 3 个 LLM prompt（JD 生成 / 简历解析 / 评分），统一走 opc-ai-core 的 HttpLlmClient + PromptGuard。候选人向量复用 Qdrant 已有 collection。

**Tech Stack:** Spring Boot 3.2 + Spring Cloud 2023 + MyBatis 3.5 + Nacos + Feign + Qdrant + DeepSeek LLM + Vue 3 + TypeScript + Element Plus + Vite + JUnit5 + Mockito + Vitest.

---

## File Structure（创建的文件）

```
springboot3/
├── ruoyi-modules/opc-hr/
│   ├── pom.xml                                          # Task 1
│   ├── Dockerfile                                       # Task 11
│   ├── src/main/resources/
│   │   ├── application.yml                              # Task 1
│   │   ├── bootstrap.yml                                # Task 1
│   │   ├── logback.xml                                  # Task 1
│   │   └── mapper/
│   │       ├── OpcHrJobMapper.xml                       # Task 3
│   │       ├── OpcHrCandidateMapper.xml                 # Task 4
│   │       ├── OpcHrApplicationMapper.xml               # Task 5
│   │       ├── OpcHrInterviewMapper.xml                 # Task 6
│   │       ├── OpcHrOfferMapper.xml                     # Task 6
│   │       └── OpcHrMatchScoreMapper.xml                # Task 6
│   └── src/main/java/com/ruoyi/opc/hr/
│       ├── OpcHrApplication.java                         # Task 1
│       ├── config/
│       │   └── OpcHrFeignConfig.java                    # Task 8
│       ├── controller/
│       │   ├── OpcHrJobController.java                  # Task 3
│       │   ├── OpcHrCandidateController.java            # Task 4
│       │   ├── OpcHrApplicationController.java          # Task 5
│       │   ├── OpcHrInterviewController.java            # Task 6
│       │   ├── OpcHrOfferController.java                # Task 6
│       │   └── OpcHrDashboardController.java            # Task 7
│       ├── domain/                                       # Task 2
│       │   ├── OpcHrJob.java
│       │   ├── OpcHrCandidate.java
│       │   ├── OpcHrApplication.java
│       │   ├── OpcHrInterview.java
│       │   ├── OpcHrOffer.java
│       │   └── OpcHrMatchScore.java
│       ├── dto/                                          # Task 2
│       │   ├── OpcHrJobDto.java
│       │   ├── OpcHrCandidateDto.java
│       │   ├── OpcHrApplicationDto.java
│       │   ├── OpcHrInterviewDto.java
│       │   ├── OpcHrOfferDto.java
│       │   ├── OpcHrDashboardDto.java
│       │   └── HrLlmRequest.java
│       ├── enums/
│       │   ├── HrJobStatus.java                         # Task 2
│       │   ├── HrJobCategory.java                       # Task 2
│       │   ├── HrApplicationStatus.java                 # Task 2
│       │   ├── HrInterviewType.java                     # Task 2
│       │   ├── HrInterviewResult.java                   # Task 2
│       │   └── HrOfferStatus.java                       # Task 2
│       ├── gateway/
│       │   ├── NotificationGateway.java                 # Task 8
│       │   ├── UserCenterGateway.java                   # Task 8
│       │   ├── AiCoreGateway.java                       # Task 8
│       │   └── CrmGateway.java                          # Task 8
│       ├── mapper/                                       # Task 2
│       │   ├── OpcHrJobMapper.java
│       │   ├── OpcHrCandidateMapper.java
│       │   ├── OpcHrApplicationMapper.java
│       │   ├── OpcHrInterviewMapper.java
│       │   ├── OpcHrOfferMapper.java
│       │   └── OpcHrMatchScoreMapper.java
│       └── service/
│           ├── OpcHrJobService.java                      # Task 3
│           ├── OpcHrCandidateService.java                # Task 4
│           ├── OpcHrApplicationService.java              # Task 5
│           ├── OpcHrInterviewService.java                # Task 6
│           ├── OpcHrOfferService.java                    # Task 6
│           ├── OpcHrDashboardService.java                # Task 7
│           └── impl/ (同上 Impl)
├── ruoyi-modules/pom.xml                                # Task 1 (加 opc-hr module)
├── sql/V20260911__opc_hr_schema.sql                     # Task 1
├── sql/seed/opc_hr_demo_seed.sql                        # Task 1
├── deploy/
│   ├── docker-compose.yml                               # Task 11
│   ├── nacos/opc-hr-dev.yml                             # Task 11
│   ├── nacos/opc-hr-prod.yml                            # Task 11
│   ├── nacos/whitelist-opc-hr.yml                       # Task 11
│   ├── mysql-initdb.d/10-opc-hr-schema.sql              # Task 11
│   ├── mysql-initdb.d/96-opc-hr-seed.sql                # Task 11
│   └── helm/opc/values.yaml                             # Task 11
└── scripts/health-check.sh                              # Task 12

vue3-typescript/src/
├── api/opc/hr.ts                                        # Task 9
├── views/opc/hr/
│   ├── job/index.vue                                    # Task 10
│   ├── job/detail.vue                                   # Task 10
│   ├── candidate/index.vue                              # Task 10
│   ├── candidate/detail.vue                             # Task 10
│   └── dashboard.vue                                    # Task 10
└── router/index.ts                                       # Task 10 (注册 HR 路由)
```

---

## Task 1: 模块骨架 + Application 启动类 + SQL 文件

**Files:**
- Create: `springboot3/ruoyi-modules/opc-hr/pom.xml`
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/resources/{application.yml,bootstrap.yml,logback.xml}`
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/OpcHrApplication.java`
- Create: `springboot3/sql/V20260911__opc_hr_schema.sql`
- Modify: `springboot3/ruoyi-modules/pom.xml`（加入 opc-hr module）

- [x] **Step 1: 创建 pom.xml** （pom.xml 在，artifactId=opc-hr、mainClass=com.ruoyi.opc.hr.OpcHrApplication、thin jar <phase>none</phase> 都在；去掉了 plan 里的 opc-notification 依赖，改为纯 Feign 走 Nacos 服务发现）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <parent>
        <groupId>com.ruoyi</groupId>
        <artifactId>ruoyi-modules</artifactId>
        <version>3.6.8</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>opc-hr</artifactId>
    <description>OPC HR / 招聘管理</description>

    <dependencies>
        <dependency>
            <groupId>com.ruoyi</groupId>
            <artifactId>opc-common</artifactId>
        </dependency>
        <dependency>
            <groupId>com.ruoyi</groupId>
            <artifactId>ruoyi-common-security</artifactId>
        </dependency>
        <dependency>
            <groupId>com.ruoyi</groupId>
            <artifactId>ruoyi-api-system</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
        <dependency>
            <groupId>com.ruoyi</groupId>
            <artifactId>opc-notification</artifactId>
            <version>3.6.8</version>
            <scope>compile</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>${project.artifactId}</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.ruoyi.opc.hr.OpcHrApplication</mainClass>
                </configuration>
                <executions>
                    <execution>
                        <phase>none</phase>  <!-- thin jar 模板, 参考 [[aiopc-all-opc-stack]] -->
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [x] **Step 2: 创建 application.yml** （application.yml 在，server.port=9322、spring.application.name=opc-hr、mybatis.mapper-locations 一致；datasource 改成 dynamic-datasource + ry-vue-opc + 环境变量占位，与 plan 模板不同）

```yaml
server:
  port: 9322

spring:
  application:
    name: opc-hr
  profiles:
    active: dev
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://aiopc-mysql:3306/ry-vue?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: Opc@2026!

mybatis:
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: com.ruoyi.opc.hr.domain
  configuration:
    map-underscore-to-camel-case: true

logging:
  level:
    com.ruoyi.opc.hr: debug
```

- [x] **Step 3: 创建 bootstrap.yml** （bootstrap.yml 在，nacos discovery/config 的 namespace=opc-dev 一致；shared-configs 改成 Spring Boot 3 的 config.import 写法）

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: nacos1:8848
        namespace: opc-dev
      config:
        server-addr: nacos1:8848
        namespace: opc-dev
        file-extension: yml
        shared-configs:
          - application-common.yml
          - application-rbac.yml
          - application-redis.yml
          - application-datasource.yml
    sentinel:
      transport:
        dashboard: localhost:8718

# fail-fast on missing config (prod)
nacos:
  fail-fast: true
```

- [x] **Step 4: 创建 OpcHrApplication.java** （OpcHrApplication.java 在，@SpringBootApplication/@EnableDiscoveryClient/@EnableFeignClients(basePackages={com.ruoyi.system.api, com.ruoyi.opc})/@ComponentScan("com.ruoyi.opc") 都在，另加了 @EnableCustomConfig 和 com.ruoyi.system 的扫描）

```java
package com.ruoyi.opc.hr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.ruoyi.system.api", "com.ruoyi.opc"})
@ComponentScan("com.ruoyi.opc")
public class OpcHrApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpcHrApplication.class, args);
    }
}
```

- [x] **Step 5: 创建 logback.xml** （logback.xml 在，内容与 plan 模板逐字一致）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/base.xml"/>
    <logger name="com.ruoyi.opc.hr" level="DEBUG"/>
</configuration>
```

- [x] **Step 6: 创建 SQL V20260911__opc_hr_schema.sql** （文件在 springboot3/sql/migrations/V20260911__opc_hr_schema.sql，不在 plan 写的 springboot3/sql/ 下；6 张表 opc_hr_job/candidate/application/interview/offer/match_score 与列名都对得上）

```sql
-- OPC HR 招聘管理 schema (W71)
USE ry-vue;

CREATE TABLE opc_hr_job (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  created_by      BIGINT NOT NULL,
  title           VARCHAR(128) NOT NULL,
  category        VARCHAR(32) NOT NULL,
  description     TEXT NOT NULL,
  full_jd         TEXT,
  skills_json     JSON,
  salary_min      DECIMAL(12,2),
  salary_max      DECIMAL(12,2),
  location        VARCHAR(64),
  status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  publish_at      DATETIME,
  close_at        DATETIME,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_status (company_id, status),
  UNIQUE KEY uk_company_title (company_id, title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE opc_hr_candidate (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  name            VARCHAR(64) NOT NULL,
  email           VARCHAR(128),
  phone           VARCHAR(32),
  resume_url      VARCHAR(512) NOT NULL,
  resume_md       MEDIUMTEXT,
  parsed_json     JSON,
  embedding       BLOB,
  source          VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  tags_json       JSON,
  created_by      BIGINT NOT NULL,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_name (company_id, name),
  UNIQUE KEY uk_company_email (company_id, email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE opc_hr_application (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  job_id          BIGINT NOT NULL,
  candidate_id    BIGINT NOT NULL,
  channel         VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  score           INT NOT NULL DEFAULT 0,
  score_reason    TEXT,
  status          VARCHAR(16) NOT NULL DEFAULT 'NEW',
  current_stage   VARCHAR(32),
  applied_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_job_status (company_id, job_id, status),
  UNIQUE KEY uk_job_candidate (job_id, candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE opc_hr_interview (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  application_id  BIGINT NOT NULL,
  round           INT NOT NULL,
  type            VARCHAR(16) NOT NULL,
  interviewer_id  BIGINT NOT NULL,
  scheduled_at    DATETIME NOT NULL,
  duration_min    INT NOT NULL DEFAULT 60,
  feedback        TEXT,
  result          VARCHAR(16),
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_company_app (company_id, application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE opc_hr_offer (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  application_id  BIGINT NOT NULL,
  salary          DECIMAL(12,2) NOT NULL,
  start_date      DATE NOT NULL,
  expire_at       DATETIME NOT NULL,
  status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  sent_at         DATETIME,
  responded_at    DATETIME,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_application (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE opc_hr_match_score (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  job_id          BIGINT NOT NULL,
  candidate_id    BIGINT NOT NULL,
  score           INT NOT NULL,
  reason          TEXT,
  computed_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_job_candidate (job_id, candidate_id),
  KEY idx_company_job (company_id, job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [x] **Step 7: 修改 ruoyi-modules/pom.xml 加入 opc-hr** （springboot3/ruoyi-modules/pom.xml:26 <module>opc-hr</module>）

在 `<modules>` 列表加：
```xml
<module>opc-hr</module>
```

- [ ] **Step 8: 验证编译** （未完成：仓库里查不到运行结果）

Run: `cd springboot3 && mvn -pl ruoyi-modules/opc-hr -am compile -DskipTests`
Expected: BUILD SUCCESS（首次会下载依赖,可能 2-3 分钟）

- [x] **Step 9: Commit** （commit aafbdd6 "feat(hr): Task 1 - 模块骨架 + Application + 6 表 SQL"）

```bash
git add springboot3/ruoyi-modules/opc-hr springboot3/ruoyi-modules/pom.xml springboot3/sql/V20260911__opc_hr_schema.sql
git commit -m "feat(hr): Task 1 - 模块骨架 + Application + 6 表 SQL"
```

---

## Task 2: 6 个 Domain + 6 个 Mapper 接口 + 6 个枚举 + 6 个 DTO

**Files:**
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/domain/{OpcHrJob,OpcHrCandidate,OpcHrApplication,OpcHrInterview,OpcHrOffer,OpcHrMatchScore}.java`
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/mapper/{OpcHrJobMapper,OpcHrCandidateMapper,OpcHrApplicationMapper,OpcHrInterviewMapper,OpcHrOfferMapper,OpcHrMatchScoreMapper}.java`
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/enums/{HrJobStatus,HrJobCategory,HrApplicationStatus,HrInterviewType,HrInterviewResult,HrOfferStatus}.java`
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/dto/{OpcHrJobDto,OpcHrCandidateDto,OpcHrApplicationDto,OpcHrInterviewDto,OpcHrOfferDto,OpcHrDashboardDto,HrLlmRequest}.java`

- [x] **Step 1: 创建枚举（6 个）** （6 个枚举都在，HrJobStatus 的 DRAFT/OPEN/PAUSED/CLOSED + of() 与 plan 逐字一致）

`HrJobStatus.java`:
```java
package com.ruoyi.opc.hr.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HrJobStatus {
    DRAFT("DRAFT", "草稿"),
    OPEN("OPEN", "招聘中"),
    PAUSED("PAUSED", "暂停"),
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String desc;

    public static HrJobStatus of(String code) {
        for (HrJobStatus s : values()) if (s.code.equals(code)) return s;
        throw new IllegalArgumentException("Unknown HrJobStatus: " + code);
    }
}
```

`HrJobCategory.java`:
```java
package com.ruoyi.opc.hr.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HrJobCategory {
    TECH("TECH", "技术"),
    SALES("SALES", "销售"),
    OPERATION("OPERATION", "运营"),
    FINANCE("FINANCE", "财务"),
    MARKETING("MARKETING", "市场");

    private final String code;
    private final String desc;
}
```

`HrApplicationStatus.java`:
```java
package com.ruoyi.opc.hr.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HrApplicationStatus {
    NEW("NEW", "新投递"),
    SCREENING("SCREENING", "筛选中"),
    INTERVIEW("INTERVIEW", "面试中"),
    OFFER("OFFER", "已发 Offer"),
    HIRED("HIRED", "已入职"),
    REJECTED("REJECTED", "已拒绝");

    private final String code;
    private final String desc;
}
```

`HrInterviewType.java`:
```java
package com.ruoyi.opc.hr.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HrInterviewType {
    PHONE("PHONE", "电话"),
    VIDEO("VIDEO", "视频"),
    ONSITE("ONSITE", "现场");

    private final String code;
    private final String desc;
}
```

`HrInterviewResult.java`:
```java
package com.ruoyi.opc.hr.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HrInterviewResult {
    PENDING("PENDING", "待定"),
    PASS("PASS", "通过"),
    FAIL("FAIL", "未通过");

    private final String code;
    private final String desc;
}
```

`HrOfferStatus.java`:
```java
package com.ruoyi.opc.hr.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HrOfferStatus {
    PENDING("PENDING", "待响应"),
    ACCEPTED("ACCEPTED", "已接受"),
    REJECTED("REJECTED", "已拒绝"),
    EXPIRED("EXPIRED", "已过期");

    private final String code;
    private final String desc;
}
```

- [x] **Step 2: 创建 Domain（6 个）** （6 个 Domain 都在，OpcHrJob 的字段、@JsonProperty snake_case、@Builder/@Data 与 plan 模板一致）

每个 Domain 都按此模板（以 OpcHrJob 为例）：
```java
package com.ruoyi.opc.hr.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruoyi.common.core.annotation.Excel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcHrJob {
    private Long id;
    @JsonProperty("company_id")
    private Long companyId;
    @JsonProperty("created_by")
    private Long createdBy;

    private String title;
    private String category;
    private String description;
    @JsonProperty("full_jd")
    private String fullJd;
    @JsonProperty("skills_json")
    private String skillsJson;

    @JsonProperty("salary_min")
    private BigDecimal salaryMin;
    @JsonProperty("salary_max")
    private BigDecimal salaryMax;

    private String location;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("publish_at")
    private Date publishAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("close_at")
    private Date closeAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("create_time")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("update_time")
    private Date updateTime;
}
```

`OpcHrCandidate.java`: 字段按 Task 6 SQL 映射,加 `@Builder`、snake_case JSON。
`OpcHrApplication.java`: 同上,`channel/score/status/applied_at`。
`OpcHrInterview.java`: 同上,`round/type/interviewer_id/scheduled_at/duration_min/feedback/result`。
`OpcHrOffer.java`: 同上,`salary/start_date/expire_at/status/sent_at/responded_at`。
`OpcHrMatchScore.java`: 同上,`score/reason/computed_at`。

每个字段都按 **W50 教训 1**：NOT NULL DEFAULT 0/'' 列在 INSERT 时显式赋值（service 层兜底）。

- [x] **Step 3: 创建 Mapper 接口（6 个）** （6 个 Mapper 接口都在，OpcHrJobMapper 的 insert/updateById/deleteById/selectById/selectList/countList 签名与 plan 一致）

`OpcHrJobMapper.java`:
```java
package com.ruoyi.opc.hr.mapper;

import com.ruoyi.opc.hr.domain.OpcHrJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OpcHrJobMapper {
    int insert(OpcHrJob job);
    int updateById(OpcHrJob job);
    int deleteById(@Param("id") Long id, @Param("companyId") Long companyId);
    OpcHrJob selectById(@Param("id") Long id, @Param("companyId") Long companyId);
    List<OpcHrJob> selectList(@Param("companyId") Long companyId,
                              @Param("status") String status,
                              @Param("offset") int offset,
                              @Param("limit") int limit);
    int countList(@Param("companyId") Long companyId, @Param("status") String status);
}
```

其他 5 个 Mapper 按相同模式（参考实际 mapper 接口,不能照搬 plan 模板 — W50 教训 4）。

- [x] **Step 4: 创建 DTO（7 个）** （plan 列的 7 个 DTO 全在；仓库里实际有 11 个，多出 HrScoreResult/HrSearchRequest/HrSearchResult/OpcHrJobLlmRequest）

DTO 用于 Controller 入参/出参,字段与 Domain 对应,加 Lombok `@Data`。`HrLlmRequest.java`:
```java
package com.ruoyi.opc.hr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class HrLlmRequest {
    private String promptName;
    @JsonProperty("job_id")
    private Long jobId;
    @JsonProperty("candidate_id")
    private Long candidateId;
    private String input;
}
```

- [ ] **Step 5: 验证编译** （未完成：仓库里查不到运行结果）

Run: `cd springboot3 && mvn -pl ruoyi-modules/opc-hr -am compile -DskipTests`
Expected: BUILD SUCCESS

- [x] **Step 6: Commit** （commit 86308c2 "feat(hr): Task 2 - 6 Domain + 6 Mapper + 6 Enum + 9 DTO"）

```bash
git add springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/{domain,mapper,enums,dto}
git commit -m "feat(hr): Task 2 - 6 Domain + 6 Mapper + 6 Enum + 7 DTO"
```

---

## Task 3: OpcHrJobService + OpcHrJobMapper.xml + Controller + 单测（8 endpoints）

**Files:**
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/resources/mapper/OpcHrJobMapper.xml`
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/service/OpcHrJobService.java`
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/service/impl/OpcHrJobServiceImpl.java`
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/controller/OpcHrJobController.java`
- Create: `springboot3/ruoyi-modules/opc-hr/src/test/java/com/ruoyi/opc/hr/service/impl/OpcHrJobServiceImplTest.java`

- [x] **Step 1: 创建 OpcHrJobMapper.xml** （OpcHrJobMapper.xml 在 springboot3/ruoyi-modules/opc-hr/src/main/resources/mapper/hr/ 下，不在 plan 写的 mapper/ 下；6 个 statement 与 opc_hr_job 表都对得上）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.ruoyi.opc.hr.mapper.OpcHrJobMapper">
  <resultMap id="jobMap" type="com.ruoyi.opc.hr.domain.OpcHrJob">
    <id property="id" column="id"/>
    <result property="companyId" column="company_id"/>
    <result property="createdBy" column="created_by"/>
    <result property="title" column="title"/>
    <result property="category" column="category"/>
    <result property="description" column="description"/>
    <result property="fullJd" column="full_jd"/>
    <result property="skillsJson" column="skills_json"/>
    <result property="salaryMin" column="salary_min"/>
    <result property="salaryMax" column="salary_max"/>
    <result property="location" column="location"/>
    <result property="status" column="status"/>
    <result property="publishAt" column="publish_at"/>
    <result property="closeAt" column="close_at"/>
    <result property="createTime" column="create_time"/>
    <result property="updateTime" column="update_time"/>
  </resultMap>

  <sql id="cols">id,company_id,created_by,title,category,description,full_jd,skills_json,salary_min,salary_max,location,status,publish_at,close_at,create_time,update_time</sql>

  <insert id="insert" parameterType="com.ruoyi.opc.hr.domain.OpcHrJob">
    INSERT INTO opc_hr_job (<include refid="cols"/>)
    VALUES (#{id},#{companyId},#{createdBy},#{title},#{category},#{description},#{fullJd},#{skillsJson},#{salaryMin},#{salaryMax},#{location},#{status},#{publishAt},#{closeAt},#{createTime},#{updateTime})
  </insert>

  <update id="updateById" parameterType="com.ruoyi.opc.hr.domain.OpcHrJob">
    UPDATE opc_hr_job SET title=#{title},category=#{category},description=#{description},
      full_jd=#{fullJd},skills_json=#{skillsJson},salary_min=#{salaryMin},salary_max=#{salaryMax},
      location=#{location},status=#{status},publish_at=#{publishAt},close_at=#{closeAt},
      update_time=NOW()
    WHERE id=#{id} AND company_id=#{companyId}
  </update>

  <delete id="deleteById">
    DELETE FROM opc_hr_job WHERE id=#{id} AND company_id=#{companyId} AND status='DRAFT'
  </delete>

  <select id="selectById" resultMap="jobMap">
    SELECT <include refid="cols"/> FROM opc_hr_job
    WHERE id=#{id} AND company_id=#{companyId}
  </select>

  <select id="selectList" resultMap="jobMap">
    SELECT <include refid="cols"/> FROM opc_hr_job
    WHERE company_id=#{companyId}
    <if test="status != null and status != ''">AND status=#{status}</if>
    ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}
  </select>

  <select id="countList" resultType="int">
    SELECT COUNT(*) FROM opc_hr_job
    WHERE company_id=#{companyId}
    <if test="status != null and status != ''">AND status=#{status}</if>
  </select>
</mapper>
```

- [x] **Step 2: 创建 OpcHrJobService 接口 + Impl** （部分：接口名是 IOpcHrJobService 不是 plan 写的 OpcHrJobService；publish() 只改状态和 publish_at，没有按 plan 调 LLM 生成 fullJd + skillsJson）

```java
package com.ruoyi.opc.hr.service;

import com.ruoyi.opc.hr.domain.OpcHrJob;
import java.util.List;

public interface OpcHrJobService {
    Long create(OpcHrJob job);
    void update(OpcHrJob job);
    void delete(Long id);
    OpcHrJob detail(Long id);
    List<OpcHrJob> list(Long companyId, String status, int page, int size);
    int count(Long companyId, String status);
    void publish(Long id);
    void close(Long id);
    String generateLlmJd(String title, String category, String description);
}
```

Impl 关键逻辑：
- `create()`：用 `SnowflakeIdWorker.nextId()`、`companyId = SecurityUtils.getUserId().companyId`、`createdBy = SecurityUtils.getUserId().userId`、`status = DRAFT`
- `update()`：必须 detail 先验证 companyId 归属
- `delete()`：仅 DRAFT 状态可删
- `publish()`：status DRAFT→OPEN,publish_at=NOW(),**调用 AiCoreGateway.llmGenerate(jd_generate_prompt)** 写入 fullJd + skillsJson
- `close()`：status → CLOSED
- `generateLlmJd()`：调 AiCoreGateway,返回 LLM 文本（不落库）

- [x] **Step 3: 创建 Controller（8 endpoints）** （OpcHrJobController 在，plan 列的 8 个端点全有，实际是 9 个——多一个 POST /opc/hr/job/{id}/pause）

```java
package com.ruoyi.opc.hr.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.hr.domain.OpcHrJob;
import com.ruoyi.opc.hr.service.OpcHrJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/opc/hr/job")
@RequiredArgsConstructor
public class OpcHrJobController {
    private final OpcHrJobService jobService;

    @PostMapping
    public R<Long> create(@RequestBody OpcHrJob job) { return R.ok(jobService.create(job)); }

    @GetMapping("/list")
    public R<PageResult<OpcHrJob>> list(@RequestParam(required=false) String status,
                                        @RequestParam(defaultValue="1") int pageNum,
                                        @RequestParam(defaultValue="20") int pageSize) {
        Long companyId = SecurityUtils.getUserId()... // 略
        return R.ok(new PageResult<>(jobService.list(companyId, status, (pageNum-1)*pageSize, pageSize),
                                     jobService.count(companyId, status)));
    }

    @GetMapping("/{id}")
    public R<OpcHrJob> detail(@PathVariable Long id) { return R.ok(jobService.detail(id)); }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody OpcHrJob job) {
        job.setId(id); jobService.update(job); return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) { jobService.delete(id); return R.ok(); }

    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) { jobService.publish(id); return R.ok(); }

    @PostMapping("/{id}/close")
    public R<Void> close(@PathVariable Long id) { jobService.close(id); return R.ok(); }

    @PostMapping("/generate-llm")
    public R<String> generateLlm(@RequestBody HrLlmRequest req) {
        return R.ok(jobService.generateLlmJd(req.getTitle(), req.getCategory(), req.getInput()));
    }
}
```

- [x] **Step 4: 写单测 (15 测试)** （OpcHrJobServiceImplTest.java 在，实际 20 个 @Test，多于 plan 要求的 15）

`OpcHrJobServiceImplTest.java`:
```java
package com.ruoyi.opc.hr.service.impl;

import com.ruoyi.opc.hr.domain.OpcHrJob;
import com.ruoyi.opc.hr.mapper.OpcHrJobMapper;
import com.ruoyi.opc.hr.gateway.AiCoreGateway;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
class OpcHrJobServiceImplTest {
    @InjectMocks private OpcHrJobServiceImpl service;
    @Mock private OpcHrJobMapper mapper;
    @Mock private AiCoreGateway aiCore;

    @BeforeEach void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test void create_assignsIdAndDefaults() {
        OpcHrJob j = OpcHrJob.builder().title("Java").category("TECH").description("x").build();
        when(mapper.insert(any())).thenReturn(1);
        Long id = service.create(j);
        assertNotNull(id);
        assertEquals("DRAFT", j.getStatus());
    }

    @Test void update_requiresMatchingCompany() {
        OpcHrJob existing = OpcHrJob.builder().id(1L).companyId(100L).build();
        when(mapper.selectById(1L, 200L)).thenReturn(null);
        assertThrows(OpcException.class, () -> service.update(OpcHrJob.builder().id(1L).companyId(200L).build()));
    }

    @Test void delete_onlyDraft() {
        when(mapper.deleteById(1L, 100L)).thenReturn(1);
        service.delete(1L);
        verify(mapper).deleteById(1L, 100L);
    }

    @Test void publish_setsStatusAndCallsLlm() {
        OpcHrJob j = OpcHrJob.builder().id(1L).companyId(100L).status("DRAFT").build();
        when(mapper.selectById(1L, 100L)).thenReturn(j);
        when(aiCore.llmGenerate(anyString(), any())).thenReturn("{\"full_jd\":\"...\",\"skills\":[\"Java\"]}");
        service.publish(1L);
        assertEquals("OPEN", j.getStatus());
        assertNotNull(j.getPublishAt());
        verify(mapper).updateById(j);
    }

    // ... 共 15 个测试,覆盖 create/update/delete/list/count/publish/close/generateLlm/边界
}
```

- [ ] **Step 5: 跑单测** （未完成：仓库里查不到运行结果）

Run: `cd springboot3 && mvn -pl ruoyi-modules/opc-hr test -Dtest=OpcHrJobServiceImplTest`
Expected: 15/15 PASS

- [x] **Step 6: Commit** （commit 61ce278 "feat(hr): Task 3 - Job Service + 8 endpoints + Mapper XML + 15 单测"，另有 33d186c 补到 18 个）

```bash
git add springboot3/ruoyi-modules/opc-hr/src/main/{java/com/ruoyi/opc/hr/{controller,service},resources/mapper/OpcHrJobMapper.xml} springboot3/ruoyi-modules/opc-hr/src/test
git commit -m "feat(hr): Task 3 - Job Service + 8 endpoints + 15 单测"
```

---

## Task 4: OpcHrCandidateService + Controller + 单测（7 endpoints）

**Files:**
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/resources/mapper/OpcHrCandidateMapper.xml`
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/service/OpcHrCandidateService.java`
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/service/impl/OpcHrCandidateServiceImpl.java`
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/controller/OpcHrCandidateController.java`
- Create: `springboot3/ruoyi-modules/opc-hr/src/test/java/com/ruoyi/opc/hr/service/impl/OpcHrCandidateServiceImplTest.java`

- [x] **Step 1: 创建 Mapper XML** （OpcHrCandidateMapper.xml 在，opc_hr_candidate 的 insert/update/delete/select 都在）

按 OpcHrJobMapper.xml 模板,改 table 名 opc_hr_candidate + column 名。

- [x] **Step 2: 创建 Service 接口 + Impl** （部分：parse() 已实接 opc-ai-core 的 hr_resume_parse 场景；但 create() 不调 parseResume 也不写 embedding（parsedJson 直接取 DTO），search() 是占位实现返回空列表，没有 Qdrant 向量召回也没有 LLM 重排）

关键逻辑：
- `create(candidate)`：调 AiCoreGateway.parseResume(resumeUrl) → parsedJson + embedding 落库
- `parseResume(id)`：调用 AiCoreGateway.llmGenerate("hr_resume_parse", resumeMd) → 更新 parsedJson
- `search(companyId, query, topK)`：embedding 相似度检索 + LLM 重排序

- [x] **Step 3: 创建 Controller（7 endpoints）** （OpcHrCandidateController 在，7 个端点逐个对得上）

```java
@RestController
@RequestMapping("/opc/hr/candidate")
@RequiredArgsConstructor
public class OpcHrCandidateController {
    private final OpcHrCandidateService service;

    @PostMapping public R<Long> create(@RequestBody OpcHrCandidate c) { return R.ok(service.create(c)); }
    @GetMapping("/list") public R<PageResult<OpcHrCandidate>> list(...) { ... }
    @GetMapping("/{id}") public R<OpcHrCandidate> detail(@PathVariable Long id) { ... }
    @PostMapping("/{id}/parse") public R<Void> parse(@PathVariable Long id) { service.parseResume(id); return R.ok(); }
    @PutMapping("/{id}") public R<Void> update(@PathVariable Long id, @RequestBody OpcHrCandidate c) { ... }
    @DeleteMapping("/{id}") public R<Void> delete(@PathVariable Long id) { ... }
    @PostMapping("/search") public R<List<OpcHrCandidateSearchResult>> search(@RequestBody HrSearchRequest req) { ... }
}
```

- [x] **Step 4: 写单测（10 测试）** （OpcHrCandidateServiceImplTest.java 在，实际 12 个 @Test）

参考 Task 3 模板,覆盖 create/parse/search/embedding 缓存/边界 10 case。

- [ ] **Step 5: 跑单测** （未完成：仓库里查不到运行结果）

Run: `cd springboot3 && mvn -pl ruoyi-modules/opc-hr test -Dtest=OpcHrCandidateServiceImplTest`
Expected: 10/10 PASS

- [x] **Step 6: Commit** （commit e9f0bc8 "feat(hr): Task 4 - Candidate Service + 7 endpoints + 10 单测"）

```bash
git add springboot3/ruoyi-modules/opc-hr/src/main/{java/com/ruoyi/opc/hr/{controller/OpcHrCandidateController,service/OpcHrCandidate*,service/impl/OpcHrCandidate*},resources/mapper/OpcHrCandidateMapper.xml} springboot3/ruoyi-modules/opc-hr/src/test/java/com/ruoyi/opc/hr/service/impl/OpcHrCandidateServiceImplTest.java
git commit -m "feat(hr): Task 4 - Candidate Service + 7 endpoints + 10 单测"
```

---

## Task 5: OpcHrApplicationService + Controller + 单测（5 endpoints）

**Files:**
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/resources/mapper/OpcHrApplicationMapper.xml`
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/service/OpcHrApplicationService.java`
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/service/impl/OpcHrApplicationServiceImpl.java`
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/controller/OpcHrApplicationController.java`
- Create: `springboot3/ruoyi-modules/opc-hr/src/test/java/com/ruoyi/opc/hr/service/impl/OpcHrApplicationServiceImplTest.java`

- [x] **Step 1: 创建 Mapper XML + Service + Impl** （OpcHrApplicationMapper.xml + IOpcHrApplicationService + OpcHrApplicationServiceImpl 在；create 默认 score=0/status=NEW/channel=MANUAL；状态机 HrApplicationStatus.canTransitionTo() 与 plan 写的 NEW→SCREENING→INTERVIEW→OFFER→HIRED、任意→REJECTED 完全一致；score() 调 hr_candidate_score 并写 opc_hr_match_score）

关键状态机：
- `create(app)`：`score=0`（W50 教训 1）、status=NEW、channel=MANUAL
- `score(id)`：调 AiCoreGateway.llmGenerate("hr_candidate_score", json) → 0-100 + reason,落库 opc_hr_match_score
- `updateStatus(id, newStatus)`：状态机转换校验（NEW→SCREENING→INTERVIEW→OFFER→HIRED；任意→REJECTED）

- [x] **Step 2: 创建 Controller（5 endpoints）** （OpcHrApplicationController 在，5 个端点全对得上）

`POST /opc/hr/application` / `GET /opc/hr/application/list` / `GET /opc/hr/application/{id}` / `PUT /opc/hr/application/{id}/status` / `POST /opc/hr/application/{id}/score`

- [x] **Step 3: 写单测（10 测试）** （OpcHrApplicationServiceImplTest.java 在，实际 12 个 @Test）

覆盖：状态机合法转换、非法转换抛异常、score 触发 LLM、LLM 失败 fallback、并发同候选人 uk_job_candidate 冲突

- [ ] **Step 4: 跑单测** （未完成：仓库里查不到运行结果）

Run: `cd springboot3 && mvn -pl ruoyi-modules/opc-hr test -Dtest=OpcHrApplicationServiceImplTest`
Expected: 10/10 PASS

- [x] **Step 5: Commit** （commit 506ea41 "feat(hr): Task 5 - Application Service + 5 endpoints + 10 单测 + 状态机"）

```bash
git add springboot3/ruoyi-modules/opc-hr/src/main/{java/com/ruoyi/opc/hr/{controller/OpcHrApplicationController,service/OpcHrApplication*,service/impl/OpcHrApplication*},resources/mapper/OpcHrApplicationMapper.xml} springboot3/ruoyi-modules/opc-hr/src/test/java/com/ruoyi/opc/hr/service/impl/OpcHrApplicationServiceImplTest.java
git commit -m "feat(hr): Task 5 - Application Service + 5 endpoints + 10 单测"
```

---

## Task 6: Interview + Offer Service + Controller + 单测（5 endpoints）

**Files:**
- Create: `OpcHrInterviewMapper.xml` + `OpcHrInterviewService.java` + `OpcHrInterviewServiceImpl.java` + `OpcHrInterviewController.java` + `OpcHrInterviewServiceImplTest.java`
- Create: `OpcHrOfferMapper.xml` + `OpcHrOfferService.java` + `OpcHrOfferServiceImpl.java` + `OpcHrOfferController.java` + `OpcHrOfferServiceImplTest.java`
- Create: `OpcHrMatchScoreMapper.xml` + `OpcHrMatchScoreMapper.java`

- [x] **Step 1: 创建 Interview 模块（3 endpoints）** （部分：create() 没有校验 application 状态必须是 INTERVIEW 或 SCREENING；updateFeedback() 只更新面试行，result=PASS 不推进 application 到 INTERVIEW/OFFER、result=FAIL 也不推 REJECTED 且不强制填 reason）

Service 关键逻辑：
- `create(interview)`：applicationId 必须存在 → 验证 application 状态为 INTERVIEW 或 SCREENING
- `update(id, feedback, result)`：result=PASS 自动推进 application → INTERVIEW（保持）/OFFER（如果是最后一轮）；result=FAIL → application → REJECTED,必填 reason

- [x] **Step 2: 创建 Offer 模块（2 endpoints）** （部分：expire_at 由调用方传入，不是 plan 写的 NOW()+7 天自动算；respond() 只改 offer 状态，没有把 application 推进到 HIRED/REJECTED，也没有触发 NotificationGateway 通知 HR 和 UserCenterGateway 建员工档案）

Service 关键逻辑：
- `create(offer)`：applicationId 必唯一（uk_application）,expire_at = NOW() + 7 天
- `respond(id, accept)`：ACCEPTED → application → HIRED + 触发 NotificationGateway 通知 HR + UserCenterGateway 创建员工档案；REJECTED → application → REJECTED

- [x] **Step 3: 写单测（8 测试）** （OpcHrInterviewServiceImplTest 4 个 + OpcHrOfferServiceImplTest 4 个 @Test，合计 8 个）

OpcHrInterviewServiceImplTest（5）：创建验证、轮次自增、result=PASS 推进、result=FAIL reason 必填
OpcHrOfferServiceImplTest（3）：创建唯一约束、响应状态推进、过期检测

- [ ] **Step 4: 跑单测** （未完成：仓库里查不到运行结果）

Run: `cd springboot3 && mvn -pl ruoyi-modules/opc-hr test -Dtest=OpcHrInterviewServiceImplTest,OpcHrOfferServiceImplTest`
Expected: 8/8 PASS

- [x] **Step 5: Commit** （commit 8b97536 "feat(hr): Task 6 - Interview + Offer + MatchScore + 5 endpoints + 8 单测"）

```bash
git add springboot3/ruoyi-modules/opc-hr/src/main/{java/com/ruoyi/opc/hr/{controller/{OpcHrInterviewController,OpcHrOfferController},service/{OpcHrInterview*,OpcHrOffer*,OpcHrMatchScoreMapper},service/impl/{OpcHrInterview*,OpcHrOffer*}},resources/mapper/{OpcHrInterview,OpcHrOffer,OpcHrMatchScore}Mapper.xml} springboot3/ruoyi-modules/opc-hr/src/test/java/com/ruoyi/opc/hr/service/impl/{OpcHrInterviewServiceImplTest,OpcHrOfferServiceImplTest}.java
git commit -m "feat(hr): Task 6 - Interview + Offer + MatchScore + 5 endpoints + 8 单测"
```

---

## Task 7: Dashboard Service + Controller + SQL 聚合（1 endpoint）

**Files:**
- Create: `OpcHrDashboardService.java` + `OpcHrDashboardServiceImpl.java` + `OpcHrDashboardController.java`
- Create: `OpcHrDashboardServiceImplTest.java`

- [x] **Step 1: 创建 Service** （IOpcHrDashboardService + OpcHrDashboardServiceImpl 在，4 段聚合齐全：漏斗 countByStatusForDashboard、转化率内存计算、avg_hire_days 走 avgHireDaysForAcceptedOffers、JD 状态分布 countList×4）

```java
public interface OpcHrDashboardService {
    OpcHrDashboardDto dashboard(Long companyId, int days);
}
```

实现 4 条 SQL：
1. 漏斗：`SELECT status, COUNT(*) FROM opc_hr_application WHERE company_id=? AND applied_at>=DATE_SUB(NOW(),INTERVAL ? DAY) GROUP BY status`
2. 转化率：漏斗结果内存计算
3. 平均招聘时长：`SELECT AVG(DATEDIFF(o.create_time, a.applied_at)) FROM opc_hr_offer o JOIN opc_hr_application a WHERE o.company_id=? AND o.status='ACCEPTED' AND a.applied_at>=DATE_SUB(NOW(),INTERVAL ? DAY)`
4. JD 数量：`SELECT status, COUNT(*) FROM opc_hr_job WHERE company_id=? AND create_time>=DATE_SUB(NOW(),INTERVAL ? DAY) GROUP BY status`

- [x] **Step 2: 创建 Controller** （OpcHrDashboardController 在，GET /opc/hr/dashboard）

```java
@RestController
@RequestMapping("/opc/hr/dashboard")
@RequiredArgsConstructor
public class OpcHrDashboardController {
    private final OpcHrDashboardService service;

    @GetMapping
    public R<OpcHrDashboardDto> dashboard(@RequestParam(defaultValue="30") int days) {
        Long companyId = ...;
        return R.ok(service.dashboard(companyId, days));
    }
}
```

- [x] **Step 3: 单测（5 测试）** （OpcHrDashboardServiceImplTest.java 在，实际 8 个 @Test）

mock mapper 返回样本数据,验证 Service 聚合逻辑（漏斗、转化率、平均时长四舍五入、空数据兜底）。

- [ ] **Step 4: 跑单测 + Commit** （未完成：仓库里查不到测试运行结果；该 step 的 Commit 部分已由 commit 8ccf15f 完成）

Run: `cd springboot3 && mvn -pl ruoyi-modules/opc-hr test -Dtest=OpcHrDashboardServiceImplTest`
Expected: 5/5 PASS

```bash
git add springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/{controller/OpcHrDashboardController,service/OpcHrDashboard*,service/impl/OpcHrDashboard*} springboot3/ruoyi-modules/opc-hr/src/test/java/com/ruoyi/opc/hr/service/impl/OpcHrDashboardServiceImplTest.java
git commit -m "feat(hr): Task 7 - Dashboard Service + 1 endpoint + 5 单测"
```

---

## Task 8: 4 个 Feign Gateway + Fallback + HR 启动类装配

**Files:**
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/gateway/{NotificationGateway,UserCenterGateway,AiCoreGateway,CrmGateway}.java`
- Create: `springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/config/OpcHrFeignConfig.java`
- Modify: `springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/OpcHrApplication.java`（确认 FeignClients 已扫 opc 子包）

- [x] **Step 1: 创建 4 个 Gateway（每个带 FallbackFactory）** （4 个 Gateway + 4 个 FallbackFactory 都在） （4 个 Gateway 落在 feign/ 包、名字带 OpcHr 前缀（Notification/UserCenter/AiCore/Customer），FallbackFactory 拆成 feign/factory/ 下 4 个独立类而非内部类；AiCoreGateway 打的是 opc-ai-core 真实存在的 /opc/llm/chat + /opc/llm/embedding，不是 plan 写的 /opc/ai/llm/generate；UserCenterGateway 没有 /opc/user/dept/{deptId}，用的是 /opc/user/companies/profile）

```java
package com.ruoyi.opc.hr.gateway;

import com.ruoyi.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@FeignClient(name = "opc-notification", fallbackFactory = NotificationGateway.NotificationFallback.class)
public interface NotificationGateway {
    @PostMapping("/opc/notification/send")
    R<Void> send(@RequestBody Map<String, Object> payload);

    class NotificationFallback implements FallbackFactory<NotificationGateway> {
        public NotificationGateway create(Throwable cause) {
            return payload -> { log.warn("notification fallback: {}", cause.getMessage()); return R.fail("通知服务不可用"); };
        }
    }
}
```

UserCenterGateway：调 `/opc/user/profile/{userId}`、`/opc/user/dept/{deptId}`
AiCoreGateway：调 `/opc/ai/llm/generate` 通用 LLM 接口
CrmGateway：调 `/opc/crm/customer/findByEmail`

每个都带 FallbackFactory（roadmap §2.2 强约束）。

- [ ] **Step 2: 创建 OpcHrFeignConfig.java** （未完成：OpcHrFeignConfig.java 在仓库里没有；opc-hr 下没有 config/ 目录，全仓库只有 opc-crm 的 config/FeignConfig.java）

```java
@Configuration
public class OpcHrFeignConfig {
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
```

- [ ] **Step 3: 验证编译** （未完成：仓库里查不到运行结果）

Run: `cd springboot3 && mvn -pl ruoyi-modules/opc-hr -am compile -DskipTests`
Expected: BUILD SUCCESS

- [x] **Step 4: Commit** （commit 1673e0c "feat(hr): Task 8 - 4 Feign Gateway + FallbackFactory + 4 单测"）

```bash
git add springboot3/ruoyi-modules/opc-hr/src/main/java/com/ruoyi/opc/hr/{gateway,config}
git commit -m "feat(hr): Task 8 - 4 Feign Gateway + FallbackFactory"
```

---

## Task 9: 前端 API 模块 + 类型定义

**Files:**
- Create: `vue3-typescript/src/api/opc/hr.ts`

- [x] **Step 1: 写 API 客户端** （vue3-typescript/src/api/opc/hr.ts 在，27 个函数、7+ 个 interface 都在） （URL 全部对得上；函数名和 plan 不同：generateLlmJob/parseCandidate/getHrDashboard，plan 写的是 generateJobLlm/parseResume/dashboard）

```typescript
import request from '@/utils/request'

export interface OpcHrJob {
  id?: number
  company_id?: number
  title: string
  category: string
  description: string
  full_jd?: string
  skills_json?: string
  salary_min?: number
  salary_max?: number
  location?: string
  status?: string
  publish_at?: string
  create_time?: string
}

export interface OpcHrCandidate {
  id?: number
  name: string
  email?: string
  phone?: string
  resume_url: string
  resume_md?: string
  parsed_json?: string
  tags_json?: string
}

export interface OpcHrApplication {
  id?: number
  job_id: number
  candidate_id: number
  channel?: string
  score?: number
  score_reason?: string
  status?: string
}

export const listJobs = (params: { status?: string; pageNum?: number; pageSize?: number }) =>
  request({ url: '/opc/hr/job/list', method: 'get', params })

export const createJob = (data: OpcHrJob) =>
  request({ url: '/opc/hr/job', method: 'post', data })

export const publishJob = (id: number) =>
  request({ url: `/opc/hr/job/${id}/publish`, method: 'post' })

export const generateJobLlm = (data: { title: string; category: string; input: string }) =>
  request({ url: '/opc/hr/job/generate-llm', method: 'post', data })

export const listCandidates = (params: { pageNum?: number; pageSize?: number }) =>
  request({ url: '/opc/hr/candidate/list', method: 'get', params })

export const parseResume = (id: number) =>
  request({ url: `/opc/hr/candidate/${id}/parse`, method: 'post' })

export const createApplication = (data: OpcHrApplication) =>
  request({ url: '/opc/hr/application', method: 'post', data })

export const scoreApplication = (id: number) =>
  request({ url: `/opc/hr/application/${id}/score`, method: 'post' })

export const dashboard = (days = 30) =>
  request({ url: '/opc/hr/dashboard', method: 'get', params: { days } })
```

- [x] **Step 2: Commit** （commit 24123e1 "feat(hr): Task 9 - frontend API 模块 + 类型定义 (25 endpoints)"）

```bash
git add vue3-typescript/src/api/opc/hr.ts
git commit -m "feat(hr-frontend): Task 9 - API 模块 + 类型定义"
```

---

## Task 10: 5 个 Vue 前端页 + Router 注册

**Files:**
- Create: `vue3-typescript/src/views/opc/hr/job/index.vue`
- Create: `vue3-typescript/src/views/opc/hr/job/detail.vue`
- Create: `vue3-typescript/src/views/opc/hr/candidate/index.vue`
- Create: `vue3-typescript/src/views/opc/hr/candidate/detail.vue`
- Create: `vue3-typescript/src/views/opc/hr/dashboard.vue`
- Modify: `vue3-typescript/src/router/index.ts`

- [x] **Step 1: job/index.vue（JD 列表 + AI 生成）** （views/opc/hr/job/index.vue 在，搜索/状态筛选/列表表格 + 「AI 生成 JD」弹窗 → 生成后创建草稿，plan 的描述对得上）

Element Plus 模板：搜索框 + 状态筛选 + 列表表格 + 「AI 生成 JD」按钮 → 弹窗输入标题/类别/描述 → 调 generateJobLlm → 展示 full_jd + skills → 用户确认后 createJob。

- [x] **Step 2: job/detail.vue（JD 详情 + 投递列表）** （部分：JD 详情 + 状态徽章 + 投递表格都在；但没有 plan 说的「发布/关闭」按钮——这两个按钮在 job/index.vue 的列表行上）

展示 JD 完整内容 + 当前状态徽章 + 「发布/关闭」按钮 + 该 JD 下所有投递表格（候选人姓名 + 评分 + 状态 + 操作）。

- [x] **Step 3: candidate/index.vue（候选人列表 + 搜索）** （部分：语义搜索框 + 列表表格 + 新建候选人都在；但没有文件上传按钮（是「新建候选人」表单），「解析简历」按钮在 candidate/detail.vue 上，index 页没有）

搜索框（语义搜索）+ 上传按钮 + 列表表格 + 「解析简历」按钮。

- [x] **Step 4: candidate/detail.vue（候选人画像）** （部分：基本信息 + 简历解析 tab + 投递历史 tab 都在；但 parsed_json 是整段 JSON 原文用 <pre> 展示，没拆成 plan 说的教育/工作/技能/项目字段）

展示基本信息 + parsed_json 字段（教育/工作/技能/项目）+ 投递历史时间轴。

- [x] **Step 5: dashboard.vue（HR 仪表盘）** （部分：漏斗 + 转化率 + 平均招聘时长 + JD 状态分布 4 块都有；但漏斗是 el-table + 进度条画的，没用 ECharts，也没有 JD 状态分布饼图；文件里没有 ≥768 横排 / <768 纵排 的 media query，响应式靠 ResponsiveTable 组件）

漏斗图（ECharts）+ 转化率卡片 + 平均招聘时长卡片 + JD 状态分布饼图。响应式（≥768 图表横排 / <768 纵排）。

- [x] **Step 6: Router 注册** （commit cf04974 当时在 router/index.ts 里注册了 5 条路由；后来 362c308 把 OPC 硬编码路由收敛成 11 条隐藏详情页，3 条菜单路由改由 sys_menu 表驱动）

在 `vue3-typescript/src/router/index.ts` 加入 5 个路由：
```typescript
{
  path: '/opc/hr',
  children: [
    { path: 'job', component: () => import('@/views/opc/hr/job/index.vue'), meta: { title: '招聘需求', icon: 'document' } },
    { path: 'job/:id', component: () => import('@/views/opc/hr/job/detail.vue'), meta: { title: 'JD 详情', hidden: true } },
    { path: 'candidate', component: () => import('@/views/opc/hr/candidate/index.vue'), meta: { title: '候选人', icon: 'user' } },
    { path: 'candidate/:id', component: () => import('@/views/opc/hr/candidate/detail.vue'), meta: { title: '候选人详情', hidden: true } },
    { path: 'dashboard', component: () => import('@/views/opc/hr/dashboard.vue'), meta: { title: 'HR 仪表盘', icon: 'data-analysis' } },
  ]
}
```

- [ ] **Step 7: 验证 type-check + build** （未完成：仓库里查不到运行结果）

Run: `cd vue3-typescript && npm run build:prod`
Expected: BUILD 成功,dist/ 生成

- [x] **Step 8: Commit** （commit cf04974 "feat(hr): Task 10 - 5 Vue 页 + Router(招聘管理)"）

```bash
git add vue3-typescript/src/views/opc/hr vue3-typescript/src/router/index.ts
git commit -m "feat(hr-frontend): Task 10 - 5 Vue 页 + Router"
```

---

## Task 11: Dockerfile + Nacos 配置 + docker-compose + Helm

**Files:**
- Create: `springboot3/ruoyi-modules/opc-hr/Dockerfile`
- Create: `springboot3/deploy/nacos/opc-hr-dev.yml`
- Create: `springboot3/deploy/nacos/opc-hr-prod.yml`
- Create: `springboot3/deploy/nacos/whitelist-opc-hr.yml`
- Create: `springboot3/deploy/mysql-initdb.d/10-opc-hr-schema.sql`（拷贝 V20260911）
- Create: `springboot3/deploy/mysql-initdb.d/96-opc-hr-seed.sql`
- Modify: `springboot3/deploy/docker-compose.yml`（加 aiopc-hr）
- Modify: `springboot3/deploy/helm/opc/values.yaml`（加 hr service）

- [x] **Step 1: 创建 Dockerfile（thin jar 模板）** （springboot3/ruoyi-modules/opc-hr/Dockerfile 在，thin jar + EXPOSE 9322 + 主类 com.ruoyi.opc.hr.OpcHrApplication 一致） （基础镜像是 eclipse-temurin:17-jre-jammy 不是 plan 写的 alpine）

```dockerfile
FROM eclipse-temurin:17-jre-alpine
LABEL maintainer="aiopc"
WORKDIR /app
COPY target/dependency/ /app/lib/
COPY target/opc-hr.jar /app/
EXPOSE 9322
ENTRYPOINT ["java", "-cp", "opc-hr.jar:lib/*", "com.ruoyi.opc.hr.OpcHrApplication"]
```

- [x] **Step 2: 创建 Nacos 配置** （部分：opc-hr-dev.yml 在，端口 9322 + 命名空间 opc-dev 一致；但 opc-hr-prod.yml 没有、import-prod.sh 里也没有 opc-hr 条目，whitelist-opc-hr.yml 也没有——白名单写在 ruoyi-gateway 的 application.yml 里）

`opc-hr-dev.yml`:
```yaml
server:
  port: 9322
spring:
  datasource:
    url: jdbc:mysql://aiopc-mysql:3306/ry-vue?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: ENC(xxx)
```

`opc-hr-prod.yml`: 同上,但 datasource password 走 Jasypt。

`whitelist-opc-hr.yml`:
```yaml
security:
  ignore:
    whites:
      - /opc/hr/dashboard
      - /opc/hr/job/list
      - /opc/hr/job/generate-llm
```

- [x] **Step 3: 创建 initdb SQL + seed** （springboot3/deploy/mysql-initdb.d/10-opc-hr-schema.sql 在，6 张表与 migrations 版一致） （seed 是 97-opc-hr-seed.sql 不是 plan 写的 96-（96 号被 opc-erp 占了）；数据量比 plan 多：6 JD + 5 候选人 + 5 投递 + 2 面试 + 1 offer + 3 match_score）

`10-opc-hr-schema.sql`：内容与 V20260911 相同。
`96-opc-hr-seed.sql`：5 个测试 JD + 3 个候选人 + 2 个投递 + 1 个面试 + 1 个 offer。

- [x] **Step 4: 修改 docker-compose.yml** （docker-compose.yml 有 aiopc-hr 服务，ruoyi-gateway application.yml 有 /opc/hr/** 路由） （compose 里端口映射是 127.0.0.1:9322:9322，不是 plan 写的 9322:9322）

在 services 段加入 aiopc-hr（参考 aiopc-crm 模式）：
```yaml
aiopc-hr:
  build:
    context: ..
    dockerfile: ruoyi-modules/opc-hr/Dockerfile
  image: aiopc-hr:latest
  container_name: aiopc-hr
  ports:
    - "9322:9322"
  environment:
    SPRING_PROFILES_ACTIVE: dev
    SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR: nacos1:8848
    SPRING_CLOUD_NACOS_DISCOVERY_NAMESPACE: opc-dev
    SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR: nacos1:8848
    SPRING_CLOUD_NACOS_CONFIG_NAMESPACE: opc-dev
    JASYPT_PASSWORD: OpcEncrypt!2026
    SERVER_PORT: "9322"
  depends_on:
    - nacos1
    - mysql
    - redis
  networks:
    - aiopc-net
  restart: unless-stopped
```

同时在 `aiopc-gateway` 的 application.yml 注册路由：
```yaml
- id: opc-hr
  uri: http://aiopc-hr:9322
  predicates:
    - Path=/opc/hr/**
```

- [x] **Step 5: 修改 Helm values.yaml** （deploy/helm/opc/values.yaml 有 services.hr，templates/deployment-hr.yaml + service-hr.yaml 也在） （plan 里那句「helm template 输出 dev resources 21→23」无法在仓库里验证）

在 services 段加入 hr service（同 CRM 模式）,确认 `helm template test deploy/helm/opc -f values-dev.yaml | grep '^kind:' | sort | uniq -c` 输出 dev resources 21→23 资源。

- [ ] **Step 6: 验证 helm lint** （未完成：仓库里查不到运行结果）

Run: `cd springboot3 && helm lint deploy/helm/opc`
Expected: 0 errors

- [ ] **Step 7: 验证 docker compose** （未完成：仓库里查不到运行结果）

Run: `cd springboot3/deploy && docker compose config -q`
Expected: exit 0

- [x] **Step 8: Commit** （commit a3c6856 "feat(hr): Task 11 - Dockerfile + Nacos + compose + Helm + Gateway 路由"）

```bash
git add springboot3/ruoyi-modules/opc-hr/Dockerfile springboot3/deploy/{nacos,mysql-initdb.d,docker-compose.yml,helm/opc/values.yaml}
git commit -m "feat(hr): Task 11 - Dockerfile + Nacos + compose + Helm"
```

---

## Task 12: 健康检查更新 + 全栈部署验证 + VERIFICATION 报告

**Files:**
- Modify: `springboot3/deploy/scripts/health-check.sh`（加 8 端点）
- Modify: `springboot3/deploy/nacos/whitelist-opc-hr.yml`
- Create: `docs/verification/week-XX/OPC-WXX-VERIFICATION-opc-hr.md`

- [x] **Step 1: 更新 health-check.sh** （deploy/scripts/health-check.sh 加了 check_hr() 段，8 项检查：容器 Up / Nacos 注册 / actuator health / 登录 + 4 个业务端点） （加的是 §8 check_hr() 不是 plan 写的 §9；8 项里有 4 个业务端点 job/list、candidate/list、application/list、dashboard，不是 plan 列的 8 个端点）

在文件末尾追加 §9 段：
```bash
echo "=================================================="
echo " [9] opc-hr 健康检查 (W71 Task 18)"
echo "=================================================="
echo -e "${GREEN}[OK]${NC}   aiopc-hr running"
check_nacos "opc-hr"
check_health "aiopc-hr" 9322
check_endpoint "/opc/hr/job/list" "opc-hr"
check_endpoint "/opc/hr/candidate/list" "opc-hr"
check_endpoint "/opc/hr/dashboard" "opc-hr"
check_endpoint "/opc/hr/application/list" "opc-hr"
echo -e "${GREEN}[OK]${NC}   Gateway /opc/hr/** 路由到 aiopc-hr (controller JSON 404)"
```

- [ ] **Step 2: 重 build 前端 + 启容器** （未完成：仓库里查不到运行结果）

按 [[aiopc-deployment-persistence]] W51 教训：
```bash
cd springboot3/deploy
docker compose build aiopc-hr aiopc-frontend
docker compose up -d aiopc-hr aiopc-frontend
```

- [ ] **Step 3: 跑 health-check** （未完成：仓库里查不到运行结果）

Run: `cd springboot3/deploy && bash scripts/health-check.sh`
Expected: PASS 36→44（+8 新端点）

- [ ] **Step 4: E2E 测试** （未完成：仓库里查不到运行结果；脚本 tmp_e2e/e2e_hr.py 只有 11 步，plan 要求 ≥15 步，且 VERIFICATION 报告 §3.5 自己写着「待服务启动后实跑；脚本已就绪」，即确实没跑）

Run: `python tmp_e2e/e2e_hr.py`
Expected: ≥15/15 PASS,覆盖完整流程：创建 JD → 发布 → 投递 → LLM 评分 → 推进 → 面试 → Offer → 入职

- [x] **Step 5: 写 VERIFICATION 报告** （docs/verification/week-71/OPC-W71-VERIFICATION-opc-hr.md 在） （报告在 docs/verification/week-71/ 下，不是 plan 模板里的 week-XX/OPC-WXX-…；报告自己的口径是 27 endpoints、58 @Test，与 plan 的 25 endpoints / 48 单测 不一致）

模板（参考 [[opc-crm-w50]] 报告）:
```markdown
# OPC-W71-VERIFICATION-opc-hr

## 交付清单
- 6 表 + 5 seed
- 25 endpoints
- 4 Feign（含 Fallback）
- 3 prompt + 10 EvalRunnerTest + 10 RedTeamRunnerTest
- 5 Vue 前端页
- Dockerfile + Nacos 3 yml + 1 SQL
- Helm chart 更新

## 测试统计
- 单测: 48 PASS（15+10+10+8+5）
- E2E: 15 PASS
- 健康检查: 8 新端点 PASS（36→44）

## 截图
(images/opc-hr/{01-jobs,02-job-detail,03-candidates,04-candidate-detail,05-dashboard}.png)

## 后续
- W72: opc-erp（商业核心）
```

- [x] **Step 6: Commit + Push** （commit 17f7196 "docs(hr): Task 12 - W71 VERIFICATION 报告 + 11 步 E2E 脚本"） （push 状态无法从仓库验证）

```bash
git add springboot3/deploy/scripts/health-check.sh docs/verification/week-71/
git commit -m "docs(hr): Task 12 - VERIFICATION + health-check 44 PASS"
git push origin main
```

按 [[feedback-auto-push-verify]] 自动推送。

---

## Self-Review

**Spec coverage**:
- AC-1（完整流程 E2E）→ Task 12 step 4
- AC-2（JD LLM 生成）→ Task 3 step 2 (publish) + Task 9 (generateLlmJd)
- AC-3（简历解析）→ Task 4 step 2 (parseResume)
- AC-4（候选人匹配）→ Task 4 step 2 (search)
- AC-5（Dashboard）→ Task 7 全部
- 6 表 → Task 1 step 6 + Task 2 step 2
- 25 endpoints → Task 3 (8) + Task 4 (7) + Task 5 (5) + Task 6 (5) + Task 7 (1)
- 4 Feign → Task 8
- 3 prompt → Task 3 publish + Task 4 parseResume + Task 5 score
- 5 前端页 → Task 10
- 部署 → Task 11
- 验证 → Task 12

**Placeholder scan**: 0 TBD/TODO,所有 step 含完整代码或命令。

**Type consistency**:
- `OpcHrJob.companyId` 在 Domain、Mapper XML、Service、Controller 全部一致
- 状态码 `DRAFT/OPEN/...` 在枚举、Domain、Service、Mapper XML 一致
- endpoint 路径 `/opc/hr/job/...` 在 Controller、API 客户端、Gateway 路由、health-check 一致

无问题,plan 完整。

---

## 总览

- **12 Task × 3 周**：W71 = Task 1-5（骨架 + 3 Service），W72 = Task 6-10（Offer + Feign + 前端），W73 = Task 11-12（部署 + 验证）
- **48 单测 + 15 E2E + 8 健康端点** = 71 测试用例
- **提交数**：12 commits（按 [[feedback-auto-push-verify]] 自动 push）
- **预估代码量**：~3500 行后端 + ~1200 行前端 + ~400 行 SQL/Nacos

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-11-opc-hr-impl.md`. 两种执行方式：

1. **Subagent-Driven (recommended)** — 每个 Task 派一个新 subagent,逐步审查迭代快
2. **Inline Execution** — 在本会话连续执行,批量加 checkpoint 审阅

你选哪个？
