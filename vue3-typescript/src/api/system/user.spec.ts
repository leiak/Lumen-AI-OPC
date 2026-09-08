// W23 — src/api/system/user.spec.ts. RuoYi built-in user (用户) CRUD + profile mgmt.
// 14 endpoints: listUser / getUser / addUser / updateUser / delUser /
// resetUserPwd / changeUserStatus / getUserProfile / updateUserProfile /
// updateUserPwd / uploadAvatar / getAuthRole / updateAuthRole / deptTreeSelect.
//
// Pinned behaviors:
// - getUser(userId?) uses parseStrEmpty(userId) — undefined/0/'' becomes '',
//   yielding URL '/system/user/' (empty trailing id). Pin this behavior
//   so future "fix" to add validation is flagged.
// - uploadAvatar sets headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
//   — explicit override. Other endpoints rely on request interceptor defaults.
// - updateAuthRole uses PUT + `params:` (NOT `data:`) — body via query
//   string. Asymmetric with updateUser/updateUserProfile which use PUT + data.
// - getUserProfile / updateUserProfile share URL '/system/user/profile' (GET vs PUT).
// - updateUserPwd has its OWN URL '/system/user/profile/updatePwd' (distinct
//   from '/system/user/resetPwd' used by admin reset).
// - deptTreeSelect is here (NOT in dept.ts) — pin the placement asymmetry.
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))
// Mock @/utils/ruoyi parseStrEmpty so we can pin its effect on getUser.
vi.mock('@/utils/ruoyi', () => ({
  parseStrEmpty: (s: any) => (!s || s == 'undefined' || s == 'null' ? '' : s),
  // Provide other commonly-used exports so test files that import from
  // ruoyi elsewhere don't break (this spec only imports parseStrEmpty
  // via the source code transitively).
  dateStr: (d: string) => d,
  buildTree: (arr: any[]) => arr,
}))

import request from '@/utils/request'
import {
  listUser,
  getUser,
  addUser,
  updateUser,
  delUser,
  resetUserPwd,
  changeUserStatus,
  getUserProfile,
  updateUserProfile,
  updateUserPwd,
  uploadAvatar,
  getAuthRole,
  updateAuthRole,
  deptTreeSelect,
} from '@/api/system/user'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200, data: {} } as any)
})

describe('api/system/user — listUser()', () => {
  it('1. listUser({userName,status}) -> GET /system/user/list with params', () => {
    listUser({ userName: 'admin', status: '0', pageNum: 1, pageSize: 10 } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user/list',
      method: 'get',
      params: { userName: 'admin', status: '0', pageNum: 1, pageSize: 10 },
    })
  })

  it('2. listUser() uses `params` not `data` (GET envelope via query string)', () => {
    listUser({ foo: 'bar' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params).toEqual({ foo: 'bar' })
    expect('data' in arg).toBe(false)
  })

  it('3. listUser() return value is the request() promise', () => {
    const sentinel = Symbol('listUser-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(listUser({} as any)).toBe(sentinel)
  })
})

describe('api/system/user — getUser()', () => {
  it('4. getUser(1) -> GET /system/user/1 (URL concat via parseStrEmpty)', () => {
    getUser(1)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user/1',
      method: 'get',
    })
  })

  it('5. getUser() (no arg) -> GET /system/user/ (parseStrEmpty returns "")', () => {
    // Pin: parseStrEmpty(undefined) === '' → URL has empty trailing id.
    // Backend may treat this as "no user specified" — flag if a future
    // refactor adds null/undefined validation.
    getUser()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user/',
      method: 'get',
    })
  })

  it('6. getUser(0) -> GET /system/user/ (parseStrEmpty treats 0 as falsy)', () => {
    // Pin: parseStrEmpty(0) returns '' (because !0 is true). Edge case.
    getUser(0)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user/',
      method: 'get',
    })
  })

  it('7. getUser() call config has exactly 2 keys: url + method', () => {
    getUser(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('8. getUser() return value is the request() promise', () => {
    const sentinel = Symbol('getUser-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getUser(1)).toBe(sentinel)
  })
})

describe('api/system/user — addUser()', () => {
  it('9. addUser({userName,nickName,password,...}) -> POST /system/user', () => {
    addUser({
      userId: 1,
      userName: 'alice',
      nickName: '艾丽斯',
      password: 'P@ssw0rd',
      phonenumber: '13800001111',
      email: 'alice@example.com',
      status: '0',
      roleIds: [1, 2],
    } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user',
      method: 'post',
      data: {
        userId: 1,
        userName: 'alice',
        nickName: '艾丽斯',
        password: 'P@ssw0rd',
        phonenumber: '13800001111',
        email: 'alice@example.com',
        status: '0',
        roleIds: [1, 2],
      },
    })
  })

  it('10. addUser() uses `data` (not `params`) for the envelope', () => {
    addUser({ userName: 'A' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.data).toBeDefined()
    expect('params' in arg).toBe(false)
  })

  it('11. addUser() return value is the request() promise', () => {
    const sentinel = Symbol('addUser-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(addUser({} as any)).toBe(sentinel)
  })
})

describe('api/system/user — updateUser()', () => {
  it('12. updateUser({userId,...}) -> PUT /system/user (same URL as addUser)', () => {
    updateUser({ userId: 1, nickName: '艾莉丝' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user',
      method: 'put',
      data: { userId: 1, nickName: '艾莉丝' },
    })
  })

  it('13. updateUser() and addUser() share URL — only method differs', () => {
    updateUser({ userId: 1 } as any)
    addUser({} as any)
    const updateArg = requestMock.mock.calls[0][0] as any
    const addArg = requestMock.mock.calls[1][0] as any
    expect(updateArg.url).toBe(addArg.url)
    expect(updateArg.url).toBe('/system/user')
    expect(updateArg.method).toBe('put')
    expect(addArg.method).toBe('post')
  })

  it('14. updateUser() return value is the request() promise', () => {
    const sentinel = Symbol('updateUser-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(updateUser({} as any)).toBe(sentinel)
  })
})

describe('api/system/user — delUser()', () => {
  it('15. delUser(5) -> DELETE /system/user/5 (single id)', () => {
    delUser(5)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user/5',
      method: 'delete',
    })
  })

  it('16. delUser([1,2,3]) -> DELETE /system/user/1,2,3 (array comma-join)', () => {
    delUser([1, 2, 3])
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user/1,2,3',
      method: 'delete',
    })
  })

  it('17. delUser() return value is the request() promise', () => {
    const sentinel = Symbol('delUser-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(delUser(1)).toBe(sentinel)
  })
})

describe('api/system/user — resetUserPwd()', () => {
  it('18. resetUserPwd(1,"newPwd") -> PUT /system/user/resetPwd with {userId,password}', () => {
    resetUserPwd(1, 'newPwd')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user/resetPwd',
      method: 'put',
      data: { userId: 1, password: 'newPwd' },
    })
  })

  it('19. resetUserPwd() builds {userId,password} envelope from positional args', () => {
    resetUserPwd(99, 'pwd')
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.data.userId).toBe(99)
    expect(arg.data.password).toBe('pwd')
  })

  it('20. resetUserPwd() return value is the request() promise', () => {
    const sentinel = Symbol('resetPwd-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(resetUserPwd(1, 'p')).toBe(sentinel)
  })
})

describe('api/system/user — changeUserStatus()', () => {
  it('21. changeUserStatus(1,"1") -> PUT /system/user/changeStatus with {userId,status}', () => {
    changeUserStatus(1, '1')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user/changeStatus',
      method: 'put',
      data: { userId: 1, status: '1' },
    })
  })

  it('22. changeUserStatus() builds {userId,status} envelope from positional args', () => {
    changeUserStatus(99, '0')
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.data.userId).toBe(99)
    expect(arg.data.status).toBe('0')
  })

  it('23. changeUserStatus() return value is the request() promise', () => {
    const sentinel = Symbol('changeStatus-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(changeUserStatus(1, '0')).toBe(sentinel)
  })
})

describe('api/system/user — getUserProfile()', () => {
  it('24. getUserProfile() -> GET /system/user/profile (no body, no params)', () => {
    getUserProfile()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user/profile',
      method: 'get',
    })
  })

  it('25. getUserProfile() URL is shared with updateUserProfile — GET vs PUT', () => {
    getUserProfile()
    updateUserProfile({} as any)
    const getArg = requestMock.mock.calls[0][0] as any
    const updateArg = requestMock.mock.calls[1][0] as any
    expect(getArg.url).toBe(updateArg.url)
    expect(getArg.url).toBe('/system/user/profile')
    expect(getArg.method).toBe('get')
    expect(updateArg.method).toBe('put')
  })

  it('26. getUserProfile() call config has exactly 2 keys: url + method', () => {
    getUserProfile()
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('27. getUserProfile() return value is the request() promise', () => {
    const sentinel = Symbol('profile-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getUserProfile()).toBe(sentinel)
  })
})

describe('api/system/user — updateUserProfile()', () => {
  it('28. updateUserProfile({nickName,...}) -> PUT /system/user/profile with data', () => {
    updateUserProfile({ userId: 1, nickName: '新昵称' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user/profile',
      method: 'put',
      data: { userId: 1, nickName: '新昵称' },
    })
  })

  it('29. updateUserProfile() return value is the request() promise', () => {
    const sentinel = Symbol('updateProfile-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(updateUserProfile({} as any)).toBe(sentinel)
  })
})

describe('api/system/user — updateUserPwd()', () => {
  it('30. updateUserPwd("old","new") -> PUT /system/user/profile/updatePwd with {oldPassword,newPassword}', () => {
    updateUserPwd('oldPwd', 'newPwd')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user/profile/updatePwd',
      method: 'put',
      data: { oldPassword: 'oldPwd', newPassword: 'newPwd' },
    })
  })

  it('31. updateUserPwd() builds {oldPassword,newPassword} envelope from positional args', () => {
    updateUserPwd('o', 'n')
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.data.oldPassword).toBe('o')
    expect(arg.data.newPassword).toBe('n')
  })

  it('32. updateUserPwd() URL is distinct from admin resetUserPwd', () => {
    // Pin: '/system/user/profile/updatePwd' (self-service) is DIFFERENT
    // from '/system/user/resetPwd' (admin reset). Same data shape, but
    // different endpoints.
    updateUserPwd('o', 'n')
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url).toBe('/system/user/profile/updatePwd')
    expect(arg.url).not.toBe('/system/user/resetPwd')
  })

  it('33. updateUserPwd() return value is the request() promise', () => {
    const sentinel = Symbol('updatePwd-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(updateUserPwd('o', 'n')).toBe(sentinel)
  })
})

describe('api/system/user — uploadAvatar()', () => {
  it('34. uploadAvatar(FormData) -> POST /system/user/profile/avatar with form-urlencoded headers', () => {
    const fd = new FormData()
    fd.append('avatar', new Blob(['x']), 'avatar.png')
    uploadAvatar(fd)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user/profile/avatar',
      method: 'post',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      data: fd,
    })
  })

  it('35. uploadAvatar() sets explicit Content-Type header (overrides default application/json)', () => {
    // Pin: uploadAvatar explicitly sets Content-Type. Most other endpoints
    // rely on the request interceptor's default. Document the override.
    const fd = new FormData()
    uploadAvatar(fd)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.headers).toEqual({ 'Content-Type': 'application/x-www-form-urlencoded' })
  })

  it('36. uploadAvatar() also accepts File (not just FormData) — pin signature', () => {
    const file = new File(['x'], 'avatar.png', { type: 'image/png' })
    uploadAvatar(file)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user/profile/avatar',
      method: 'post',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      data: file,
    })
  })

  it('37. uploadAvatar() return value is the request() promise', () => {
    const sentinel = Symbol('avatar-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(uploadAvatar(new FormData())).toBe(sentinel)
  })
})

describe('api/system/user — getAuthRole()', () => {
  it('38. getAuthRole(1) -> GET /system/user/authRole/1 (URL concat)', () => {
    getAuthRole(1)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user/authRole/1',
      method: 'get',
    })
  })

  it('39. getAuthRole() call config has exactly 2 keys: url + method', () => {
    getAuthRole(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('40. getAuthRole() return value is the request() promise', () => {
    const sentinel = Symbol('authRole-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getAuthRole(1)).toBe(sentinel)
  })
})

describe('api/system/user — updateAuthRole()', () => {
  it('41. updateAuthRole({userId,roleIds}) -> PUT /system/user/authRole with params (NOT data)', () => {
    updateAuthRole({ userId: 1, roleIds: [1, 2, 3] } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user/authRole',
      method: 'put',
      params: { userId: 1, roleIds: [1, 2, 3] },
    })
  })

  it('42. updateAuthRole() uses PUT + params (NOT data) — pin asymmetry with updateUser', () => {
    // Pin: updateUser uses PUT + data, but updateAuthRole uses PUT + params.
    // Same HTTP verb, different envelope mechanism. Asymmetric — flag if
    // refactored to use data envelope.
    updateAuthRole({ userId: 1 } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.method).toBe('put')
    expect(arg.params).toEqual({ userId: 1 })
    expect('data' in arg).toBe(false)
  })

  it('43. updateAuthRole() return value is the request() promise', () => {
    const sentinel = Symbol('updateAuthRole-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(updateAuthRole({} as any)).toBe(sentinel)
  })
})

describe('api/system/user — deptTreeSelect()', () => {
  it('44. deptTreeSelect() -> GET /system/user/deptTree (no body, no params)', () => {
    deptTreeSelect()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user/deptTree',
      method: 'get',
    })
  })

  it('45. deptTreeSelect() is here in user.ts (NOT in dept.ts) — pin placement asymmetry', () => {
    // Pin: role.ts also has a deptTreeSelect(roleId). user.ts's version
    // takes no args and returns the FULL dept tree. Document the asymmetry.
    deptTreeSelect()
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url).toBe('/system/user/deptTree')
  })

  it('46. deptTreeSelect() return value is the request() promise', () => {
    const sentinel = Symbol('deptTree-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(deptTreeSelect()).toBe(sentinel)
  })
})

describe('api/system/user — module behavior', () => {
  it('47. all 14 exports are functions', () => {
    expect(typeof listUser).toBe('function')
    expect(typeof getUser).toBe('function')
    expect(typeof addUser).toBe('function')
    expect(typeof updateUser).toBe('function')
    expect(typeof delUser).toBe('function')
    expect(typeof resetUserPwd).toBe('function')
    expect(typeof changeUserStatus).toBe('function')
    expect(typeof getUserProfile).toBe('function')
    expect(typeof updateUserProfile).toBe('function')
    expect(typeof updateUserPwd).toBe('function')
    expect(typeof uploadAvatar).toBe('function')
    expect(typeof getAuthRole).toBe('function')
    expect(typeof updateAuthRole).toBe('function')
    expect(typeof deptTreeSelect).toBe('function')
  })

  it('48. each export calls request exactly once per invocation', () => {
    listUser({} as any)
    getUser(1)
    addUser({} as any)
    updateUser({} as any)
    delUser(1)
    resetUserPwd(1, 'p')
    changeUserStatus(1, '0')
    getUserProfile()
    updateUserProfile({} as any)
    updateUserPwd('o', 'n')
    uploadAvatar(new FormData())
    getAuthRole(1)
    updateAuthRole({} as any)
    deptTreeSelect()
    expect(requestMock).toHaveBeenCalledTimes(14)
  })

  it('49. all 14 endpoints have distinct URLs (no cross-routing)', () => {
    listUser({} as any)
    getUser(1)
    addUser({})
    updateUser({})
    delUser(1)
    resetUserPwd(1, 'p')
    changeUserStatus(1, '0')
    getUserProfile()
    updateUserProfile({})
    updateUserPwd('o', 'n')
    uploadAvatar(new FormData())
    getAuthRole(1)
    updateAuthRole({})
    deptTreeSelect()
    const urls = requestMock.mock.calls.map((c) => (c[0] as any).url)
    expect(new Set(urls).size).toBe(11) // 3 URL-sharing pairs: addUser/updateUser + getUser/delUser + getUserProfile/updateUserProfile
    expect(urls.sort()).toEqual([
      '/system/user',
      '/system/user',
      '/system/user/1',
      '/system/user/1',
      '/system/user/authRole',
      '/system/user/authRole/1',
      '/system/user/changeStatus',
      '/system/user/deptTree',
      '/system/user/list',
      '/system/user/profile',
      '/system/user/profile',
      '/system/user/profile/avatar',
      '/system/user/profile/updatePwd',
      '/system/user/resetPwd',
    ])
  })
})