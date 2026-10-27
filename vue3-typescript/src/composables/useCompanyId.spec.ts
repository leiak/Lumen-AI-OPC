// useCompanyId —— 前端「当前租户」的唯一取值口。
// 断言的是「取登录态里的 companyId，而不是硬编码 1」。
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/login', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  getInfo: vi.fn(),
}))
vi.mock('@/api/opc/user', () => ({
  listMyCompanyMembers: vi.fn(),
  switchCompany: vi.fn(),
}))

import useUserStore from '@/store/modules/user'
import { useCompanyId } from '@/composables/useCompanyId'

describe('useCompanyId', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns the companyId from the login state (not a hardcoded 1)', () => {
    const store = useUserStore()
    store.companyId = 7
    expect(useCompanyId()).toBe(7)
  })

  it('falls back to 0 (无归属) when the login state carries no company', () => {
    const store = useUserStore()
    store.companyId = 0
    expect(useCompanyId()).toBe(0)
  })

  it('never silently pretends the user belongs to the demo company 1', () => {
    // 兜底成 1 会把「没关联企业」和「在示例一人公司」混成同一件事：
    // 前端只有这一个数字，没有第二个来源能分辨两者。
    const store = useUserStore()
    store.companyId = 0
    expect(useCompanyId()).not.toBe(1)
  })

  it('is a plain number snapshot — a later store change does not mutate it', () => {
    const store = useUserStore()
    store.companyId = 3
    const snapshot = useCompanyId()
    store.companyId = 4
    expect(snapshot).toBe(3)
    expect(typeof snapshot).toBe('number')
  })
})
