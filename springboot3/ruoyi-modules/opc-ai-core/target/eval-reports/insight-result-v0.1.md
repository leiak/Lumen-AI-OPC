# Insight Eval v0.1 — Live LLM Run

> 生成时间：2026-09-08
> 评测集：insight-agent-v1.0.json（80 条用例，4 类场景 × 20 条）
> 计划 spec：`docs/superpowers/plans/2026-09-08-opc-insight-mvp.md` Task 19（第 1603–1621 行）

## Status: BLOCKED — 无可执行 live LLM 通道

| 维度 | 结果 |
|------|------|
| Mock 离线通过率 | **100.0% (80/80)**（已在 `insight-eval-baseline-v0.1.md` 记录，Task 16） |
| Live LLM 通过率 | **未跑**（环境阻断，见下） |
| 目标阈值 | ≥ 85% |

## 阻断原因（按决定性排序）

### 1. `-Peval-live` profile 在 pom.xml 中**不存在**

```
$ mvn -pl ruoyi-modules/opc-ai-core help:active-profiles -Peval-live
[WARNING] The requested profile "eval-live" could not be activated because it does not exist.
```

`opc-ai-core/pom.xml`（实际文件 `D:/work-ai/0401-lumen-opc/springboot3/ruoyi-modules/opc-ai-core/pom.xml`）只有默认构建段，没有 `<profiles>` 块。`-Peval-live` 在 `EvalRunnerTest.java` 的注释（line 16）和 `MockFinanceLlmProvider.java` 注释（line 19）里被提及，但**profile 从未被实现**。

### 2. `EvalInsightRunnerTest` 硬编码 Mock，无 live LLM 切换路径

`EvalInsightRunnerTest.setUp()`（line 51–54）：

```java
@BeforeEach
void setUp() {
    provider = new MockInsightLlmProvider();
}
```

`EvalInsightRunnerTest.runAll()`（line 152–181）只调用 `provider.extract(input)`，没有任何 `System.getProperty("eval.live")`、`@Profile` 或环境变量开关。即使 profile 存在并尝试注入 `LlmGateway`，也找不到注入点。

### 3. 评测集 / Mock 体系当前只覆盖 Mock 模式

`EvalInsightRunnerTest` 与 `MockInsightLlmProvider` 是双向绑定的（Mock 内部实现直接对应 prompt v1.0 规则的 Java 镜像）。这意味着：
- 没有 `LiveInsightLlmProvider` 类
- 没有 `LlmGateway.chat(...)` → `MockInsightLlmProvider.extract(...)` 适配层
- 没有 real-LLM 输出格式的 comparator fallback

### 4. JDK 版本不兼容

```
[ERROR] com/ruoyi/opc/ai/eval/EvalInsightRunnerTest has been compiled by a more recent version
        of the Java Runtime (class file version 61.0), this version of the Java Runtime only
        recognizes class file versions up to 52.0
```

| 环境 | Java 版本 | 备注 |
|------|-----------|------|
| 本地 Maven (`mvn -version`) | 1.8.0_231 (class 52.0) | JDK 8 |
| `opc-ai-core` 编译目标 | 17 (class 61.0) | W1 已确认「this module IS runnable locally on JDK 17, unlike the Spring Boot 3 services」 |

`mvn test -Dtest=EvalInsightRunnerTest` 在本地 JDK 8 上连 surefire 启动都失败（forked JVM 拒绝加载 class 61.0）。

### 5. 无 LLM API key / 无外网访问

```
OPENAI_API_KEY=(unset)
DEEPSEEK_API_KEY=(unset)
ZHIPUAI_API_KEY=(unset)
EEVAL_LLM_KEY=(unset)
```

`application.yml`（`src/main/resources/application.yml:19`）默认是 `api-key: ${OPENAI_API_KEY:sk-placeholder}`，而 `sk-placeholder` 会在任何真实调用时被远端 401。即便有 key，本地环境到 `api.openai.com` / `api.deepseek.com` 也不可达。

## 当前 Mock 基线（已提交，Task 16）

`target/eval-reports/insight-eval-baseline-v0.1.md`：

| 场景 | 总数 | 通过 | 通过率 |
|------|------|------|--------|
| KPI_SUMMARY | 20 | 20 | 100.0% |
| ANOMALY | 20 | 20 | 100.0% |
| ADVICE | 20 | 20 | 100.0% |
| TRAP | 20 | 20 | 100.0% |
| **合计** | **80** | **80** | **100.0%** |

Mock 100% 通过是「把 prompt v1.0 的判定规则用 Java 写一遍」的结果，**不代表**真实 LLM 的真实准确率（spec 已明确警告）。

## Recommendation

按 spec 「If pass rate < 85% in production, iterate insight prompts (Task 15)」的方针，**留待部署时验证**：

### 部署时（CI / staging）补做 Task 19

1. **实现 `-Peval-live` profile**（前置工作，建议作为 Task 19.5 单独提）：
   - 在 `opc-ai-core/pom.xml` 加 `<profile><id>eval-live</id>...<build><plugins>` 切换 `System.getProperty("eval.live")=true`
   - 在 `EvalInsightRunnerTest` 加 `if ("true".equals(System.getProperty("eval.live"))) provider = new LiveInsightLlmProvider();` 分支
   - 写 `LiveInsightLlmProvider`（包 `com.ruoyi.opc.ai.eval`），内部调 `LlmGateway.chat(...)`，把 JSON output 拆成 `Map` 走同一个 `InsightComparator`

2. **在 staging runner 上跑**（JDK 17 + 有 LLM key 的环境）：

   ```bash
   mvn -pl ruoyi-modules/opc-ai-core test \
     -Dtest=EvalInsightRunnerTest \
     -Peval-live \
     -DOPENAI_API_KEY=$OPENAI_API_KEY \
     -DDEEPSEEK_API_KEY=$DEEPSEEK_API_KEY
   ```

3. **判定**：
   - 通过率 ≥ 85% → 把这次的 `insight-result-v0.1.md` 替换为真实数字
   - 通过率 < 85% → 按 Task 15 迭代 prompt（system / soft-anomaly / advice），重新跑 Task 16 的 Mock 基线，再跑 Task 19 的 Live

### 为什么不在本任务里硬补 live 路径

- **Scope discipline**（任务约束）：「DO NOT MODIFY any Java file」「DO NOT MODIFY any config file (LlmGateway API key config)」
- 加 `-Peval-live` profile 必然改 `pom.xml` + 写新 Java 类，已经越界
- 即便改出来，本地 JDK 8 + 无 key + 无外网三重硬阻断，仍然跑不出真实数字

## 结论

**Task 19 在当前环境不可执行。** 留作部署时阻断点（deployment-time gate），由 ops / CI 在 JDK 17 + LLM key 就绪后补做。本报告作为「已知未完成项」入仓，配合 Task 20 验证报告一起提交。

---

> 注：本报告由 Task 19 子任务生成（2026-09-08），仅记录 blocker + 部署建议，不修改任何代码或配置。
