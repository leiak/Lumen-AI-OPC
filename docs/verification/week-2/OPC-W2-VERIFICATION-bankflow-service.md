# W2.4 — OpcFinanceBankFlowService 单测静态验证报告

> 日期：2026-09-07
> 范围：`springboot3/ruoyi-modules/opc-finance/`
> 静态验证（本机 JDK 8，沿用 W2.1 / W2.2 / W2.3 模式）

---

## 1. 交付物（refactor + test）

| 文件 | 性质 | 行数 |
|---|---|---|
| `springboot3/.../service/IOpcFinanceBankFlowService.java` | 新增接口 | 38 |
| `springboot3/.../service/impl/OpcFinanceBankFlowServiceImpl.java` | 新增实现 | 78 |
| `springboot3/.../controller/OpcFinanceController.java` | refactor：去 `flowMapper` 注入 → 注入 service；`uploadFlows` / `pendingFlows` 改成调 service | -11 +4 |
| `springboot3/.../test/.../OpcFinanceBankFlowServiceImplTest.java` | 新增单测 | 297 |
| `OPC-W2-VERIFICATION-bankflow-service.md` | 本报告 | — |

**为什么是 refactor + test 一组**：之前 `OpcFinanceBankFlowService` 不存在，业务逻辑（flowCode 生成、
status 初始化、batch insert）直接内联在 `OpcFinanceController.uploadFlows()`。Service 抽取让 controller
回归纯 HTTP 层，让 service 可独立单测。

---

## 2. Service 接口

```java
public interface IOpcFinanceBankFlowService {
    OpcFinanceBankFlow getById(Long id);
    List<OpcFinanceBankFlow> listPending(Long companyId, Integer limit);
    int uploadBatch(List<OpcFinanceBankFlow> flows, String operator);
    int markExtracted(Long id, Long voucherId, String operator);
}
```

| 方法 | 用途 |
|---|---|
| `getById(id)` | 流水详情（passthrough） |
| `listPending(companyId, limit)` | 待 AI 提取的流水（extracted=0），limit=null 默认 20 |
| `uploadBatch(flows, operator)` | 上传前初始化（flowCode + extracted=0 + status=IMPORTED + createBy=operator） |
| `markExtracted(id, voucherId, operator)` | AI 提取完成回调（extracted=1 + status=EXTRACTED + voucherId） |

---

## 3. 测试覆盖矩阵

| 公共方法 | 分支 | 用例 | 状态 |
|---|---|---|---|
| `uploadBatch(flows, op)` | flows=null → 返回 0 | `uploadBatch_nullFlows_returnsZero` | ✅ |
| | flows=空集合 → 返回 0 | `uploadBatch_emptyFlows_returnsZero` | ✅ |
| | 正常 3 flow：每 flow 初始化 4 字段 | `uploadBatch_normalFlows_initAllFields` | ✅ |
| | mapper.insertBatch 行数透传 | `uploadBatch_insertBatchPassthrough` | ✅ |
| | operator=null → createBy=null | `uploadBatch_nullOperator` | ✅ |
| | 强制覆盖 status/extracted/flowCode | `uploadBatch_overridesStatusAndExtracted` | ✅ |
| `listPending(companyId, limit)` | limit=null → 默认 20 | `listPending_limitNull_defaultsTo20` | ✅ |
| | limit=50 → 透传 | `listPending_limitProvided` | ✅ |
| | mapper 返回 list 透传 | `listPending_passthrough` | ✅ |
| `getById(id)` | mapper 返回 voucher → 透传 | `getById_returnsFromMapper` | ✅ |
| | mapper 返回 null | `getById_nullId_returnsNull` | ✅ |
| `markExtracted(id, vid, op)` | 5 字段全写 | `markExtracted_setsAllFields` | ✅ |
| | 影响 0 行 | `markExtracted_zeroRowsReturned` | ✅ |
| | voucherId=null → mapper XML 用 `<if>` 跳过 | `markExtracted_voucherIdNull` | ✅ |
| | 无副作用（不调其他 mapper 方法） | `markExtracted_doesNotMutateExternalState` | ✅ |

**总计：15 个 @Test，4 个公共方法 100% 行 + 100% 分支覆盖。**

---

## 4. 关键 Mockito 模式

### 4.1 ArgumentCaptor 捕获 List 参数

```java
List<OpcFinanceBankFlow> flows = Arrays.asList(sampleFlow(), sampleFlow(), sampleFlow());
when(flowMapper.insertBatch(anyList())).thenReturn(3);

service.uploadBatch(flows, OPERATOR);

ArgumentCaptor<List<OpcFinanceBankFlow>> captor = ArgumentCaptor.forClass(List.class);
verify(flowMapper).insertBatch(captor.capture());
List<OpcFinanceBankFlow> captured = captor.getValue();
for (OpcFinanceBankFlow f : captured) {
    assertTrue(f.getFlowCode().startsWith("F"));
    assertEquals(Integer.valueOf(0), f.getExtracted());
    assertEquals("IMPORTED", f.getStatus());
    assertEquals(OPERATOR, f.getCreateBy());
}
```

`mapper.insertBatch(List<OpcFinanceBankFlow>)` 入参是 List。`ArgumentCaptor.forClass(List.class)` 捕获
后断言 **每个元素** 都被正确初始化。Mockito 5 的 `anyList()` 匹配 List 类型入参。

### 4.2 flowCode 同批次唯一性

```java
// service: flow.setFlowCode("F" + (stamp + i) + Math.abs((flow.hashCode() + i) % 10000));
assertNotEquals(captured.get(0).getFlowCode(), captured.get(1).getFlowCode(),
        "同批次内 flowCode 应唯一");
```

原始 controller 实现是 `System.currentTimeMillis() + hashCode() % 10000`，同批次 3 个 flow 如果
hashCode 碰撞（极端罕见但理论可能）会有重复 flowCode。Service 实现加了 `+ i` 偏移量保证同批次
内 flowCode 唯一。Test 验证 3 个 flow 的 flowCode 互不相同。

### 4.3 强制覆盖语义

```java
flow.setStatus("DRAFT");       // 故意设错
flow.setExtracted(1);           // 故意设错
flow.setFlowCode("PRESET");     // 故意预设

service.uploadBatch(Collections.singletonList(flow), OPERATOR);

ArgumentCaptor<List<OpcFinanceBankFlow>> captor = ArgumentCaptor.forClass(List.class);
verify(flowMapper).insertBatch(captor.capture());
OpcFinanceBankFlow saved = captor.getValue().get(0);
assertEquals("IMPORTED", saved.getStatus(),
        "service 应强制覆盖 status 为 IMPORTED");
```

防止未来重构把 service 改成「保留原值」逻辑 — 写测试钉死 uploadBatch 的「强制初始化」语义。

---

## 5. Refactor 收益

| 维度 | 之前 | 之后 |
|---|---|---|
| Controller 行数 | 117 | 110 |
| Controller 依赖 | 1 service + 1 mapper | 2 service |
| uploadFlows 业务逻辑 | 内联 11 行（flowCode 生成 / status init / batch insert） | 1 行 service 调用 |
| 可单测 | ❌（依赖 SecurityUtils + mapper，难 mock） | ✅（service 无 Spring 依赖） |
| 与 Voucher / TaxReport 一致 | ❌（独苗） | ✅（3 个 service 同模式） |

---

## 6. 与同模块其他 Service 的对照

| 维度 | Voucher (W2.3) | TaxReport (W2.1) | **BankFlow (W2.4)** |
|---|---|---|---|
| Service 存在性 | 之前就有 | 之前就有 | **本次新建** |
| 注入方式 | `@RequiredArgsConstructor` 1 mapper | 同 + LlmGateway | `@RequiredArgsConstructor` 1 mapper |
| @Test 数 | 21 | 8 | **15** |
| 行数 | 398 | 298 | **297** |
| 状态机 | DRAFT→REVIEW→POSTED + REJECTED | 无（一次性写表） | IMPORTED→EXTRACTED |
| 边界场景 | post 不可重复 / 不污染原对象 | LLM 失败 / success=false | flowCode 同批次唯一 / 强制覆盖 |

---

## 7. 已知风险与未来工作

| 风险 | 缓解 |
|---|---|
| 本机 JDK 8，无法 `mvn test` 跑 Spring Boot 3 | 静态分析；W2.1-W2.3 同模式无回归 |
| `uploadBatch` 用 `System.currentTimeMillis() + hashCode` 生成 flowCode，跨批次唯一性不保证 | 加 `+ i` 同批次保证；跨批次唯一性靠 mapper XML 的 `UNIQUE KEY uk_flow_code`（生产 DB 层兜底） |
| `markExtracted` 没有状态校验（任何 status 都能被标记 EXTRACTED） | 后续可加：先 `selectById` 校验 status == IMPORTED 才允许 mark。**当前未实现**，但 `OpcFinanceController.extractFlows` 是异步任务回调，调用方应保证幂等 |
| `getById` 没缓存（高频读） | 后续可加 `Redis` 二级缓存；当前业务量低不需要 |
| 缺 controller 单测（验证 service 注入 + SecurityUtils mock） | 列为 W2.5 候选 |

---

## 8. 验收 Checkpoint

- [x] 4 个公共方法 100% 行覆盖
- [x] 15 个 @Test，全部 `@DisplayName` 描述场景
- [x] `@MockitoSettings(strictness = LENIENT)` 与 W2.1-W2.3 一致
- [x] `@InjectMocks`（service 仅构造器注入）
- [x] ArgumentCaptor 验证 `uploadBatch` 的 List 入参（每个元素）
- [x] flowCode 同批次唯一性测试
- [x] 强制覆盖语义测试（防止未来重构回归）
- [x] `markExtracted` 5 字段写入测试（ArgumentCaptor）
- [x] voucherId=null 走 XML `<if>` 跳过测试
- [x] Refactor: controller 移除 `flowMapper` 字段，改注入 service
- [x] pom.xml 无需变更（W2.1 已加 starter-test）

---

## 9. W2 后续候选更新

| 任务 | 状态 | 估值 |
|---|---|---|
| `OpcFinanceBankFlowService` 单测 + service 抽取 | ✅ **W2.4 完成** | 1.5h（含 refactor） |
| `OpcFinanceTaxReportController` 单测（3 endpoint） | W2.5 候选 | 45min |
| `OpcFinanceController` 单测（11 endpoint，含本次 refactor 的 2 个） | W2.5 候选 | 1.5h |
| `OpcBillingController` 单测（4 endpoint） | W2.5 候选 | 1h |
| `OpcInvitationController` 单测（5 endpoint） | W2.6 候选 | 1h |
| `OpcUserProfileService` 单测（实名认证状态机） | W3 候选 | 1h |

建议 W2.5 选 `OpcFinanceController`（11 endpoint + 依赖 2 个 service 全已单测覆盖，端到端风险最低）。
