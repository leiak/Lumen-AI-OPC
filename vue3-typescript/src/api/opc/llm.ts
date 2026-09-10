import request from '@/utils/request'
import type { AjaxResult } from '@/types'

// ================ OPC LLM API ================

/**
 * W48.6: chat 接口支持传 provider 字段（minimax / deepseek），
 * 落到后端 OpcLlmController.scene 用于计量埋点。
 */
export function chat(messages: any[], options?: any): Promise<AjaxResult> {
  return request({ url: '/opc/llm/chat', method: 'post', data: { messages, ...options } })
}

export function listModels(): Promise<AjaxResult> {
  return request({ url: '/opc/llm/models', method: 'get' })
}

/** 模型下拉项（来自 listModels 响应里的 data 数组） */
export interface LlmModelOption {
  provider: string  // 'minimax' | 'deepseek' | 'openai'
  id: string        // 模型 ID
  name: string
  priority: number
  enabled: boolean
}
