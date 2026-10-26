// useCompanySwitch —— 切租户 = 调服务端 + 整页重载。
// 重载是必须的：19 个视图在 setup 里就把 companyId 取成了普通数字。
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessageBox } from 'element-plus'

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
import { switchCompany as switchCompanyApi } from '@/api/opc/user'
import { useCompanySwitch } from '@/composables/useCompanySwitch'

const confirmMock = ElMessageBox.confirm as unknown as ReturnType<typeof vi.fn>
const switchCompanyApiMock = switchCompanyApi as unknown as ReturnType<typeof vi.fn>

/** happy-dom 的 location.reload 会真的尝试导航 —— 挡掉它，只记录调用。 */
let reloadSpy: ReturnType<typeof vi.spyOn>

beforeEach(() => {
  vi.clearAllMocks()
  confirmMock.mockResolvedValue(undefined)
  switchCompanyApiMock.mockResolvedValue({ code: 200, data: { companyId: 2 } })
  reloadSpy = vi.spyOn(window.location, 'reload').mockImplementation(() => {})
})

describe('useCompanySwitch', () => {
  it('switches to the target company and reloads the page', async () => {
    const store = useUserStore()
    store.companyId = 1
    const { switchTo } = useCompanySwitch()

    const switched = await switchTo(2)

    expect(switched).toBe(true)
    expect(store.companyId).toBe(2)
    expect(reloadSpy).toHaveBeenCalledTimes(1)
  })

  it('asks for confirmation before switching', async () => {
    const store = useUserStore()
    store.companyId = 1
    const { switchTo } = useCompanySwitch()

    await switchTo(2)

    expect(confirmMock).toHaveBeenCalledTimes(1)
    expect(confirmMock.mock.calls[0][0]).toContain('切换公司')
  })

  it('does nothing when the user cancels the confirmation', async () => {
    const store = useUserStore()
    store.companyId = 1
    confirmMock.mockRejectedValueOnce(new Error('cancel'))
    const { switchTo } = useCompanySwitch()

    const switched = await switchTo(2)

    expect(switched).toBe(false)
    expect(store.companyId).toBe(1)
    expect(reloadSpy).not.toHaveBeenCalled()
  })

  it('skips the confirmation when confirm:false', async () => {
    const store = useUserStore()
    store.companyId = 1
    const { switchTo } = useCompanySwitch()

    const switched = await switchTo(2, { confirm: false })

    expect(switched).toBe(true)
    expect(confirmMock).not.toHaveBeenCalled()
    expect(store.companyId).toBe(2)
    expect(reloadSpy).toHaveBeenCalledTimes(1)
  })

  it('is a no-op when the target is already the current company', async () => {
    const store = useUserStore()
    store.companyId = 2
    const { switchTo } = useCompanySwitch()

    const switched = await switchTo(2)

    expect(switched).toBe(false)
    expect(confirmMock).not.toHaveBeenCalled()
    expect(reloadSpy).not.toHaveBeenCalled()
  })

  it('is a no-op for an empty target id', async () => {
    const store = useUserStore()
    store.companyId = 1
    const { switchTo } = useCompanySwitch()

    const switched = await switchTo(0)

    expect(switched).toBe(false)
    expect(confirmMock).not.toHaveBeenCalled()
    expect(reloadSpy).not.toHaveBeenCalled()
  })

  it('propagates a server failure and does not reload', async () => {
    const store = useUserStore()
    store.companyId = 1
    const { switchTo } = useCompanySwitch()
    const err = new Error('403 无权切换')
    vi.spyOn(store, 'switchCompany').mockRejectedValueOnce(err)

    await expect(switchTo(2)).rejects.toThrow('403 无权切换')
    expect(reloadSpy).not.toHaveBeenCalled()
  })
})
