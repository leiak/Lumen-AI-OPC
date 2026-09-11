# Task 17: Frontend — Inbox.vue + WS composable

Date: 2026-09-11

## Files Added

- `src/api/opc/notification.ts` — API module
  - `listInbox(params)` — `GET /opc/notification/inbox?page=&pageSize=`
  - `unreadCount()` — `GET /opc/notification/inbox/unread-count`
  - `markRead(id)` — `POST /opc/notification/inbox/{id}/read`
  - `markAllRead()` — `POST /opc/notification/inbox/read-all`
  - Types: `NotificationInboxItem`, `InboxPageResult`
- `src/composables/useNotificationSocket.ts` — WebSocket composable
  - `useNotificationSocket()` returns `{ connected, inbox, unread, loading, refresh, markRead, markAllRead, connect, disconnect }`
  - WS URL: `${proto}//${host}/opc/notification/ws?token=<jwt>`
  - Auto-reconnect 3s on close (guarded with single reconnect timer)
  - `onMounted` → `refresh()` + `connect()`; `onBeforeUnmount` → `disconnect()`
- `src/views/opc/inbox.vue` — full page
  - Reuses `ResponsiveTable` (existing W1 Sub-task 5.2 component) for desktop table / mobile card list
  - Header: title + 在线/离线 tag + 未读 badge + 全部已读 + 刷新
  - Columns: 标题 / 内容 / 分类 / 时间 / 状态; "标为已读" in actions slot

## Files Modified

- `src/router/index.ts` — added `/opc/inbox` route under `/opc` children
  - `name: OpcInbox`, `meta: { title: '通知中心', icon: 'bell' }`
- Sidebar menu is auto-generated from routes (`permissionStore.sidebarRouters`), so no extra change needed

## Verification

- `npm run build:prod` — pass (built in 34.63s, chunk `inbox-CbzYnaEp.js` = 4.09 kB)
- `npx vue-tsc --noEmit` — 174 pre-existing errors across the codebase (e.g. `invite.vue`, `invitations.vue`, `finance/vouchers.vue` all show `Module '"vue"' has no exported member 'ref'`); 3 errors in new composable file share this same pre-existing pattern. No NEW error categories introduced by Task 17.
- Route registered: yes
- WS auto-reconnect: 3s on close
- Mobile responsive: via `ResponsiveTable` (768px breakpoint)

## Commit

- `0c7860e` feat(notification): Task 17 — Inbox.vue + WS composable + API module

## Notes / Patterns

- API module follows `src/api/opc/user.ts` style: `request({ url, method, params/data })` returning `Promise<AjaxResult<T>>`; data accessed as `r.data`
- Notification icon uses Element Plus `'bell'` (string icon name) consistent with router meta.icon pattern
- Backend WS message format expected: `{"type":"NOTIFICATION","data":{...}}` — composable only refreshes on `type === 'NOTIFICATION'` and silently ignores other frames
