import { describe, expect, it } from 'vitest'
import errorCode from '@/utils/errorCode'

describe('errorCode', () => {
  it('maps 401 to auth-failure message', () => {
    expect(errorCode['401']).toBe('认证失败，无法访问系统资源')
  })

  it('maps 403 to forbidden message', () => {
    expect(errorCode['403']).toBe('当前操作没有权限')
  })

  it('maps 404 to not-found message', () => {
    expect(errorCode['404']).toBe('访问资源不存在')
  })

  it('maps default to unknown-error message', () => {
    expect(errorCode['default']).toBe('系统未知错误，请反馈给管理员')
  })

  it('returns undefined for unmapped 500 (boundary)', () => {
    expect(errorCode['500']).toBeUndefined()
  })

  it('exports the same object identity on re-import', async () => {
    const again = (await import('@/utils/errorCode')).default
    expect(again).toBe(errorCode)
  })
})
