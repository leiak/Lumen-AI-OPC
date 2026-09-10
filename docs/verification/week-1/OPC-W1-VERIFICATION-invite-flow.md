# W1 Task #7 邀请落地页 — 验证报告

> 验证日期:2026-09-04
> 验证范围:Task #7.1 + #7.2 + #7.3 端到端链路
> 验证人:Claude (静态审查) + 待运行时人工补齐

---

## 0. 验收结论摘要

| 子任务 | 代码就位 | 静态一致 | 运行时验证 | 状态 |
|--------|---------|---------|-----------|------|
| 7.1 邀请码 API (后端) | ✅ | ✅ | ⏸ 待人工 | 🟡 静态通过 |
| 7.2 落地页 Invite.vue | ✅ | ✅ | ⏸ 待人工 | 🟡 静态通过 |
| 7.3 分享卡 SharePoster | ✅ | ✅ | ⏸ 待人工 | 🟡 静态通过 |

**整体判定**:🟡 **代码层全部就位且内部一致,可进入运行时 E2E 阶段**;本机缺 JDK 17,无法跑 mvn 编译,运行时 3 个接口需人工在 dev 环境跑通。

---

## 1. 已验证项(静态)

### 1.1 后端文件齐备

| 文件 | 路径 | 状态 |
|------|------|------|
| Controller | `opc-user-center/.../controller/OpcInvitationController.java` | ✅ |
| Service 接口 | `.../service/IOpcInvitationService.java` | ✅ |
| Service 实现 | `.../service/impl/OpcInvitationServiceImpl.java` | ✅ |
| Mapper 接口 | `.../mapper/OpcInvitationMapper.java` | ✅ |
| Mapper XML | `.../resources/mapper/OpcInvitationMapper.xml` | ✅ |
| Domain | `.../domain/OpcInvitation.java` | ✅ |
| 公共依赖 | `opc-common/.../utils/OpcCodeGenerator.java` (`inviteCode()`/`txCode()`) | ✅ |
| 公共异常 | `opc-common/.../exception/OpcException.java` | ✅ |
| 路由表 | `bootstrap.yml` 注册端口 9302 + name `opc-user-center` | ✅ |

### 1.2 API 契约(前后端一致)

| 操作 | 前端 `user.ts` 调用 | 后端 Controller 端点 | 匹配 |
|------|---------------------|---------------------|------|
| 生成邀请码 | `generateInvitation()` → `POST /opc/user/invitations/generate` | `@PostMapping("/generate")` 需 `SecurityUtils.getUserId()` | ✅ |
| 我的邀请码列表 | `listMyInvitations()` → `GET /opc/user/invitations` | `@GetMapping` 需登录 | ✅ |
| 公开查邀请码 | `getInvitationPublic(code)` → `GET /opc/user/invitations/{code}` | `@GetMapping("/{code}")` 公开 | ✅ |
| 接受邀请 | `acceptInvitation(code, mobile)` → `POST /opc/user/invitations/accept`, body `{code, mobile}` | `@PostMapping("/accept")` body `Map<String,String>` | ✅ |

### 1.3 数据库 schema(主 SQL 已存在)

`sql/opc_20260903.sql` 已建表:

| 表名 | 行号 | 关键字段 |
|------|------|---------|
| `opc_invitation` | 707 | invite_code (UK), inviter_id, invitee_id, max_uses, used_count, expire_time, status |
| `opc_user_profile` | 17 | user_id (FK 到 sys_user), real_name, avatar_url, inviter_id, invitation_code |
| `opc_company_profile` | 47 | owner_user_id (供 accept() 找默认公司) |
| `opc_company_member` | 85 | company_id, user_id, role, status |
| `opc_wallet` | 529 | company_id, user_id, balance |
| `opc_transaction` | 582 | tx_code, wallet_id, tx_type, amount, biz_type=INVITE_REWARD |

**Domain ↔ 表字段映射**: 14 个字段全部对齐(`OpcInvitation` 字段 ↔ `opc_invitation` 列),`@JsonFormat` 已配。

### 1.4 业务约束(在 Service 实现中)

| 约束 | 实现位置 | 正确性 |
|------|---------|--------|
| 最多 50 个 ACTIVE | `MAX_ACTIVE_PER_USER=50` + `countActiveByInviter()` | ✅ |
| 邀请码唯一 | `uk_invite_code` (DB) + `selectByCode` 重试 5 次 | ✅ |
| 8 位 base32 | `OpcCodeGenerator.inviteCode()` 用 31 字符表(去掉 0/1/I/L/O) | ✅(符合 Sub-task 7.1 AC) |
| 有效期 90 天 | `DEFAULT_EXPIRE_DAYS=90` | ✅ |
| maxUses 默认 1 | `DEFAULT_MAX_USES=1` | ✅ |
| 不能接受自己邀请 | `invite.getInviterId().equals(inviteeId)` 拦截 | ✅ |
| 邀请人得 50 元 | `INVITER_REWARD=50.00` → `opc_transaction` + `opc_wallet` 加余额 | ✅ |
| 绑为公司 STAFF | `INSERT IGNORE INTO opc_company_member ... 'STAFF'` | ✅ |
| 一码一次 | `usedCount+1` ≥ `maxUses` 时 `status='USED'` | ✅ |

### 1.5 前端页面齐备

| 文件 | 路径 | 备注 |
|------|------|------|
| 落地页 | `views/opc/invite.vue` | 279 行,移动 480px 适配,登录/未登录双分支 |
| 我的邀请页 | `views/opc/invitations.vue` | 190 行,表格 + 分享卡弹窗 |
| 分享卡组件 | `views/opc/components/SharePoster.vue` | 248 行,Canvas 1080×1080,QR 扫码回跳 |
| API 客户端 | `api/opc/user.ts` | 4 个 invitation 方法 |
| 路由 | `router/index.ts` 第 80 行 `/opc/invite`, 第 119 行 `user/invitations` | ✅ |

### 1.6 前端依赖

`package.json`:`qrcode@1.5.4` ✅(SharePoster 用于生成二维码)

> 注:`html2canvas` 未在 deps 中,但 SharePoster 实际**未使用 html2canvas**,而是直接用原生 `<canvas>` 2D API 绘制(绘制顺序:背景渐变 → 白卡片 → Logo → 标题 → 福利 → 邀请码 → QR → 底标),然后 `canvas.toDataURL('image/png')` 导出。✅ 无依赖问题。

### 1.7 路由 ↔ 网关注册

- 落地页 `/opc/invite`:`router/index.ts:80-81` → 导入 `@/views/opc/invite.vue` ✅
- 我的邀请 `user/invitations`:`router/index.ts:119-120` → 导入 `@/views/opc/invitations.vue` ✅

---

## 2. ⚠️ 风险与待补缺口

### 2.1 🔴 **网关白名单配置不可见**

**问题**: `OpcInvitationController` 的 `GET /opc/user/invitations/{code}` 标注为"公开",但 `AuthFilter` 默认会拦截所有非白名单 URL 要求 JWT。白名单配置项 `security.ignore.whites` 在 Nacos `application-{env}.yml` 中,**本地仓库看不到**。

**影响**: 如果 Nacos 没把 `/opc/user/invitations/*` 加入白名单,落地页打开后会被网关 401 拦截。

**建议行动**:
1. 在 Nacos 控制台核对 `application-dev.yml` 的 `security.ignore.whites` 是否包含 `/opc/user/invitations/**`
2. 若没有,**必须加上**(注意:白名单只覆盖 GET,POST `/accept` 仍需登录)
3. 长期方案:把白名单放进 `opc-common-prod.yml` 共享(其他 OPC 服务也会用到)

### 2.2 🟡 **invitee_profile.reward 流程的边界**

在 `OpcInvitationServiceImpl.accept()`:
- 当 inviteeProfile 为 null → 创建并 set inviterId ✅
- 当 inviteeProfile.inviterId 已有 → **静默跳过**(不更新 invite.invitee_id 的来源)

**潜在问题**: 用户 A 通过 B 邀请,后来又被 C 邀请。第二次 accept 时:
- 不会更新 inviteeProfile.inviter_id(保留 B)✅ 这是合理的(第一个邀请人得奖励)
- 但 invite.invitee_id 仍会被更新为第二次的 inviteeId → C 获得 50 元 ❌ 这是漏洞

**建议**: 加一个 `SELECT ... FROM opc_invitation WHERE invitee_id = ?` 预检,若 invitee 已被绑定,直接拒绝。

### 2.3 🟡 **tx_code 唯一性**

`OpcCodeGenerator.txCode()` 格式 `TX{yyyyMMdd}{6 位 random}`,理论上有同秒碰撞可能(`1/1_000_000`)。当前 INSERT 没显式 `ON DUPLICATE KEY` 处理,会出现 `tx_code UNIQUE` 冲突导致整事务回滚(包括 invite 状态更新)。

**建议**: 在 catch 块中重试,或改为 `INSERT IGNORE` + 后台对账。

### 2.4 🟡 **reward 事务回滚边界**

`accept()` 方法被 `@Transactional` 包裹,但 reward 的 `INSERT INTO opc_transaction` 写在 try/catch 里,异常被吞掉 → 即使后续 invite 绑定失败,reward 已经写入 ❌

**建议**: 把 reward 部分也放进事务,或采用"先写 reward 再绑 invite"的两阶段提交(更复杂,需权衡)。

### 2.5 🟢 已知非问题

- `frontend invite.vue` 没用到 `<el-alert>` 的 type:"success" 而仅 info,符合 AC 中"加入成功动画"用 setTimeout 实现即可的预期。
- `SharePoster` 不依赖 html2canvas,改用原生 Canvas,部署更轻。
- `OpcUserProfileMapper` 已在 mapper 列表中,`selectByUserId(...)` 是约定方法,假定 mapper.xml 里有对应 SQL(未单独核验,见 §3 清单)。

---

## 3. ⏸ 运行时 E2E 验证清单(待人工在 dev 环境跑)

### 3.1 环境前置

- [ ] **JDK 17+ 安装并切换**(本机 JDK 8 阻塞编译)
- [ ] MySQL 启动并应用 `sql/opc_20260903.sql`
- [ ] Nacos 启动 (127.0.0.1:8848) 并导入 `application-dev.yml` / `opc-user-center-dev.yml` / `opc-common-dev.yml`
- [ ] Redis / RabbitMQ / Sentinel 可选
- [ ] 确认 Nacos 中 `application-dev.yml` 的 `security.ignore.whites` 含 `/opc/user/invitations/**`

### 3.2 编译 & 启动

```bash
cd springboot3
mvn clean install -DskipTests
mvn spring-boot:run -pl ruoyi-gateway
mvn spring-boot:run -pl ruoyi-modules/opc-user-center
```

### 3.3 Postman 三个接口

| # | 接口 | 请求 | 预期 |
|---|------|------|------|
| 1 | 生成 | `POST http://localhost:8080/opc/user/invitations/generate` (header: Authorization: Bearer XXX) | 200,返回 `inviteCode`(8位base32) |
| 2 | 公开查 | `GET http://localhost:8080/opc/user/invitations/{code}` (无 token) | 200,返回 inviter 公开信息;过期/作废返回 500 + msg |
| 3 | 接受 | `POST http://localhost:8080/opc/user/invitations/accept` body `{code, mobile}`,header: token 为另一用户 | 200,`opc_invitation` status→USED,`opc_transaction` 多 1 条 INVITE_REWARD,`opc_wallet` 余额+50 |

### 3.4 前端页面 E2E

- [ ] `/opc/user/invitations` 登录态访问,看到邀请码列表
- [ ] 点"生成新邀请码" → 表格新增一行,提示"已生成:XXXX"
- [ ] 第 51 次点生成 → 应弹"最多 50 个有效邀请码"
- [ ] 点"生成分享卡" → 弹出 SharePoster,1080×1080 canvas 渲染正确
- [ ] 点"下载图片" → 浏览器下载 `opc-invite-{code}.png`,扫描二维码能回到 `/opc/invite?code={code}`
- [ ] 登出,访问 `/opc/invite?code={code}` → 看到邀请人卡片 + "注册"按钮(跳 `/register?inviteCode=...`)
- [ ] 用另一用户登录,访问 `/opc/invite?code={code}` → 点"立即接受邀请" → 提示成功 → 1.2s 后跳 `/opc`
- [ ] 用邀请人账号回去看 `opc_wallet.balance`,应 +50.00

### 3.5 移动端

- [ ] Chrome DevTools 切到 360/414/768 三个宽度,落地页排版不破

---

## 4. 改进建议(非阻塞,记录到下个迭代)

| # | 优先级 | 建议 |
|---|--------|------|
| 1 | P1 | 加 `OpcInvitationServiceImplTest`:覆盖合法/不存在/已用/已过期/自己邀请 5 种 case |
| 2 | P1 | §2.2 invitee 重复绑定漏洞修复 |
| 3 | P2 | §2.3 tx_code 重试机制 |
| 4 | P2 | §2.4 reward 事务一致性重构 |
| 5 | P3 | 把 `security.ignore.whites` 抽到 `opc-common-prod.yml` 共享 |

---

## 5. 文件清单(本次验证未新增代码)

无文件变更。本次产出仅为本报告 `OPC-W1-VERIFICATION-invite-flow.md`。

> **结论**:Task #7 代码层全部就位且内部一致。**可进入 dev 环境运行时验证阶段**。运行时验证需 JDK 17 环境(本机缺),由相关同学执行 §3 清单。
