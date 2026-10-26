import { beforeEach, describe, expect, it, vi } from 'vitest'

// Mock all 6 modules the user store imports BEFORE importing the store.
// Use inline factories so we don't trip on vi.mock hoisting.
vi.mock('@/api/login', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  getInfo: vi.fn(),
}))
vi.mock('@/utils/auth', () => ({
  getToken: vi.fn().mockReturnValue(undefined),
  setToken: vi.fn(),
  removeToken: vi.fn(),
}))
vi.mock('@/plugins/cache', () => ({
  default: {
    session: { set: vi.fn(), get: vi.fn(), remove: vi.fn() },
    local: { set: vi.fn(), get: vi.fn(), remove: vi.fn() },
  },
}))
vi.mock('@/router', () => ({
  default: { push: vi.fn() },
}))
vi.mock('@/store/modules/lock', () => ({
  default: () => ({
    unlockScreen: vi.fn(),
  }),
}))
// 多公司（可切换的租户）—— store 依赖这两个 API，必须挡掉真实 request
vi.mock('@/api/opc/user', () => ({
  listMyCompanyMembers: vi.fn(),
  switchCompany: vi.fn(),
}))

import { login as apiLogin, logout as apiLogout, getInfo as apiGetInfo } from '@/api/login'
import { listMyCompanyMembers, switchCompany as switchCompanyApi } from '@/api/opc/user'
import { setToken, removeToken } from '@/utils/auth'
import cache from '@/plugins/cache'
import router from '@/router'
import useUserStore from '@/store/modules/user'

const mockLogin = apiLogin as unknown as ReturnType<typeof vi.fn>
const mockLogout = apiLogout as unknown as ReturnType<typeof vi.fn>
const mockGetInfo = apiGetInfo as unknown as ReturnType<typeof vi.fn>
const mockSetToken = setToken as unknown as ReturnType<typeof vi.fn>
const mockRemoveToken = removeToken as unknown as ReturnType<typeof vi.fn>
const mockRouterPush = router.push as unknown as ReturnType<typeof vi.fn>

const apiLoginMock = mockLogin
const apiLogoutMock = mockLogout
const apiGetInfoMock = mockGetInfo
const setTokenMock = mockSetToken
const removeTokenMock = mockRemoveToken
const routerPushMock = mockRouterPush

const cacheMock = cache as unknown as {
  session: { set: ReturnType<typeof vi.fn>; get: ReturnType<typeof vi.fn>; remove: ReturnType<typeof vi.fn> }
}

const listMyCompanyMembersMock = listMyCompanyMembers as unknown as ReturnType<typeof vi.fn>
const switchCompanyApiMock = switchCompanyApi as unknown as ReturnType<typeof vi.fn>

describe('user store — login', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('trims username and calls api.login', async () => {
    apiLoginMock.mockResolvedValueOnce({ data: { access_token: 'tok-1' } })
    const store = useUserStore()
    await store.login({ username: '  admin  ', password: 'pwd', code: '0000', uuid: 'u' })
    expect(apiLoginMock).toHaveBeenCalledWith('admin', 'pwd', '0000', 'u')
  })

  it('sets token and updates store on success', async () => {
    apiLoginMock.mockResolvedValueOnce({ data: { access_token: 'tok-2' } })
    const store = useUserStore()
    await store.login({ username: 'admin', password: 'p', code: 'c', uuid: 'u' })
    expect(setTokenMock).toHaveBeenCalledWith('tok-2')
    expect(store.token).toBe('tok-2')
  })

  it('rejects when api.login rejects', async () => {
    apiLoginMock.mockRejectedValueOnce(new Error('401'))
    const store = useUserStore()
    await expect(
      store.login({ username: 'a', password: 'p', code: 'c', uuid: 'u' }),
    ).rejects.toThrow('401')
    expect(store.token).toBeUndefined()
    expect(setTokenMock).not.toHaveBeenCalled()
  })

  it('does not throw when lockStore.unlockScreen is called (lock module stubbed)', async () => {
    apiLoginMock.mockResolvedValueOnce({ data: { access_token: 'tok-3' } })
    const store = useUserStore()
    await expect(
      store.login({ username: 'admin', password: 'p', code: 'c', uuid: 'u' }),
    ).resolves.toBeUndefined()
  })
})

describe('user store — getInfo', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('populates id/name/nickName/avatar from response', async () => {
    apiGetInfoMock.mockResolvedValueOnce({
      user: { userId: 42, userName: 'alice', nickName: 'Al', avatar: 'http://x/a.png' },
      roles: ['admin'],
      permissions: ['*:*:*'],
    })
    const store = useUserStore()
    await store.getInfo()
    expect(store.id).toBe(42)
    expect(store.name).toBe('alice')
    expect(store.nickName).toBe('Al')
    expect(store.avatar).toBe('http://x/a.png')
    expect(store.roles).toEqual(['admin'])
    expect(store.permissions).toEqual(['*:*:*'])
  })

  it('falls back to default avatar when avatar is empty', async () => {
    apiGetInfoMock.mockResolvedValueOnce({
      user: { userId: 1, userName: 'u', nickName: 'n', avatar: '' },
      roles: ['admin'],
      permissions: [],
    })
    const store = useUserStore()
    await store.getInfo()
    expect(store.avatar).toBe('/mock-profile.jpg')
  })

  it('falls back to ROLE_DEFAULT when roles empty', async () => {
    apiGetInfoMock.mockResolvedValueOnce({
      user: { userId: 1, userName: 'u', nickName: 'n', avatar: 'x' },
      roles: [],
      permissions: [],
    })
    const store = useUserStore()
    await store.getInfo()
    expect(store.roles).toEqual(['ROLE_DEFAULT'])
  })

  it('writes pwrChrtype to session cache', async () => {
    apiGetInfoMock.mockResolvedValueOnce({
      user: { userId: 1, userName: 'u', nickName: 'n', avatar: 'x' },
      roles: ['admin'],
      permissions: [],
      pwdChrtype: '0',
    })
    const store = useUserStore()
    await store.getInfo()
    expect(cacheMock.session.set).toHaveBeenCalledWith('pwrChrtype', '0')
  })

  it('triggers Profile redirect on default-password prompt', async () => {
    apiGetInfoMock.mockResolvedValueOnce({
      user: { userId: 1, userName: 'u', nickName: 'n', avatar: 'x' },
      roles: ['admin'],
      permissions: [],
      isDefaultModifyPwd: true,
      isPasswordExpired: false,
    })
    const store = useUserStore()
    await store.getInfo()
    // ElMessageBox.confirm is mocked to resolve immediately, so router.push fires
    expect(routerPushMock).toHaveBeenCalledWith({
      name: 'Profile',
      params: { activeTab: 'resetPwd' },
    })
  })

  it('triggers Profile redirect on password-expired prompt', async () => {
    apiGetInfoMock.mockResolvedValueOnce({
      user: { userId: 1, userName: 'u', nickName: 'n', avatar: 'x' },
      roles: ['admin'],
      permissions: [],
      isDefaultModifyPwd: false,
      isPasswordExpired: true,
    })
    const store = useUserStore()
    await store.getInfo()
    expect(routerPushMock).toHaveBeenCalledWith({
      name: 'Profile',
      params: { activeTab: 'resetPwd' },
    })
  })

  it('does NOT trigger Profile redirect when both flags are false', async () => {
    apiGetInfoMock.mockResolvedValueOnce({
      user: { userId: 1, userName: 'u', nickName: 'n', avatar: 'x' },
      roles: ['admin'],
      permissions: [],
      isDefaultModifyPwd: false,
      isPasswordExpired: false,
    })
    const store = useUserStore()
    await store.getInfo()
    expect(routerPushMock).not.toHaveBeenCalled()
  })

  it('rejects when api.getInfo rejects', async () => {
    apiGetInfoMock.mockRejectedValueOnce(new Error('500'))
    const store = useUserStore()
    await expect(store.getInfo()).rejects.toThrow('500')
  })
})

describe('user store — logOut', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('clears token, roles, perms and calls removeToken', async () => {
    apiLogoutMock.mockResolvedValueOnce({ msg: 'ok' })
    const store = useUserStore()
    store.token = 'old'
    store.roles = ['admin']
    store.permissions = ['sys:user:list']
    await store.logOut()
    expect(store.token).toBe('')
    expect(store.roles).toEqual([])
    expect(store.permissions).toEqual([])
    expect(removeTokenMock).toHaveBeenCalled()
  })

  it('rejects when api.logout rejects', async () => {
    apiLogoutMock.mockRejectedValueOnce(new Error('network'))
    const store = useUserStore()
    await expect(store.logOut()).rejects.toThrow('network')
  })

  it('clears companyId and companies — 换人登录不能留着上一个人的租户', async () => {
    apiLogoutMock.mockResolvedValueOnce({ msg: 'ok' })
    const store = useUserStore()
    store.companyId = 9
    store.companies = [{ id: 9, companyName: 'A 公司', role: 'OWNER', current: true }]
    await store.logOut()
    expect(store.companyId).toBe(0)
    expect(store.companies).toEqual([])
  })
})

describe('user store — 多公司（可切换的租户）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loadCompanies() stores the list and returns it', async () => {
    const list = [
      { id: 1, companyName: 'A 公司', role: 'OWNER', current: true },
      { id: 2, companyName: 'B 公司', role: 'STAFF', current: false },
    ]
    listMyCompanyMembersMock.mockResolvedValueOnce({ code: 200, data: list })
    const store = useUserStore()
    const res = await store.loadCompanies()
    expect(res).toEqual(list)
    expect(store.companies).toEqual(list)
  })

  it('loadCompanies() tolerates a non-array payload (不炸，列表当空)', async () => {
    listMyCompanyMembersMock.mockResolvedValueOnce({ code: 200, data: null })
    const store = useUserStore()
    await expect(store.loadCompanies()).resolves.toEqual([])
    expect(store.companies).toEqual([])
  })

  it('loadCompanies() tolerates a missing data field', async () => {
    listMyCompanyMembersMock.mockResolvedValueOnce({ code: 500 })
    const store = useUserStore()
    await expect(store.loadCompanies()).resolves.toEqual([])
    expect(store.companies).toEqual([])
  })

  it('defaultCompanyId() prefers the current tenant, not the first one', async () => {
    const store = useUserStore()
    store.companyId = 2
    const list = [
      { id: 1, companyName: 'A 公司', role: 'OWNER', current: false },
      { id: 2, companyName: 'B 公司', role: 'STAFF', current: true },
    ]
    expect(store.defaultCompanyId(list)).toBe(2)
  })

  it('defaultCompanyId() falls back to the first company when the current one is not in the list', () => {
    const store = useUserStore()
    store.companyId = 99
    const list = [
      { id: 1, companyName: 'A 公司', role: 'OWNER', current: false },
      { id: 2, companyName: 'B 公司', role: 'STAFF', current: false },
    ]
    expect(store.defaultCompanyId(list)).toBe(1)
  })

  it('defaultCompanyId() uses store.companies when no argument is given', async () => {
    listMyCompanyMembersMock.mockResolvedValueOnce({
      code: 200,
      data: [{ id: 5, companyName: 'E 公司', role: 'OWNER', current: true }],
    })
    const store = useUserStore()
    await store.loadCompanies()
    store.companyId = 5
    expect(store.defaultCompanyId()).toBe(5)
  })

  it('defaultCompanyId() returns undefined when there is no company at all', () => {
    const store = useUserStore()
    expect(store.defaultCompanyId([])).toBeUndefined()
  })

  it('switchCompany() updates companyId and the current flag', async () => {
    switchCompanyApiMock.mockResolvedValueOnce({ code: 200, data: { companyId: 2 } })
    const store = useUserStore()
    store.companies = [
      { id: 1, companyName: 'A 公司', role: 'OWNER', current: true },
      { id: 2, companyName: 'B 公司', role: 'STAFF', current: false },
    ]
    const res = await store.switchCompany(2)
    expect(res).toBe(2)
    expect(store.companyId).toBe(2)
    expect(store.companies[0].current).toBe(false)
    expect(store.companies[1].current).toBe(true)
  })

  it('switchCompany() accepts a bare id as the response payload', async () => {
    switchCompanyApiMock.mockResolvedValueOnce({ code: 200, data: 3 })
    const store = useUserStore()
    await expect(store.switchCompany(3)).resolves.toBe(3)
    expect(store.companyId).toBe(3)
  })

  it('switchCompany() leaves companyId at 0 when the payload carries no id', async () => {
    switchCompanyApiMock.mockResolvedValueOnce({ code: 200 })
    const store = useUserStore()
    await expect(store.switchCompany(3)).resolves.toBe(0)
    expect(store.companyId).toBe(0)
  })
})
