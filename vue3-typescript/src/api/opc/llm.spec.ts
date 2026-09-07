// W12.4 — api/opc/llm.spec.ts.
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import { chat, listModels } from '@/api/opc/llm'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200 } as any)
})

describe('api/opc/llm', () => {
  it('1. chat([{role:"user",content:"hi"}]) -> POST /opc/llm/chat data={messages}', () => {
    chat([{ role: 'user', content: 'hi' }])
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/llm/chat',
      method: 'post',
      data: { messages: [{ role: 'user', content: 'hi' }] },
    })
  })

  it('2. chat(messages,{model:"gpt-4"}) -> data spreads options into envelope', () => {
    chat([{ role: 'user', content: 'hi' }], { model: 'gpt-4' })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/llm/chat',
      method: 'post',
      data: { messages: [{ role: 'user', content: 'hi' }], model: 'gpt-4' },
    })
  })

  it('3. chat(messages,{model:"x",temperature:0.7}) -> all options spread', () => {
    chat([{ role: 'user', content: 'hi' }], { model: 'x', temperature: 0.7 })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/llm/chat',
      method: 'post',
      data: { messages: [{ role: 'user', content: 'hi' }], model: 'x', temperature: 0.7 },
    })
  })

  it('4. listModels() -> GET /opc/llm/models', () => {
    listModels()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/llm/models',
      method: 'get',
    })
  })

  it('5. return value is the request() promise', () => {
    const sentinel = Symbol('p')
    requestMock.mockReturnValue(sentinel as any)
    expect(listModels()).toBe(sentinel)
  })
})