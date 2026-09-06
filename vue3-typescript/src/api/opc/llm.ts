import request from '@/utils/request'
import type { AjaxResult } from '@/types'

// ================ OPC LLM API ================

export function chat(messages: any[], options?: any): Promise<AjaxResult> {
  return request({ url: '/opc/llm/chat', method: 'post', data: { messages, ...options } })
}

export function listModels(): Promise<AjaxResult> {
  return request({ url: '/opc/llm/models', method: 'get' })
}
