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

import { login as apiLogin, logout as apiLogout, getInfo as apiGetInfo } from '@/api/login'
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
})
