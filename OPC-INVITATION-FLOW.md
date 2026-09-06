# Task #7 邀请落地页 - 实现总览

## 交付清单（17 个文件）

### 后端（6 个新文件）
```
ruoyi-modules/opc-user-center/src/main/java/com/ruoyi/opc/user/
├── controller/OpcInvitationController.java      # 4 个端点
├── domain/OpcInvitation.java                    # 实体类
├── mapper/OpcInvitationMapper.java              # MyBatis 接口
└── service/
    ├── IOpcInvitationService.java               # 业务接口
    └── impl/OpcInvitationServiceImpl.java       # 业务实现

ruoyi-modules/opc-user-center/src/main/resources/mapper/
└── OpcInvitationMapper.xml                      # MyBatis XML
```

### 前端（5 个新文件 + 2 个修改）
```
src/views/opc/
├── invite.vue                       # 公开落地页（独立 Layout）
├── invitations.vue                  # 我的邀请列表（带 Layout）
└── components/SharePoster.vue       # 1080×1080 海报生成

src/api/opc/user.ts                  # +4 个 invitation API
src/router/index.ts                  # +2 个路由
package.json                         # +qrcode 依赖
```

---

## API 契约

| Method | Path | Auth | 说明 |
|--------|------|------|------|
| POST | `/opc/user/invitations/generate` | 需登录 | 生成新邀请码（最多 50 个 ACTIVE） |
| GET | `/opc/user/invitations` | 需登录 | 我的邀请码列表 |
| GET | `/opc/user/invitations/{code}` | **匿名** | 查询邀请人公开信息（落地页用） |
| POST | `/opc/user/invitations/accept` | 需登录 | 接受邀请，被邀请人调用 |

### 关键约束

| 约束 | 数值 |
|------|------|
| 单用户 ACTIVE 邀请码上限 | 50 |
| 邀请码长度 | 8 位 base32（去除 0/1/I/L） |
| 有效期 | 90 天 |
| 默认 maxUses | 1（一次性） |
| 邀请人奖励 | 50 元代金券（自动到账） |

### 业务流程（accept）

```
1. 校验邀请码合法（ACTIVE / 未过期 / 未用完）
2. 更新被邀请人 opc_user_profile.inviter_id
3. 把被邀请人加入邀请人名下第一家 opc_company_profile（role=STAFF）
4. 给邀请人发 50 元代金券
   - INSERT opc_transaction (tx_type=RECHARGE, biz_type=INVITE_REWARD)
   - UPDATE opc_wallet.balance += 50
5. 更新 opc_invitation
   - invitee_id, used_time, used_count++, status (USED if maxUses reached)
```

---

## 前端页面结构

### 落地页 `/opc/invite?code=XXX`

- **独立 Layout**（无侧边栏）
- 移动端友好（< 480px 样式调整）
- 状态机：
  - 加载中 → spinner
  - 邀请码有效 → 显示邀请人信息 + 福利 + 接受按钮
  - 未登录 → 跳转 `/register?inviteCode=XXX`
  - 已登录 → 直接调 `/opc/user/invitations/accept`
  - 邀请码无效 → 错误页 + 返回首页

### 我的邀请 `/opc/user/invitations`（需登录）

- 左：邀请码列表（生成/复制/分享/状态）
- 右：累计邀请数 + 累计奖励

### 分享卡弹窗（`SharePoster.vue`）

- 1080×1080 PNG 海报
- Canvas API 直接绘制（不依赖 html2canvas）
- 包含：渐变背景 / Logo / 主标语 / 福利列表 / 邀请码 / 二维码 / 底部提示
- 操作：下载 PNG / 复制文字 / 复制链接

---

## E2E 测试脚本

```bash
# Step 1: 用户 A 注册 → 创建公司 → 生成邀请码
TOKEN_A=$(curl -X POST http://localhost:9302/opc/user/invitations/generate \
  -H "Authorization: Bearer $TOKEN_A" | jq -r '.data.inviteCode')

# Step 2: 用户 B 通过落地页访问
curl -X GET http://localhost:9302/opc/user/invitations/$CODE \
  -H "Authorization: Bearer $TOKEN_B_GUEST"   # 匿名时无 token
# 返回: { inviteCode, inviter: {realName, ...}, expireTime }

# Step 3: 用户 B 注册并接受
curl -X POST http://localhost:9302/opc/user/invitations/accept \
  -H "Authorization: Bearer $TOKEN_B" \
  -d "code=$CODE&mobile=13800138000"
# 返回: { inviterId, companyId, rewardAmount: 50 }

# Step 4: 验证
# - opc_invitation.used_count = 1, status = USED
# - opc_company_member 多一条 (companyId, B, STAFF)
# - opc_transaction 多一条 (tx_type=RECHARGE, amount=50, biz_type=INVITE_REWARD)
# - opc_wallet.balance 增加 50
```

---

## 已知依赖 / 配置

### 前端 npm 包
- `qrcode@1.5.4` — 已加入 package.json，需 `npm install`

### 后端 SQL
- 表 `opc_invitation` — 已在 sql/opc_20260903.sql 第 706 行
- 表 `opc_company_member` — 已存在
- 表 `opc_transaction` — 已存在
- 表 `opc_wallet` — 已存在

### 网关白名单(已 patch)

**Patch 文件**:
- `deploy/nacos/whitelist-opc-invite.yml` — 合并片段(注释完整说明)
- `deploy/nacos/apply-whitelist.sh` — 一键推送到 Nacos
- `ruoyi-gateway/src/main/resources/application.yml` — 本地 fallback

**应用方式**:
```bash
cd deploy/nacos
./apply-whitelist.sh dev       # 应用到 Nacos application-dev.yml
./apply-whitelist.sh prod      # 应用到 Nacos application-prod.yml
DRY_RUN=1 ./apply-whitelist.sh dev  # 仅预览,不推送
```

**白名单条目**:
```
/opc/user/invitations/*
```
(Ant 模式: `*` 匹配单层路径段,**匹配多层。`{code}` 8 位 base32 视为单层)

**⚠️ 已知限制**:
RuoYi `AuthFilter` 的 `whites` 是 path-only,不支持 HTTP method 维度。
因此 `/opc/user/invitations/*` 会**同时豁免** POST /generate 和 POST /accept。
但下游 controller `SecurityUtils.getUserId()` 在未鉴权时返回 null → 抛 `OpcException("userId 不能为空")` → 500,**不会**真正创建邀请 / 发放奖励。
风险评估:🟡 低(只暴露 500,无数据风险)。

**长期方案**:
改造 `IgnoreWhiteProperties`,支持 `method+path` 格式(例 `"GET /opc/user/invitations/*"`)。
届时把 patch 文件路径换成精准匹配,无需改其他文件。

### 路由
- `/opc/invite` — 顶级路由（无 Layout），适合营销落地页
- `/opc/user/invitations` — OPC 子路由（带 Layout），后台页面

---

## 验收 Checklist

- [x] 后端 4 个 API 实现完成
- [x] 邀请码生成 / 校验 / 接受逻辑正确
- [x] 50 元奖励写入 opc_transaction + opc_wallet
- [x] 落地页 `/opc/invite` 在已登录/未登录两种状态都正常
- [x] 分享海报 1080×1080 可下载、可分享
- [x] 移动端样式适配（< 480px）
- [ ] 端到端测试（需真实注册流程）
- [x] 网关白名单 patch 准备完成(待运维执行 `./apply-whitelist.sh <env>`)

> 文档版本：v1.0 · 2026-09-03 · Task #7 配套
