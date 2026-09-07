// W12.4 — api/opc/user.spec.ts. Covers profile, company, home, invitations.
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  getMyProfile,
  saveProfile,
  listMyCompanies,
  createCompany,
  getCompany,
  updateCompany,
  getUserHome,
  generateInvitation,
  listMyInvitations,
  getInvitationPublic,
  acceptInvitation,
} from '@/api/opc/user'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200 } as any)
})

describe('api/opc/user', () => {
  // ---- profile ----
  it('1. getMyProfile() -> GET /opc/user/profile', () => {
    getMyProfile()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/user/profile',
      method: 'get',
    })
  })

  it('2. saveProfile({nickName:"x"}) -> POST /opc/user/profile data', () => {
    saveProfile({ nickName: 'x' })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/user/profile',
      method: 'post',
      data: { nickName: 'x' },
    })
  })

  // ---- companies ----
  it('3. listMyCompanies() -> GET /opc/user/companies', () => {
    listMyCompanies()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/user/companies',
      method: 'get',
    })
  })

  it('4. createCompany({name:"ACME"}) -> POST /opc/user/company data', () => {
    createCompany({ name: 'ACME' })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/user/company',
      method: 'post',
      data: { name: 'ACME' },
    })
  })

  it('5. getCompany(5) -> GET /opc/user/company/5', () => {
    getCompany(5)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/user/company/5',
      method: 'get',
    })
  })

  it('6. updateCompany({id:5,name:"X"}) -> PUT /opc/user/company data', () => {
    updateCompany({ id: 5, name: 'X' })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/user/company',
      method: 'put',
      data: { id: 5, name: 'X' },
    })
  })

  // ---- home ----
  it('7. getUserHome() -> GET /opc/user/home', () => {
    getUserHome()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/user/home',
      method: 'get',
    })
  })

  // ---- invitations ----
  it('8. generateInvitation() -> POST /opc/user/invitations/generate', () => {
    generateInvitation()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/user/invitations/generate',
      method: 'post',
    })
  })

  it('9. listMyInvitations() -> GET /opc/user/invitations', () => {
    listMyInvitations()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/user/invitations',
      method: 'get',
    })
  })

  it('10. getInvitationPublic("ABC12345") -> GET /opc/user/invitations/ABC12345', () => {
    getInvitationPublic('ABC12345')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/user/invitations/ABC12345',
      method: 'get',
    })
  })

  it('11. acceptInvitation("ABC12345","13800000000") -> POST /opc/user/invitations/accept data', () => {
    acceptInvitation('ABC12345', '13800000000')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/user/invitations/accept',
      method: 'post',
      data: { code: 'ABC12345', mobile: '13800000000' },
    })
  })

  it('12. acceptInvitation("ABC12345") (no mobile) -> data.mobile=undefined', () => {
    acceptInvitation('ABC12345')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/user/invitations/accept',
      method: 'post',
      data: { code: 'ABC12345', mobile: undefined },
    })
  })

  it('13. return value is the request() promise', () => {
    const sentinel = Symbol('p')
    requestMock.mockReturnValue(sentinel as any)
    expect(getMyProfile()).toBe(sentinel)
  })
})