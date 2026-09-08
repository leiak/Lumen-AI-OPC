import request from '@/utils/request'

// ================ OPC 经营洞察 API (M4 INSIGHT MVP) ================
//
// Spec deviation: regenerateAdvice takes `id: number` and POSTs to
// /opc/insight/advice/{id}/regenerate (path-scoped). The plan spec line 1326
// said `?topic=cost_optimization` (query), but the actual backend (Task 9
// AdviceController) uses `{id}` path variable to identify the advice row
// being re-generated. Both the wrapper and the spec file implement the
// backend-correct contract.

// Dashboard
export function dashboard() {
  return request({ url: '/opc/insight/dashboard', method: 'get' })
}

// Alerts
export function listAlerts(limit?: number) {
  return request({ url: '/opc/insight/alerts', method: 'get', params: { limit } })
}
export function ackAlert(id: number) {
  return request({ url: `/opc/insight/alerts/${id}/ack`, method: 'post' })
}

// Daily Reports
export function listDaily(from: string, to: string, limit?: number) {
  return request({ url: '/opc/insight/daily', method: 'get', params: { from, to, limit } })
}
export function getDaily(id: number) {
  return request({ url: `/opc/insight/daily/${id}`, method: 'get' })
}
export function generateDaily(date: string) {
  return request({ url: '/opc/insight/daily/generate', method: 'post', params: { date } })
}

// Advice
export function listAdvice(limit?: number) {
  return request({ url: '/opc/insight/advice', method: 'get', params: { limit } })
}
export function getAdvice(id: number) {
  return request({ url: `/opc/insight/advice/${id}`, method: 'get' })
}
export function regenerateAdvice(id: number) {
  return request({ url: `/opc/insight/advice/${id}/regenerate`, method: 'post' })
}