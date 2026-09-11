import { ref, onMounted, onBeforeUnmount } from 'vue'
import { getToken } from '@/utils/auth'
import {
  listInbox,
  markRead as apiMarkRead,
  markAllRead as apiMarkAllRead,
  type NotificationInboxItem,
} from '@/api/opc/notification'

/**
 * OPC 通知 WS Composable (Task 17)
 *
 * <p>连接到后端 `/opc/notification/ws?token=<jwt>`,收到 `NOTIFICATION`
 * 消息后自动刷新分页 + 未读计数。close 后 3s 自动重连。
 */
export function useNotificationSocket() {
  const connected = ref(false)
  const inbox = ref<NotificationInboxItem[]>([])
  const unread = ref(0)
  const loading = ref(false)

  let ws: WebSocket | null = null
  let reconnectTimer: number | null = null

  async function refresh() {
    loading.value = true
    try {
      const r: any = await listInbox({ page: 1, pageSize: 50 })
      const data = r?.data
      if (data) {
        inbox.value = Array.isArray(data.items) ? data.items : []
        unread.value = Number(data.unreadCount || 0)
      }
    } catch (e) {
      console.error('Failed to load inbox', e)
    } finally {
      loading.value = false
    }
  }

  function connect() {
    if (ws) return
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    const token = getToken() || ''
    const url = `${proto}//${host}/opc/notification/ws?token=${encodeURIComponent(token)}`
    try {
      ws = new WebSocket(url)
    } catch (e) {
      console.error('WS construct error', e)
      scheduleReconnect()
      return
    }
    ws.onopen = () => {
      connected.value = true
    }
    ws.onclose = () => {
      connected.value = false
      ws = null
      scheduleReconnect()
    }
    ws.onmessage = async (evt) => {
      try {
        const msg = JSON.parse(evt.data)
        if (msg && msg.type === 'NOTIFICATION') {
          await refresh()
        }
      } catch (e) {
        console.error('WS message parse error', e)
      }
    }
    ws.onerror = (e) => {
      console.error('WS error', e)
    }
  }

  function scheduleReconnect() {
    if (reconnectTimer != null) return
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null
      connect()
    }, 3000)
  }

  function disconnect() {
    if (reconnectTimer != null) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (ws) {
      ws.close()
      ws = null
    }
    connected.value = false
  }

  async function markRead(id: number) {
    try {
      await apiMarkRead(id)
      await refresh()
    } catch (e) {
      console.error('markRead failed', e)
      throw e
    }
  }

  async function markAllReadFn() {
    try {
      await apiMarkAllRead()
      await refresh()
    } catch (e) {
      console.error('markAllRead failed', e)
      throw e
    }
  }

  onMounted(() => {
    refresh()
    connect()
  })

  onBeforeUnmount(() => {
    disconnect()
  })

  return {
    connected,
    inbox,
    unread,
    loading,
    refresh,
    markRead,
    markAllRead: markAllReadFn,
    connect,
    disconnect,
  }
}
