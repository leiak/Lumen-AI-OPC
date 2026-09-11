import request from '@/utils/request'
import type { AjaxResult } from '@/types'

// ================ OPC 通知中心 API (Task 17) ================

export interface NotificationInboxItem {
  id: number
  userId: number
  title: string
  content: string
  category: string
  refId?: string
  readFlag: 0 | 1
  readTime?: string
  createTime: string
}

export interface InboxPageResult {
  items: NotificationInboxItem[]
  total: number
  unreadCount: number
  page: number
  pageSize: number
}

/** 站内信分页 */
export function listInbox(params: { page: number; pageSize: number }): Promise<AjaxResult<InboxPageResult>> {
  return request({ url: '/opc/notification/inbox', method: 'get', params })
}

/** 未读数量 */
export function unreadCount(): Promise<AjaxResult<number>> {
  return request({ url: '/opc/notification/inbox/unread-count', method: 'get' })
}

/** 标记已读 */
export function markRead(id: number): Promise<AjaxResult<void>> {
  return request({ url: `/opc/notification/inbox/read/${id}`, method: 'post' })
}

/** 全部标记已读 */
export function markAllRead(): Promise<AjaxResult<void>> {
  return request({ url: '/opc/notification/inbox/read-all', method: 'post' })
}
