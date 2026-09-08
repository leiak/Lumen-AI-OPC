// W23 — src/api/system/post.spec.ts. RuoYi built-in post (岗位) CRUD.
// 5 endpoints: listPost / getPost / addPost / updatePost / delPost.
//
// Pinned behaviors:
// - delPost accepts `number | number[]` (Array.toString → comma-join).
// - Same URL sharing pattern as most RuoYi CRUD modules — addPost/updatePost
//   share '/system/post' (POST vs PUT distinguish).
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  listPost,
  getPost,
  addPost,
  updatePost,
  delPost,
} from '@/api/system/post'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200, data: {} } as any)
})

describe('api/system/post — listPost()', () => {
  it('1. listPost({postCode,postName,status}) -> GET /system/post/list with params', () => {
    listPost({ postCode: 'ceo', postName: '董事长', status: '0' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/post/list',
      method: 'get',
      params: { postCode: 'ceo', postName: '董事长', status: '0' },
    })
  })

  it('2. listPost() uses `params` not `data` (GET envelope via query string)', () => {
    listPost({ foo: 'bar' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params).toEqual({ foo: 'bar' })
    expect('data' in arg).toBe(false)
  })

  it('3. listPost() return value is the request() promise', () => {
    const sentinel = Symbol('listPost-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(listPost({} as any)).toBe(sentinel)
  })
})

describe('api/system/post — getPost()', () => {
  it('4. getPost(1) -> GET /system/post/1 (URL concat)', () => {
    getPost(1)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/post/1',
      method: 'get',
    })
  })

  it('5. getPost() call config has exactly 2 keys: url + method', () => {
    getPost(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('6. getPost() return value is the request() promise', () => {
    const sentinel = Symbol('getPost-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getPost(1)).toBe(sentinel)
  })
})

describe('api/system/post — addPost()', () => {
  it('7. addPost({postCode,postName,postSort,...}) -> POST /system/post', () => {
    addPost({
      postId: 1,
      postCode: 'dev',
      postName: '工程师',
      postSort: 1,
      status: '0',
      remark: '技术岗',
    } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/post',
      method: 'post',
      data: {
        postId: 1,
        postCode: 'dev',
        postName: '工程师',
        postSort: 1,
        status: '0',
        remark: '技术岗',
      },
    })
  })

  it('8. addPost() uses `data` (not `params`) for the envelope', () => {
    addPost({ postName: 'A' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.data).toBeDefined()
    expect('params' in arg).toBe(false)
  })

  it('9. addPost() return value is the request() promise', () => {
    const sentinel = Symbol('addPost-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(addPost({} as any)).toBe(sentinel)
  })
})

describe('api/system/post — updatePost()', () => {
  it('10. updatePost({postId,...}) -> PUT /system/post (same URL as addPost)', () => {
    updatePost({ postId: 1, postName: '高级工程师' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/post',
      method: 'put',
      data: { postId: 1, postName: '高级工程师' },
    })
  })

  it('11. updatePost() and addPost() share URL — only method differs', () => {
    updatePost({ postId: 1 } as any)
    addPost({} as any)
    const updateArg = requestMock.mock.calls[0][0] as any
    const addArg = requestMock.mock.calls[1][0] as any
    expect(updateArg.url).toBe(addArg.url)
    expect(updateArg.url).toBe('/system/post')
    expect(updateArg.method).toBe('put')
    expect(addArg.method).toBe('post')
  })

  it('12. updatePost() return value is the request() promise', () => {
    const sentinel = Symbol('updatePost-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(updatePost({} as any)).toBe(sentinel)
  })
})

describe('api/system/post — delPost()', () => {
  it('13. delPost(5) -> DELETE /system/post/5 (single id)', () => {
    delPost(5)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/post/5',
      method: 'delete',
    })
  })

  it('14. delPost([1,2]) -> DELETE /system/post/1,2 (array comma-join)', () => {
    delPost([1, 2])
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/post/1,2',
      method: 'delete',
    })
  })

  it('15. delPost() return value is the request() promise', () => {
    const sentinel = Symbol('delPost-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(delPost(1)).toBe(sentinel)
  })
})

describe('api/system/post — module behavior', () => {
  it('16. all 5 exports are functions', () => {
    expect(typeof listPost).toBe('function')
    expect(typeof getPost).toBe('function')
    expect(typeof addPost).toBe('function')
    expect(typeof updatePost).toBe('function')
    expect(typeof delPost).toBe('function')
  })

  it('17. each export calls request exactly once per invocation', () => {
    listPost({} as any)
    getPost(1)
    addPost({} as any)
    updatePost({} as any)
    delPost(1)
    expect(requestMock).toHaveBeenCalledTimes(5)
  })

  it('18. all 5 endpoints have distinct URLs (no cross-routing)', () => {
    listPost({} as any)
    getPost(1)
    addPost({})
    updatePost({})
    delPost(1)
    const urls = requestMock.mock.calls.map((c) => (c[0] as any).url)
    expect(new Set(urls).size).toBe(3) // addPost/updatePost share '/system/post' + getPost/delPost share '/system/post/1'
    expect(urls.sort()).toEqual([
      '/system/post',
      '/system/post',
      '/system/post/1',
      '/system/post/1',
      '/system/post/list',
    ])
  })

  it('19. methods span GET/POST/PUT/DELETE — full post CRUD', () => {
    listPost({} as any)
    getPost(1)
    addPost({} as any)
    updatePost({} as any)
    delPost(1)
    const methods = requestMock.mock.calls.map((c) => (c[0] as any).method)
    expect(methods.sort()).toEqual(['delete', 'get', 'get', 'post', 'put'])
  })
})