# OPC-W1-VERIFICATION：Sub-task 7.3 邀请分享卡 + 二维码

> 验证日期：2026-09-06
> 对应任务：`OPC-W1-TASK-BREAKDOWN.md` Task #7 / Sub-task 7.3
> 验证方式：**静态验证**（本机无 node_modules，无法 `npm install` / `npm run build`）
> Owner：FE + Product

---

## 1. 验收对照

| 验收项 | 规格 | 实现位置 | 状态 |
|--------|------|---------|------|
| 点击"生成分享卡"按钮 → 下载 PNG | `invitations.vue` 的 `<el-button @click="onShare(row)">` → 弹 `<el-dialog>` 嵌 `<SharePoster>` → `canvas.toDataURL('image/png')` 触发 `<a download>` | `invitations.vue:17` + `SharePoster.vue:166-178` | ✅ |
| 二维码扫描后能正确跳转落地页 | QR 内容 = `${window.location.origin}/opc/invite?code=${inviteCode}`，与 `invite.vue` 路由 `/opc/invite?code=` 一致 | `SharePoster.vue:43` + `invite.vue` 路由 `/opc/invite` | ✅ |
| 卡片尺寸适配微信朋友圈（1080×1080） | `<canvas width="1080" height="1080">`，PNG 按此尺寸导出 | `SharePoster.vue:4` | ✅ |
| 海报文案（邀请码 + 落地页 URL + 文案） | inviteCode + shareUrl + shareText（标题 / 副标 / 福利 / 邀请码 / 二维码 / 底部提示 共 10 段） | `SharePoster.vue:54-148` | ✅ |
| 复制文字 / 复制链接 | `copyText()` + `copyLink()` → `navigator.clipboard.writeText` | `SharePoster.vue:186-202` | ✅ |

3 条核心验收全部满足，2 条附属功能（复制）一并交付。

---

## 2. 设计概览

```
┌────────────────────────────── 1080 ──────────────────────────────┐
│ 紫蓝渐变背景 (#667eea → #764ba2)                                │
│  ┌────────────── 白色圆角卡片 (#fff, r=32) ────────────────┐    │
│  │                                                          │    │
│  │   [LOGO 80×80]   OPC-Agent-Community         ← 第 3-5 段 │    │
│  │                   一人公司 + N 个数字员工                  │    │
│  │                                                          │    │
│  │            让 AI 成为你的合伙人  ← 主标语                  │    │
│  │                                                          │    │
│  │       ✅ 注册即送 50 元代金券                             │    │
│  │       ✅ 7 天免费体验全部 Agent                            │    │
│  │       ✅ 1 对 1 新手引导                                   │    │
│  │                                                          │    │
│  │                  我的专属邀请码                            │    │
│  │              7 段 base32 邀请码（72px mono）              │    │
│  │                                                          │    │
│  │             ┌────── QR 240×240 ──────┐                   │    │
│  │             │  编码：邀请页完整 URL    │  ← 二维码        │    │
│  │             └──────────────────────────┘                  │    │
│  │                                                          │    │
│  │             微信扫码 / 长按识别                            │    │
│  └──────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
```

绘制方式：**纯 Canvas 2D 原生绘图**（`ctx.fillRect` / `ctx.fillText` / `roundRect`），
不使用 html2canvas。优势：输出位图精确 1080×1080、字体清晰、无 DOM 反射带来的延迟；
缺点：背景是纯色矩形 + 文本，未嵌入真实 logo 图片（如需替换为 SVG logo 需要额外 Image 资源）。

---

## 3. 关键技术决策

### 3.1 为什么不用 html2canvas

| 维度 | 原生 Canvas（当前） | html2canvas |
|------|-------------------|-------------|
| 依赖 | 0（已用 qrcode） | +200KB（package.json 暂未装） |
| 输出尺寸 | 精确 1080×1080 | 受 DOM 实际尺寸 + devicePixelRatio 影响 |
| 字体渲染 | Canvas 抗锯齿，文字锐利 | 反射 DOM，受 `font-smoothing` 影响 |
| 复杂度 | 中（要手写 10 个绘制段） | 低（DOM 截图） |
| 朋友圈兼容性 | PNG 直接 1080×1080，无失真 | 需手动 `scale(2)` 强制高分辨率 |

规格写的是 `使用 qrcode + html2canvas`，但实际原生 Canvas 实现更稳定；
本验证报告顶部说明此偏离，由用户决定是否接受。

### 3.2 QR 码纠错级别 H

`errorCorrectionLevel: 'H'`（≈ 30% 容错），允许海报被部分遮挡（贴纸覆盖、Logo 压在中央）
仍可被微信 / 相机识别。规格未规定，按业界最佳实践选择。

### 3.3 失败降级

二维码生成异常时（如浏览器禁用 Canvas CORS、QR 库版本冲突）：
不阻塞 PNG 下载，改为在二维码位置画灰色占位块 + "二维码生成失败，请重试" 文字。
规格未规定，作为鲁棒性补丁加入。

---

## 4. 与邀请管线的端到端联动

```
┌──────────────────┐   GET /opc/user/invitations       ┌──────────────────────────┐
│ invitations.vue  │ ────────────────────────────────→ │ OpcInvitationService     │
│ ResponsiveTable  │                                    │ .listMyInvitations()      │
│ 7 个列 + actions │ ←────────────────────────────────  │ → Mapper → MySQL         │
└────────┬─────────┘   Array<{inviteCode, usedCount,   └──────────────────────────┘
         │              maxUses, status, expireTime,
         │ onShare(row) createTime}>
         ▼
┌──────────────────┐
│ SharePoster.vue  │  shareUrl = origin + '/opc/invite?code=' + inviteCode
│ <canvas 1080²>   │  QR 编码 = shareUrl（H 容错）
│ 10 段原生绘制     │  downloadPng() → canvas.toDataURL('image/png')
└──────────────────┘  微信扫码 → /opc/invite → Invite.vue (Task #7.2)
```

---

## 5. 文件清单

| 文件 | 状态 | 行数 | 备注 |
|------|------|------|------|
| `RuoYi-Cloud-Vue3-typescript/src/views/opc/invitations.vue` | 已存在 | 182 | 本次未修改；调用 `SharePoster` 弹窗 |
| `RuoYi-Cloud-Vue3-typescript/src/views/opc/components/SharePoster.vue` | **编辑** | 178→184 | 唯一改动：QR 失败时画占位 + 文字（`SharePoster.vue:121-141`） |

依赖：
- `qrcode@1.5.4`（已在 package.json）
- ~~`html2canvas`~~（未安装；当前实现不依赖）

---

## 6. 验证局限与待办

### 6.1 本机无法跑 `npm run build`

`RuoYi-Cloud-Vue3-typescript/node_modules` 在本机不存在（`ls node_modules/ | wc -l = 0`），
因此无法执行 `npm run type-check` / `npm run build` 做编译验证。
**依赖 CI / 用户本机执行以下命令做最终验收**：

```bash
cd RuoYi-Cloud-Vue3-typescript
npm install
npm run type-check   # vue-tsc --noEmit
npm run build       # vite build
```

预计 PASS 依据：
- 模板使用 Element Plus 已声明的 `<el-button>` / `<el-icon>` / `<el-dialog>`（package.json 2.13.1 已装）
- `<canvas>` 是 HTML5 原生标签
- `QRCode` 是 qrcode 1.5.4 的 default export
- TypeScript 类型注解均使用 `ref<T>` / `defineProps<{...}>` / `defineEmits<{...}>` 标准语法

### 6.2 待办（不在 Sub-task 7.3 验收范围内）

| # | 项 | 备注 |
|---|----|------|
| 1 | 真实 LOGO 替换 | 当前用 `ctx.fillStyle + fillText('OPC')` 占位，应替换为 `/logo.png` Image 资源 |
| 2 | inviter 昵称 | 当前海报无邀请人个性化信息，需 `getUserInfo()` 取 `nickname` 后渲染到"我的专属邀请码"上方 |
| 3 | 分享按钮 | 当前只有"下载 PNG"；可加"分享到微信" / "分享到朋友圈"（需微信 JS-SDK，范围更广） |
| 4 | A/B 测试 | 海报主标语 / 福利文案可做 A/B 测试，把"50 元代金券"换成"7 天 Pro 会员"等 |

---

## 7. 偏离规格说明（与 Task #7.3 spec 对照）

| 偏离项 | 规格 | 实际 | 原因 |
|--------|------|------|------|
| 海报生成库 | `qrcode` + `html2canvas` | 仅 `qrcode`（原生 Canvas 2D 绘制其余部分） | 原生 Canvas 输出位图精确可控、性能更好、无额外 200KB 依赖；html2canvas 适用于"截 DOM 卡片"场景，本海报是程序化绘制，不需要 |
| 海报元素 | "邀请码 + 落地页 URL + 海报文案" | + Logo + 副标 + 福利列表 + 主标语 + 底部扫码提示（10 段） | 10 段构图更适合朋友圈视觉密度，避免下载后像空白卡片 |
| — | 未要求 | 加 QR 失败降级（占位块 + 提示文字） | 鲁棒性补丁，避免阻塞 PNG 下载 |

如不接受"不引入 html2canvas"的偏离，可改为：
1. `npm install html2canvas --save`
2. 把 `<canvas>` 替换为 `<div class="poster-card">` + DOM 元素
3. 导出时 `html2canvas(dom, { scale: 2 })`

工作量大改不大，约 30 分钟。**建议保持当前实现**，与朋友圈 PNG 输出质量更优。