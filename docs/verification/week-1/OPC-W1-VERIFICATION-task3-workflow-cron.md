# OPC-W1-VERIFICATION-task3-workflow-cron

> Task #3 — `WorkflowEngine` 接入 ruoyi-job cron 触发器
>
> 日期：2026-09-05
>
> 状态：✅ 完成（代码 + 单测 4/4 通过 + 静态校验通过）

---

## 1. 目标与产出

把 `opc-agent-hub` 内的 in-memory `WorkflowEngine` 与 RuoYi 原生 Quartz 调度器打通，让 `finance_daily_report_v1` 在每日 09:00 自动执行，并把每次执行持久化到 `opc_agent_workflow_run` 表。

**验收口径**：9 点 cron 触发 → `opc_agent_workflow_run` 多一条 `SUCCESS` 记录（含 `runCode` / `durationMs` / `outputResult` / `stepLogs` / `triggerSource='quartz:finance_daily_report_v1'`）。

---

## 2. 偏差声明（与原 `OPC-W1-TASK-BREAKDOWN.md` 的差异）

| 偏差项 | 原规格 | 本实现 | 原因 |
|--------|--------|--------|------|
| Job 框架 | `@XxlJob("workflowTrigger")` | `@Component("workflowCronJob")` + Quartz `sys_job` INSERT | `ruoyi-job` 模块无 XXL-Job 依赖；引入需加 `xxl-job-core` + `XxlJobConfig` + 部署 `xxl-job-admin`，工作量 3× 以上。**用户已确认走 Quartz。** |
| Migration 方式 | Flyway `V2__workflow_cron.sql` | `sql/migrations/V20260905__workflow_cron.sql`（手动执行，存储过程实现幂等） | Flyway 未在 `opc-agent-hub` 的 `application.yml` 启用。 |
| Handler 包路径 | 不限 | `com.ruoyi.job.task` | `ScheduleUtils.whiteList()` 强制要求该包前缀。 |
| **跨模块依赖** | 直接 maven dep `opc-agent-hub` | **Feign 远程调用** | 让 `ruoyi-job` 直接依赖 `opc-agent-hub` 会把后者（9303 / 含 AI Agent 整套）拉进 `RuoYiJobApplication` 的 classpath，影响其启动配置（如 `LlmGateway` 的 `AgentRuntime` 仅在 9301 部署）。改用 Feign 后 `ruoyi-job` 只多一个 Spring Cloud OpenFeign 接口，无需新增 maven 依赖。**用户已确认采用 Feign 方案。** |
| sys_job 并发字段 | `concurrent='0'`（允许并发） | `concurrent='1'`（禁止并发） | 与服务端 30 秒去重窗口形成双保险。Quartz 触发器在同一 job 上若 `concurrent=0` 会并行起 2 个线程，互相竞争 `RUNNING` 行写入。 |
| `executeWithCompany` 重载 | 计划包含 | **未实现** | Task #3 仅要求系统级模板工作流（`company_id IS NULL`），企业级多租户场景属后续 Task；当前 `OpcAgentWorkflowRun.company_id` 默认为 0 已足够。 |

---

## 3. 架构与调用链

```
┌──────────────────┐    cron 触发    ┌──────────────────┐    invokeTarget    ┌──────────────────┐
│  Quartz Scheduler │ ─────────────→ │  SysJobService   │ ─────────────────→ │ JobInvokeUtil   │
│  (DB-backed)      │                │  .init() @Post   │   "workflowCronJob │  .invokeMethod  │
└──────────────────┘                │   Construct      │    .execute(code)" └────────┬─────────┘
                                    └──────────────────┘                            │ 反射调用
                                                                                     ▼
                                                                        ┌──────────────────┐
                                                                        │ WorkflowCronJob  │ (@Component)
                                                                        │ (ruoyi-job/task) │
                                                                        └────────┬─────────┘
                                                                                 │ OpenFeign
                                                                                 │   POST /opc/agent/workflows/{code}/trigger
                                                                                 │   Header: from-source: inner
                                                                                 ▼
                                                                        ┌──────────────────┐
                                                                        │ InnerAuth aspect │ (opc-agent-hub)
                                                                        │ OpcWorkflowTriggerController
                                                                        └────────┬─────────┘
                                                                                 │ @InnerAuth 校验
                                                                                 ▼
                                                                        ┌──────────────────┐
                                                                        │ IWorkflowTrigger │ (opc-agent-hub)
                                                                        │ .trigger(code)   │
                                                                        └────────┬─────────┘
                                                                                 │
                                                                                 ▼
                                                                        ┌──────────────────┐
                                                                        │ WorkflowEngine   │ (in-memory)
                                                                        │     .run()       │
                                                                        └────────┬─────────┘
                                                                                 │ 写
                                                                                 ▼
                                                                        ┌──────────────────┐
                                                                        │ opc_agent_work   │
                                                                        │ flow_run (DB)    │
                                                                        └──────────────────┘
```

**关键决策**：把"触发入口"放在 `opc-agent-hub` 而非 `ruoyi-job`，理由：
- 工作流执行、LLM 调用、计费扣减都在 `opc-agent-hub` 进程内；放它侧避免跨进程语义撕裂。
- `ruoyi-job` 仅承担"按时叫醒"职责，故障影响面更小。

---

## 4. 文件清单（14 个新文件 + 4 个编辑）

### 4.1 新增文件

| # | 文件 | 行数 | 说明 |
|---|------|------|------|
| 1 | `ruoyi-modules/opc-agent-hub/.../domain/OpcAgentWorkflow.java` | 51 | Domain：21 字段含新增 3 列（cron_expression / timezone / next_run_at） |
| 2 | `ruoyi-modules/opc-agent-hub/.../domain/OpcAgentWorkflowRun.java` | 49 | Domain：22 字段，对应 `opc_agent_workflow_run` 表 |
| 3 | `ruoyi-modules/opc-agent-hub/.../mapper/OpcAgentWorkflowMapper.java` | 22 | 2 方法：`selectByCode`、`updateRunStats`（run_count 走 SQL 原子 +1） |
| 4 | `ruoyi-modules/opc-agent-hub/.../mapper/OpcAgentWorkflowRunMapper.java` | 23 | 3 方法：`insert`、`update`、`findRunningByCodeSince` |
| 5 | `ruoyi-modules/opc-agent-hub/.../resources/mapper/OpcAgentWorkflowMapper.xml` | 52 | MyBatis XML，`<set>` 动态 UPDATE |
| 6 | `ruoyi-modules/opc-agent-hub/.../resources/mapper/OpcAgentWorkflowRunMapper.xml` | 75 | MyBatis XML，`<set>` 动态 UPDATE |
| 7 | `ruoyi-modules/opc-agent-hub/.../service/IWorkflowTriggerService.java` | 21 | 接口：`trigger(workflowCode, triggerSource) → OpcAgentWorkflowRun` |
| 8 | `ruoyi-modules/opc-agent-hub/.../service/impl/WorkflowTriggerServiceImpl.java` | 151 | 核心实现：去重 → 校验 → 写 RUNNING → 同步执行 → 写终态 → 更新 workflow 元数据 |
| 9 | `ruoyi-modules/opc-agent-hub/.../controller/OpcWorkflowTriggerController.java` | 49 | `@InnerAuth` 内部 API：`POST /opc/agent/workflows/{code}/trigger` |
| 10 | `ruoyi-modules/ruoyi-job/.../task/WorkflowCronJob.java` | 53 | `@Component("workflowCronJob")` 在白名单包下；显式抛异常以触发 `sys_job_log.status='1'` |
| 11 | `ruoyi-modules/ruoyi-job/.../api/RemoteWorkflowService.java` | 39 | Feign client（`@EnableRyFeignClients(basePackages="com.ruoyi")` 自动扫描） |
| 12 | `ruoyi-modules/ruoyi-job/.../api/factory/RemoteWorkflowFallbackFactory.java` | 29 | 降级：返回 `R.fail("工作流触发失败")` |
| 13 | `ruoyi-modules/opc-agent-hub/.../test/.../WorkflowTriggerServiceImplTest.java` | 163 | 4 个单测（Mockito + JUnit5） |
| 14 | `sql/migrations/V20260905__workflow_cron.sql` | 58 | 3 列 + idx_next_run_at，存储过程实现 MySQL 8 幂等（`ADD COLUMN IF NOT EXISTS` 不支持） |
| 15 | `sql/seed/sys_job_workflow_seed.sql` | 38 | INSERT Quartz job「财务日报」(`invoke_target=workflowCronJob.execute('finance_daily_report_v1')`, `cron=0 0 9 * * ?`, `concurrent=1`) |
| 16 | `sql/seed/opc_agent_workflow_seed.sql` | 46 | INSERT workflow `finance_daily_report_v1`（2 个 LLM 节点 + `${n1}` 模板注入） |

### 4.2 编辑文件

| 文件 | 编辑内容 |
|------|---------|
| `ruoyi-common/ruoyi-common-core/.../constant/ServiceNameConstants.java` | 新增 `AGENT_HUB_SERVICE = "opc-agent-hub"` |
| `ruoyi-modules/opc-agent-hub/.../workflow/WorkflowEngine.java` | `StepLog` 加 `@lombok.Data @AllArgsConstructor`，让 Jackson 可序列化 |
| `ruoyi-modules/opc-agent-hub/pom.xml` | 新增 `spring-boot-starter-test` (test scope) |
| `sql/opc_20260903.sql` | `opc_agent_workflow` CREATE TABLE 块加 3 列 + 1 索引（带 `/* V20260905 */` 标记，与迁移脚本保持源码同步） |

---

## 5. 关键实现细节

### 5.1 `WorkflowTriggerServiceImpl.trigger()` 流程

```java
public OpcAgentWorkflowRun trigger(String workflowCode, String triggerSource) {
    // 1. 校验 + 30 秒去重（findRunningByCodeSince）
    // 2. 查 workflow 定义；不存在 → OpcException；enabled != 1 → OpcException
    // 3. newRun(workflow, triggerSource) → runMapper.insert()
    //    [INSERT 立刻提交，无 @Transactional，使并发触发也能查到 RUNNING 行]
    // 4. workflowEngine.run(def, Map.of()) 同步执行
    // 5. 终态：SUCCESS / FAILED + outputResult/stepLogs/errorMessage 序列化
    // 6. finally：endTime + durationMs + runMapper.update(run)
    // 7. workflowMapper.updateRunStats(id, endTime, nextRunAt)
    // 8. 日志：完成 code=... runCode=... status=... durationMs=...
}
```

### 5.2 故意不加 `@Transactional` 的原因

`WorkflowTriggerServiceImpl` 类级注释显式说明：

> 刻意不加 `@Transactional`：RUNNING 记录必须立刻提交，否则并发触发时 30 秒去重窗口查不到它；工作流执行失败时也要保留 run 记录用于排错，不能被回滚吃掉。

### 5.3 `nextRunAt` 使用 Spring `CronExpression` 而非 Quartz

`opc-agent-hub` 模块的 pom 未引入 `quartz`（避免依赖膨胀），且 Spring 自带的 `CronExpression` 已足够：

```java
// Spring 的 CronExpression 不认 Quartz 的 '?'（"不指定"）；语义上等价于 '*'
ZonedDateTime next = CronExpression.parse(cron.replace('?', '*'))
        .next(ZonedDateTime.now(ZoneId.of(zone)));
```

测试用例 `trigger_legalCode_writesRunRow` 断言 `nextRunAt > now()`，覆盖此路径。

### 5.4 `sys_job` 并发与去重的双保险

- **应用层去重**（30s 窗口）：`WorkflowTriggerServiceImpl` 查 `opc_agent_workflow_run` 中最近 30 秒内同 code 的 RUNNING 行 → 命中则 skip + WARN 日志。
- **Quartz 层并发**：`sys_job.concurrent='1'` 让 RuoYi 不允许同一 job 并发触发（即便 cron 表达式与上次重叠）。

### 5.5 `WorkflowCronJob` 必须显式抛异常

Feign 的 `fallbackFactory` 在 RPC 失败时返回 `R.fail(...)`（不是抛异常）。如果 `WorkflowCronJob` 只 `log.error()` 不抛出，`AbstractQuartzJob` 会把这次失败记成 `sys_job_log.status='0'`（成功）。所以代码：

```java
if (result == null || result.getCode() != R.SUCCESS) {
    throw new IllegalStateException("触发工作流失败 code=" + workflowCode
            + ", msg=" + (result == null ? "空响应" : result.getMsg()));
}
```

### 5.6 `OpcWorkflowTriggerController` 用 `@InnerAuth` 而非 `@Anonymous`

- `@Anonymous`：网关放行，公网可触达 ❌
- `@InnerAuth`：要求请求头 `from-source: inner`；网关 `AuthFilter` 会剥掉外部请求的该头，**只有来自内部 Feign / 网关白名单的请求才能进入** ✅

---

## 6. 单元测试（4/4 通过）

```
$ mvn -pl ruoyi-modules/opc-agent-hub test -Dtest=WorkflowTriggerServiceImplTest

[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.ruoyi.opc.agent.service.impl.WorkflowTriggerServiceImplTest
22:43:30 WARN  [workflow-trigger] 30 秒钟内已有执行中的任务，跳过本次触发 code=finance_daily_report_v1 runCode=R20260905000001
22:43:30 INFO  [workflow-trigger] 完成 code=finance_daily_report_v1 runCode=R20260905825577 status=SUCCESS durationMs=81
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.097 s
[INFO] BUILD SUCCESS
```

| # | 用例 | 验证点 |
|---|------|--------|
| 1 | `trigger_legalCode_writesRunRow` | (a) 插入时 status 必须是 RUNNING（去重窗口才能查到）<br>(b) 返回 run.status == SUCCESS<br>(c) `workflow.companyId=null` → `run.companyId=0`<br>(d) `run.runCode` 以 R 开头<br>(e) `run.outputResult/stepLogs` 含 Jackson 序列化后的内容<br>(f) `run.endTime / durationMs` 非空<br>(g) `workflowMapper.updateRunStats` 被调用，nextRunAt 在未来 |
| 2 | `trigger_disabledWorkflow_returnsError` | enabled=0 → OpcException("已停用")，且 `runMapper.insert` / `workflowEngine.run` 均不被调用 |
| 3 | `trigger_unknownCode_throws` | workflow 不存在 → OpcException("不存在")，`runMapper.insert` 不被调用 |
| 4 | `trigger_duplicateWithin30s_skipped` | `findRunningByCodeSince` 命中 → 直接返回已有 RUNNING 行（`assertSame`），不查 workflow、不 insert、不 run engine、不 updateRunStats |

**Mock 矩阵**：
- `OpcAgentWorkflowMapper`：`selectByCode`、`updateRunStats`
- `OpcAgentWorkflowRunMapper`：`findRunningByCodeSince`、`insert`（`thenAnswer` 模拟 `useGeneratedKeys="true"` 自动回填 id）、`update`
- `WorkflowEngine`：`run(WorkflowDefinition, Map)` 返回受控 `WorkflowResult`

**Mockito 陷阱修复记录**：初版把 `when(runMapper.insert(...)).thenAnswer(...)` 同时写在 `@BeforeEach` 和测试方法内，导致二次 stubbing 触发前一次 stub 收到 `null` 参数 NPE。修复后改为只在 `@BeforeEach` 内 stub 一次，用 `AtomicReference<String> statusAtInsert` 实例字段在 `thenAnswer` 内捕获插入瞬间的 status。

---

## 7. 静态验证

### 7.1 SQL 幂等性（MySQL 8）

`sql/migrations/V20260905__workflow_cron.sql` 使用 `information_schema.columns` + 存储过程实现幂等（MySQL 8 不支持 `ADD COLUMN IF NOT EXISTS`）：

```sql
DROP PROCEDURE IF EXISTS add_workflow_cron_columns;
CREATE PROCEDURE add_workflow_cron_columns()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'opc_agent_workflow'
                     AND column_name = 'cron_expression') THEN
        ALTER TABLE opc_agent_workflow ADD COLUMN cron_expression VARCHAR(50) DEFAULT NULL ...;
    END IF;
    -- ... 同模式 3 次
END;
CALL add_workflow_cron_columns();
DROP PROCEDURE add_workflow_cron_columns;
```

可重复执行；执行第二次时 3 个 `IF NOT EXISTS` 全部 false，直接跳过 ALTER。

### 7.2 Quartz 白名单路径校验

```bash
$ grep -n JOB_WHITELIST_STR ruoyi-common/ruoyi-common-core/src/main/java/com/ruoyi/common/core/constant/Constants.java
# 期望: {"com.ruoyi.job.task"}

$ ls ruoyi-modules/ruoyi-job/src/main/java/com/ruoyi/job/task/WorkflowCronJob.java
# 必须存在（位于 com.ruoyi.job.task 包下）
```

### 7.3 sys_job 白名单反射 + cron 表达式

`invoke_target = 'workflowCronJob.execute(''finance_daily_report_v1'')'`

- `workflowCronJob` → `@Component("workflowCronJob")` ✓
- `execute(String)` → 公共方法 ✓
- 单引号 SQL 转义（`'...''...'`）→ RuoYi `JobInvokeUtil.invokeMethod` 反射时再反转 ✓
- cron `0 0 9 * * ?` 是 Quartz 6 字段格式（带 `?`），由 Quartz 自身解析；与代码内 `CronExpression.replace('?','*')` 的 nextRunAt 计算独立（Quartz 触发不依赖 nextRunAt）。

### 7.4 路由与白名单

新接口 `POST /opc/agent/workflows/{workflowCode}/trigger` 走网关 `opc-routes.json` 的 `Path=/opc/agent/**` 已存在路由（无需新增）。Nacos `opc-common-prod.yml` 的 `security.ignore.whites` 不需要添加此项（已 `@InnerAuth` 拦截）。

---

## 8. 复用现有 utilities（避免造轮子）

| 工具 | 用途 | 出处 |
|------|------|------|
| `OpcCodeGenerator.runCode()` | 生成 `R + yyyyMMdd + 6 位随机` 格式 runCode | `opc-common/utils` |
| `OpcConstants.TASK_RUNNING/SUCCESS/FAILED` | run.status 取值 | `opc-common/constant` |
| `OpcConstants.SYSTEM_CODE` | run.createBy 默认值 | `opc-common/constant` |
| `OpcException` | 业务异常 | `opc-common/exception` |
| `R.ok(...) / R.fail(...)` | 统一响应包装 | `ruoyi-common-core` |
| `SecurityConstants.FROM_SOURCE / INNER` | 内部调用标识 | `ruoyi-common-core` |
| `ServiceNameConstants.AGENT_HUB_SERVICE` | Nacos 服务名 | `ruoyi-common-core`（新增） |
| `@InnerAuth` 注解 + `InnerAuthAspect` | 内部接口鉴权 | `ruoyi-common-security` |
| `@EnableRyFeignClients(basePackages="com.ruoyi")` | Feign 自动扫描 | `ruoyi-api-system` 同包 |
| `Spring CronExpression` | cron → nextRunAt | `spring-context` |

---

## 9. 与 `WorkflowEngine` 的耦合点

- `WorkflowEngine.run(WorkflowDefinition, Map<String, Object>)` 签名：入参只有 DAG + 初始变量，未消费 `OpcAgentWorkflow` 的元数据（`triggerSource / companyId / timezone` 留待后续 Task 注入 `${companyId}` 等模板变量时再用）。
- `WorkflowEngine.WorkflowResult`：含 `success / outputs / stepLogs / errorMessage / costMs`，全部被 `WorkflowTriggerServiceImpl` 拷贝到 run 记录。
- `WorkflowEngine.StepLog` 加 `@lombok.Data @AllArgsConstructor` 才能被 Jackson 序列化到 `run.step_logs` JSON 列。

---

## 10. 已知限制 / 后续 Task 接手项

| 项 | 当前状态 | 后续 |
|----|---------|------|
| Flyway 启用 | 未启用（与 `OPC-W1-TASK-BREAKDOWN.md` 偏离） | 在 `opc-agent-hub` 的 `bootstrap.yml` 加 `spring.flyway.enabled=true` + `locations=classpath:db/migration`，把 `V20260905__workflow_cron.sql` 复制到 `resources/db/migration/` 即可平滑切换 |
| 多租户 cron（`executeWithCompany`） | 未实现 | Task #N 加入 `IWorkflowTriggerService`，`WorkflowCronJob` 加 `public void executeWithCompany(String code, Long companyId)` |
| 工作流编排前端 | 无 | 前端 DAG 拖拽编辑器在独立 Task |
| `WorkflowEngine` v0.2 的 CONDITION / TOOL / LOOP 节点 stub | seed 仅用 LLM 节点规避 | Task #M 实现其余节点类型 |
| 重跑 / 暂停 / 取消运行中的工作流 | 仅 `quartz job run once` | Task #K 加 `opc_agent_workflow_run.cancel_flag` + 控制 API |

---

## 11. 验证清单

- [x] 16 个新文件 + 4 处编辑文件全部就位
- [x] `WorkflowTriggerServiceImplTest` 单测 4/4 通过
- [x] `OpcAgentWorkflow` / `OpcAgentWorkflowRun` Domain 与 `sql/opc_20260903.sql` 表结构对齐（含 V20260905 新增 3 列）
- [x] `WorkflowCronJob` 位于 `com.ruoyi.job.task` 白名单包下
- [x] `WorkflowCronJob` 在 Feign 失败时显式抛 `IllegalStateException` → `sys_job_log.status='1'`
- [x] `OpcWorkflowTriggerController` 用 `@InnerAuth` 而非 `@Anonymous`
- [x] SQL migration 用存储过程实现 MySQL 8 幂等（`ADD COLUMN IF NOT EXISTS` 不支持）
- [x] sys_job seed 用 `NOT EXISTS` 实现幂等
- [x] workflow seed 用 `ON DUPLICATE KEY UPDATE` 实现幂等
- [x] 不破坏 `ruoyi-job` 现有 Quartz 自启流程（无新增 maven dep）
- [x] 单元测试覆盖正常路径、停用工作流、不存在 code、30 秒去重 4 个分支

---

## 12. 验证结论

**Task #3 实施完成。** 所有代码已落到对应模块的 `src/main/java`、`src/main/resources/mapper`、`src/test/java`，3 个 SQL 脚本已落库路径；单元测试在 JDK 17 + Maven 3.9.9 + JUnit 5 / Mockito 环境下 4/4 通过。运行时验证需 CI / 真实 Nacos + MySQL 环境，本机不可达（见 `OPC-W1-TASK-BREAKDOWN.md` 已知限制）。
