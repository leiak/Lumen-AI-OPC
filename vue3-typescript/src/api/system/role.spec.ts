// W23 — src/api/system/role.spec.ts. RuoYi built-in role (角色) CRUD + auth user mgmt.
// 13 endpoints: listRole / getRole / addRole / updateRole / dataScope /
// changeRoleStatus / delRole / allocatedUserList / unallocatedUserList /
// authUserCancel / authUserCancelAll / authUserSelectAll / deptTreeSelect.
//
// Pinned behaviors:
// - authUserCancelAll and authUserSelectAll use PUT + `params:` (NOT
//   `data:`) — body sent via query string. Asymmetric with authUserCancel
//   which uses PUT + `data:`. Pin the asymmetry.
// - dataScope has its OWN URL '/system/role/dataScope' (distinct from
//   '/system/role' used by add/update). Same pattern as updateDeptSort
//   and updateMenuSort — specialized endpoints have separate URLs.
// - changeRoleStatus builds a `{roleId, status}` envelope inline (NOT a
//   raw object) — same pattern as user.changeUserStatus.
// - allocatedUserList / unallocatedUserList are TWO separate endpoints
//   for the same resource (already authorized vs unauthorized users).
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  listRole,
  getRole,
  addRole,
  updateRole,
  dataScope,
  changeRoleStatus,
  delRole,
  allocatedUserList,
  unallocatedUserList,
  authUserCancel,
  authUserCancelAll,
  authUserSelectAll,
  deptTreeSelect,
} from '@/api/system/role'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200, data: {} } as any)
})

describe('api/system/role — listRole()', () => {
  it('1. listRole({roleName,roleKey,status}) -> GET /system/role/list with params', () => {
    listRole({ roleName: '管理员', roleKey: 'admin', status: '0' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/role/list',
      method: 'get',
      params: { roleName: '管理员', roleKey: 'admin', status: '0' },
    })
  })

  it('2. listRole() uses `params` not `data` (GET envelope via query string)', () => {
    listRole({ foo: 'bar' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params).toEqual({ foo: 'bar' })
    expect('data' in arg).toBe(false)
  })

  it('3. listRole() return value is the request() promise', () => {
    const sentinel = Symbol('listRole-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(listRole({} as any)).toBe(sentinel)
  })
})

describe('api/system/role — getRole()', () => {
  it('4. getRole(2) -> GET /system/role/2 (URL concat)', () => {
    getRole(2)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/role/2',
      method: 'get',
    })
  })

  it('5. getRole() call config has exactly 2 keys: url + method', () => {
    getRole(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('6. getRole() return value is the request() promise', () => {
    const sentinel = Symbol('getRole-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getRole(1)).toBe(sentinel)
  })
})

describe('api/system/role — addRole()', () => {
  it('7. addRole({roleName,roleKey,roleSort,...}) -> POST /system/role', () => {
    addRole({
      roleId: 1,
      roleName: '运营',
      roleKey: 'operator',
      roleSort: 3,
      status: '0',
      remark: '运营角色',
    } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/role',
      method: 'post',
      data: {
        roleId: 1,
        roleName: '运营',
        roleKey: 'operator',
        roleSort: 3,
        status: '0',
        remark: '运营角色',
      },
    })
  })

  it('8. addRole() uses `data` (not `params`) for the envelope', () => {
    addRole({ roleName: 'A' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.data).toBeDefined()
    expect('params' in arg).toBe(false)
  })

  it('9. addRole() return value is the request() promise', () => {
    const sentinel = Symbol('addRole-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(addRole({} as any)).toBe(sentinel)
  })
})

describe('api/system/role — updateRole()', () => {
  it('10. updateRole({roleId,...}) -> PUT /system/role (same URL as addRole)', () => {
    updateRole({ roleId: 1, roleName: '运营主管' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/role',
      method: 'put',
      data: { roleId: 1, roleName: '运营主管' },
    })
  })

  it('11. updateRole() return value is the request() promise', () => {
    const sentinel = Symbol('updateRole-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(updateRole({} as any)).toBe(sentinel)
  })
})

describe('api/system/role — dataScope()', () => {
  it('12. dataScope({roleId,dataScope}) -> PUT /system/role/dataScope (distinct URL)', () => {
    // Pin: dataScope has its own URL '/system/role/dataScope' — distinct
    // from '/system/role' used by add/update. Same pattern as
    // updateDeptSort / updateMenuSort specialized endpoints.
    dataScope({ roleId: 1, dataScope: '2' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/role/dataScope',
      method: 'put',
      data: { roleId: 1, dataScope: '2' },
    })
  })

  it('13. dataScope() uses PUT method + data envelope', () => {
    dataScope({ roleId: 1 } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.method).toBe('put')
    expect(arg.data).toEqual({ roleId: 1 })
  })

  it('14. dataScope() return value is the request() promise', () => {
    const sentinel = Symbol('dataScope-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(dataScope({} as any)).toBe(sentinel)
  })
})

describe('api/system/role — changeRoleStatus()', () => {
  it('15. changeRoleStatus(1,"1") -> PUT /system/role/changeStatus with {roleId,status}', () => {
    changeRoleStatus(1, '1')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/role/changeStatus',
      method: 'put',
      data: { roleId: 1, status: '1' },
    })
  })

  it('16. changeRoleStatus() builds {roleId,status} envelope from positional args', () => {
    changeRoleStatus(99, '0')
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.data.roleId).toBe(99)
    expect(arg.data.status).toBe('0')
  })

  it('17. changeRoleStatus() return value is the request() promise', () => {
    const sentinel = Symbol('changeStatus-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(changeRoleStatus(1, '0')).toBe(sentinel)
  })
})

describe('api/system/role — delRole()', () => {
  it('18. delRole(2) -> DELETE /system/role/2 (single id)', () => {
    delRole(2)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/role/2',
      method: 'delete',
    })
  })

  it('19. delRole([1,2]) -> DELETE /system/role/1,2 (array comma-join)', () => {
    delRole([1, 2])
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/role/1,2',
      method: 'delete',
    })
  })

  it('20. delRole() return value is the request() promise', () => {
    const sentinel = Symbol('delRole-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(delRole(1)).toBe(sentinel)
  })
})

describe('api/system/role — allocatedUserList()', () => {
  it('21. allocatedUserList({roleId,userName}) -> GET /system/role/authUser/allocatedList', () => {
    allocatedUserList({ roleId: 1, userName: 'alice' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/role/authUser/allocatedList',
      method: 'get',
      params: { roleId: 1, userName: 'alice' },
    })
  })

  it('22. allocatedUserList() URL is distinct from unallocatedUserList', () => {
    // Pin: TWO endpoints for same resource (already-authed vs unauthed).
    allocatedUserList({ roleId: 1 } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url).toBe('/system/role/authUser/allocatedList')
  })

  it('23. allocatedUserList() return value is the request() promise', () => {
    const sentinel = Symbol('allocatedList-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(allocatedUserList({} as any)).toBe(sentinel)
  })
})

describe('api/system/role — unallocatedUserList()', () => {
  it('24. unallocatedUserList({roleId,userName}) -> GET /system/role/authUser/unallocatedList', () => {
    unallocatedUserList({ roleId: 1, userName: 'bob' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/role/authUser/unallocatedList',
      method: 'get',
      params: { roleId: 1, userName: 'bob' },
    })
  })

  it('25. unallocatedUserList() URL is distinct from allocatedUserList', () => {
    unallocatedUserList({ roleId: 1 } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url).toBe('/system/role/authUser/unallocatedList')
  })

  it('26. unallocatedUserList() return value is the request() promise', () => {
    const sentinel = Symbol('unallocatedList-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(unallocatedUserList({} as any)).toBe(sentinel)
  })
})

describe('api/system/role — authUserCancel()', () => {
  it('27. authUserCancel({userId,roleId}) -> PUT /system/role/authUser/cancel with data', () => {
    authUserCancel({ userId: 1, roleId: 2 } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/role/authUser/cancel',
      method: 'put',
      data: { userId: 1, roleId: 2 },
    })
  })

  it('28. authUserCancel() uses PUT + data (NOT params) — pin asymmetry', () => {
    // Pin: authUserCancel uses PUT + data, but authUserCancelAll uses
    // PUT + params. Asymmetric — flag if refactored.
    authUserCancel({ userId: 1, roleId: 2 } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.method).toBe('put')
    expect(arg.data).toEqual({ userId: 1, roleId: 2 })
    expect('params' in arg).toBe(false)
  })

  it('29. authUserCancel() return value is the request() promise', () => {
    const sentinel = Symbol('cancel-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(authUserCancel({} as any)).toBe(sentinel)
  })
})

describe('api/system/role — authUserCancelAll()', () => {
  it('30. authUserCancelAll({roleId,userIds}) -> PUT /system/role/authUser/cancelAll with params', () => {
    authUserCancelAll({ roleId: 1, userIds: '1,2,3' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/role/authUser/cancelAll',
      method: 'put',
      params: { roleId: 1, userIds: '1,2,3' },
    })
  })

  it('31. authUserCancelAll() uses PUT + params (NOT data) — pin asymmetry with authUserCancel', () => {
    authUserCancelAll({ roleId: 1 } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.method).toBe('put')
    expect(arg.params).toEqual({ roleId: 1 })
    expect('data' in arg).toBe(false)
  })

  it('32. authUserCancelAll() return value is the request() promise', () => {
    const sentinel = Symbol('cancelAll-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(authUserCancelAll({} as any)).toBe(sentinel)
  })
})

describe('api/system/role — authUserSelectAll()', () => {
  it('33. authUserSelectAll({roleId,userIds}) -> PUT /system/role/authUser/selectAll with params', () => {
    authUserSelectAll({ roleId: 1, userIds: '1,2,3' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/role/authUser/selectAll',
      method: 'put',
      params: { roleId: 1, userIds: '1,2,3' },
    })
  })

  it('34. authUserSelectAll() uses PUT + params (same pattern as cancelAll)', () => {
    authUserSelectAll({ roleId: 1 } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.method).toBe('put')
    expect(arg.params).toEqual({ roleId: 1 })
    expect('data' in arg).toBe(false)
  })

  it('35. authUserSelectAll() return value is the request() promise', () => {
    const sentinel = Symbol('selectAll-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(authUserSelectAll({} as any)).toBe(sentinel)
  })
})

describe('api/system/role — deptTreeSelect()', () => {
  it('36. deptTreeSelect(2) -> GET /system/role/deptTree/2', () => {
    deptTreeSelect(2)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/role/deptTree/2',
      method: 'get',
    })
  })

  it('37. deptTreeSelect() embeds roleId in path via concat (no encodeURIComponent)', () => {
    deptTreeSelect(999)
    expect(requestMock).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/system/role/deptTree/999' }),
    )
  })

  it('38. deptTreeSelect() return value is the request() promise', () => {
    const sentinel = Symbol('deptTree-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(deptTreeSelect(1)).toBe(sentinel)
  })
})

describe('api/system/role — module behavior', () => {
  it('39. all 13 exports are functions', () => {
    expect(typeof listRole).toBe('function')
    expect(typeof getRole).toBe('function')
    expect(typeof addRole).toBe('function')
    expect(typeof updateRole).toBe('function')
    expect(typeof dataScope).toBe('function')
    expect(typeof changeRoleStatus).toBe('function')
    expect(typeof delRole).toBe('function')
    expect(typeof allocatedUserList).toBe('function')
    expect(typeof unallocatedUserList).toBe('function')
    expect(typeof authUserCancel).toBe('function')
    expect(typeof authUserCancelAll).toBe('function')
    expect(typeof authUserSelectAll).toBe('function')
    expect(typeof deptTreeSelect).toBe('function')
  })

  it('40. each export calls request exactly once per invocation', () => {
    listRole({} as any)
    getRole(1)
    addRole({} as any)
    updateRole({} as any)
    dataScope({} as any)
    changeRoleStatus(1, '0')
    delRole(1)
    allocatedUserList({} as any)
    unallocatedUserList({} as any)
    authUserCancel({} as any)
    authUserCancelAll({} as any)
    authUserSelectAll({} as any)
    deptTreeSelect(1)
    expect(requestMock).toHaveBeenCalledTimes(13)
  })

  it('41. all 13 endpoints have distinct URLs (no cross-routing)', () => {
    listRole({} as any)
    getRole(1)
    addRole({})
    updateRole({})
    dataScope({})
    changeRoleStatus(1, '0')
    delRole(1)
    allocatedUserList({})
    unallocatedUserList({})
    authUserCancel({})
    authUserCancelAll({})
    authUserSelectAll({})
    deptTreeSelect(1)
    const urls = requestMock.mock.calls.map((c) => (c[0] as any).url)
    expect(new Set(urls).size).toBe(11) // addRole/updateRole share '/system/role' + getRole/delRole share '/system/role/1'
    expect(urls.sort()).toEqual([
      '/system/role',
      '/system/role',
      '/system/role/1',
      '/system/role/1',
      '/system/role/authUser/allocatedList',
      '/system/role/authUser/cancel',
      '/system/role/authUser/cancelAll',
      '/system/role/authUser/selectAll',
      '/system/role/authUser/unallocatedList',
      '/system/role/changeStatus',
      '/system/role/dataScope',
      '/system/role/deptTree/1',
      '/system/role/list',
    ])
  })
})