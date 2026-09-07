// W12.1 — request.ts axios interceptor tests.
// Covers token injection, GET param serialization, repeat-submit guard,
// response status branching (success / 401 / 5xx / 601 / 4xx / network /
// timeout), and download() helper.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import axios from 'axios'

// ---------------------------------------------------------------------------
// Module mocks — must come before importing @/utils/request
// ---------------------------------------------------------------------------

// Hoist mock-fn holders so vi.mock factories (which are also hoisted) can
// reference them without TDZ errors.
const mocks = vi.hoisted(() => ({
  logOut: vi.fn().mockResolvedValue(undefined),
  saveAs: vi.fn(),
  getToken: vi.fn().mockReturnValue(undefined as string | undefined),
}))

// Element-plus: replace interactive APIs with spies so request.ts can call
// them without rendering UI. Keep the rest of element-plus for other specs.
// Note: ElMessage is BOTH a callable function AND an object with shortcut
// methods (.error / .success / .warning / .info) — element-plus wires them
// on the function. We replicate that shape so request.ts line 151
// (`ElMessage.error('下载文件出现错误...')`) works.
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  const elMessageMock = Object.assign(vi.fn(), {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  })
  return {
    ...actual,
    ElMessage: elMessageMock,
    ElNotification: { error: vi.fn(), success: vi.fn(), info: vi.fn() },
    ElMessageBox: {
      confirm: vi.fn().mockResolvedValue(undefined),
      alert: vi.fn().mockResolvedValue(undefined),
      prompt: vi.fn().mockResolvedValue({ value: '' }),
    },
    ElLoading: { service: vi.fn(() => ({ close: vi.fn() })) },
  }
})

// User store: stub logOut so the 401 branch can be exercised.
vi.mock('@/store/modules/user', () => ({
  default: () => ({ logOut: mocks.logOut }),
}))

// file-saver: stub saveAs.
vi.mock('file-saver', () => ({ saveAs: mocks.saveAs }))

// auth.getToken: override so we control whether a token is present.
vi.mock('@/utils/auth', async () => {
  const actual = await vi.importActual<typeof import('@/utils/auth')>('@/utils/auth')
  return {
    ...actual,
    getToken: mocks.getToken,
  }
})

import service, { isRelogin, download } from '@/utils/request'
import { ElMessage, ElMessageBox, ElLoading, ElNotification } from 'element-plus'

// Type the spied element-plus exports for assertions.
const ElMessageSpy = vi.mocked(ElMessage)
const ElMessageBoxSpy = vi.mocked(ElMessageBox)
const ElNotificationSpy = vi.mocked(ElNotification)
const ElLoadingSpy = vi.mocked(ElLoading)

let mock: MockAdapter

beforeEach(() => {
  mock = new MockAdapter(service)
  isRelogin.show = false
  mocks.getToken.mockReset().mockReturnValue(undefined)
  mocks.logOut.mockReset().mockResolvedValue(undefined)
  mocks.saveAs.mockReset()
  ElMessageSpy.mockReset()
  // ElMessage.error / .success / etc. are separate mocks on the same object.
  ;(ElMessageSpy as any).error.mockReset()
  ;(ElMessageSpy as any).success.mockReset()
  ;(ElMessageSpy as any).warning.mockReset()
  ;(ElMessageSpy as any).info.mockReset()
  ElMessageBoxSpy.confirm.mockReset().mockResolvedValue(undefined)
  ElMessageBoxSpy.alert.mockReset().mockResolvedValue(undefined)
  ElNotificationSpy.error.mockReset()
  ElLoadingSpy.service.mockReset().mockReturnValue({ close: vi.fn() } as any)
  // happy-dom exposes window.location as a real Location; we want a writable
  // stub so request.ts can set `location.href = '/index'` (the 401 branch).
  if (typeof window !== 'undefined') {
    try {
      Object.defineProperty(window, 'location', {
        configurable: true,
        writable: true,
        value: { href: '' },
      })
    } catch {
      ;(window as any).location = { href: '' }
    }
  }
})

afterEach(() => {
  mock.restore()
})

// ============================================================================
// A. Module-level & config
// ============================================================================

describe('A. module-level configuration', () => {
  it('A1. exports an axios instance with VITE_APP_BASE_API baseURL + 10000 timeout', () => {
    // service.defaults is the axios instance config (not interceptors).
    expect(service.defaults.baseURL).toBe(import.meta.env.VITE_APP_BASE_API)
    expect(service.defaults.timeout).toBe(10000)
  })

  it('A2. isRelogin.show starts as false (reset in beforeEach)', () => {
    expect(isRelogin.show).toBe(false)
  })

  it('A3. module-load side effect: axios.defaults.headers Content-Type is application/json;charset=utf-8', () => {
    expect(axios.defaults.headers['Content-Type']).toBe('application/json;charset=utf-8')
  })
})

// ============================================================================
// B. Request interceptor — token injection
// ============================================================================

describe('B. request interceptor: token injection', () => {
  it('B1. GET with token: injects Authorization Bearer header', async () => {
    mocks.getToken.mockReturnValue('abc-token')
    mock.onGet('/api/protected').reply(200, { code: 200, data: { ok: true } })

    await service.get('/api/protected')

    const req = mock.history.get[0]
    expect(req?.headers?.['Authorization']).toBe('Bearer abc-token')
  })

  it('B2. POST with headers.isToken === false: skips Authorization', async () => {
    mocks.getToken.mockReturnValue('abc-token')
    mock.onPost('/api/public').reply(200, { code: 200 })

    await service.post(
      '/api/public',
      { x: 1 },
      { headers: { isToken: false } as any },
    )

    const req = mock.history.post[0]
    expect(req?.headers?.['Authorization']).toBeUndefined()
  })

  it('B3. GET without token: omits Authorization header', async () => {
    mocks.getToken.mockReturnValue(undefined)
    mock.onGet('/api/anon').reply(200, { code: 200 })

    await service.get('/api/anon')

    const req = mock.history.get[0]
    expect(req?.headers?.['Authorization']).toBeUndefined()
  })
})

// ============================================================================
// C. Request interceptor — GET parameter serialization
// ============================================================================

describe('C. request interceptor: GET params -> URL', () => {
  it('C1. GET with params moves params to URL query string and clears params', async () => {
    // Match via regex because the interceptor rewrites /api/list to
    // /api/list?foo=bar&n=42 (trailing '&' is sliced by .slice(0,-1)).
    mock.onGet(/\/api\/list.*/).reply(200, { code: 200, data: [] })

    await service.get('/api/list', { params: { foo: 'bar', n: 42 } })

    const req = mock.history.get[0]
    expect(req!.url).toContain('foo=bar')
    expect(req!.url).toContain('n=42')
    expect(req!.params).toEqual({})
  })

  it('C2. GET with nested object params: encodes bracket keys', async () => {
    mock.onGet(/\/api\/list.*/).reply(200, { code: 200, data: [] })

    await service.get('/api/list', { params: { filter: { a: 1 } } })

    const req = mock.history.get[0]
    // encodeURIComponent('filter[a]') = 'filter%5Ba%5D'
    expect(req!.url).toContain('filter%5Ba%5D=1')
    expect(req!.params).toEqual({})
  })
})

// ============================================================================
// D. Request interceptor — repeat-submit guard
// ============================================================================

describe('D. request interceptor: repeat-submit guard', () => {
  it('D1. first POST writes sessionObj and proceeds', async () => {
    mock.onPost('/api/save').reply(200, { code: 200 })

    const res = await service.post('/api/save', { x: 1 })

    expect(res.code).toBe(200)
    expect(sessionStorage.getItem('sessionObj')).not.toBeNull()
  })

  it('D2. POST within 1s with same URL+data: rejects with "数据正在处理，请勿重复提交"', async () => {
    mock.onPost('/api/save').reply(200, { code: 200 })

    // First call: should succeed and prime sessionObj
    await service.post('/api/save', { x: 1 })

    // Second call immediately: must reject BEFORE mock handler runs (no second history entry).
    await expect(service.post('/api/save', { x: 1 })).rejects.toThrow(
      '数据正在处理，请勿重复提交',
    )
    expect(mock.history.post.length).toBe(1)
  })

  it('D3. POST after 1s with same URL+data: resets timestamp and proceeds', async () => {
    mock.onPost('/api/save').reply(200, { code: 200 })

    await service.post('/api/save', { x: 1 })
    const first = JSON.parse(sessionStorage.getItem('sessionObj')!).time
    expect(typeof first).toBe('number')

    // Bump the cached time backwards to simulate > 1s elapsed.
    const stored = JSON.parse(sessionStorage.getItem('sessionObj')!)
    stored.time = stored.time - 2000
    sessionStorage.setItem('sessionObj', JSON.stringify(stored))

    const res = await service.post('/api/save', { x: 1 })
    expect(res.code).toBe(200)
    expect(mock.history.post.length).toBe(2)
  })

  it('D4. POST with different URL within 1s: proceeds (URL mismatch)', async () => {
    mock.onPost(/\/api\/.+/).reply(200, { code: 200 })

    await service.post('/api/a', { x: 1 })
    await service.post('/api/b', { x: 1 })

    expect(mock.history.post.length).toBe(2)
  })

  it('D5. POST with different data within 1s: proceeds (data mismatch)', async () => {
    mock.onPost('/api/save').reply(200, { code: 200 })

    await service.post('/api/save', { x: 1 })
    await service.post('/api/save', { x: 2 })

    expect(mock.history.post.length).toBe(2)
  })

  it('D6. POST with headers.repeatSubmit === false: skips guard entirely', async () => {
    mock.onPost('/api/save').reply(200, { code: 200 })

    await service.post('/api/save', { x: 1 }, { headers: { repeatSubmit: false } as any })
    await service.post('/api/save', { x: 1 }, { headers: { repeatSubmit: false } as any })

    expect(mock.history.post.length).toBe(2)
    // sessionObj should NOT have been written because the guard was skipped.
    expect(sessionStorage.getItem('sessionObj')).toBeNull()
  })
})

// ============================================================================
// E. Response interceptor — success path
// ============================================================================

describe('E. response interceptor: success', () => {
  it('E1. data with explicit code 200 resolves the full data envelope', async () => {
    const payload = { code: 200, msg: 'ok', data: { id: 1 } }
    mock.onGet('/api/x').reply(200, payload)

    const res = await service.get('/api/x')
    expect(res).toEqual(payload)
  })

  it('E2. data with no code field defaults to code 200 and resolves', async () => {
    const payload = { msg: 'ok', data: { id: 1 } }
    mock.onGet('/api/x').reply(200, payload)

    const res = await service.get('/api/x')
    expect(res).toEqual(payload)
  })
})

// ============================================================================
// F. Response interceptor — binary responses
// ============================================================================

describe('F. response interceptor: binary (blob/arraybuffer)', () => {
  it('F1. responseType=blob skips code check and resolves data', async () => {
    const blob = new Blob(['hello'], { type: 'text/plain' })
    mock.onGet('/api/file').reply(200, blob)

    const res = await service.get('/api/file', { responseType: 'blob' })
    expect(res).toBeInstanceOf(Blob)
  })

  it('F2. responseType=arraybuffer skips code check and resolves data', async () => {
    const buf = new ArrayBuffer(8)
    mock.onGet('/api/bin').reply(200, buf)

    const res = await service.get('/api/bin', { responseType: 'arraybuffer' })
    expect(res).toBeInstanceOf(ArrayBuffer)
  })
})

// ============================================================================
// G. Response interceptor — 401 login-expired
// ============================================================================

describe('G. response interceptor: 401 login-expired', () => {
  it('G1. 401 + isRelogin=false: pops ElMessageBox.confirm and calls logOut on confirm', async () => {
    ElMessageBoxSpy.confirm.mockResolvedValue(undefined)
    mock.onGet('/api/x').reply(200, { code: 401 })

    await expect(service.get('/api/x')).rejects.toBe(
      '无效的会话，或者会话已过期，请重新登录。',
    )
    expect(ElMessageBoxSpy.confirm).toHaveBeenCalledTimes(1)
    // Wait microtask flush for logOut promise chain.
    await new Promise((r) => setTimeout(r, 0))
    expect(mocks.logOut).toHaveBeenCalledTimes(1)
    expect((window as any).location.href).toBe('/index')
  })

  it('G2. 401 + user cancels confirm: logOut is NOT called but request still rejects', async () => {
    ElMessageBoxSpy.confirm.mockRejectedValue(new Error('cancel'))
    mock.onGet('/api/x').reply(200, { code: 401 })

    await expect(service.get('/api/x')).rejects.toBe(
      '无效的会话，或者会话已过期，请重新登录。',
    )
    await new Promise((r) => setTimeout(r, 0))
    expect(mocks.logOut).not.toHaveBeenCalled()
    expect(isRelogin.show).toBe(false) // .catch branch resets the flag
  })

  it('G3. 401 + isRelogin=true (already showing): does NOT re-prompt', async () => {
    isRelogin.show = true
    mock.onGet('/api/x').reply(200, { code: 401 })

    await expect(service.get('/api/x')).rejects.toBe(
      '无效的会话，或者会话已过期，请重新登录。',
    )
    expect(ElMessageBoxSpy.confirm).not.toHaveBeenCalled()
  })

  it('G4. 401 always rejects with the canonical Chinese message regardless of confirm outcome', async () => {
    ElMessageBoxSpy.confirm.mockResolvedValue(undefined)
    mock.onGet('/api/x').reply(200, { code: 401 })

    const promise = service.get('/api/x')
    await expect(promise).rejects.toBe('无效的会话，或者会话已过期，请重新登录。')
  })
})

// ============================================================================
// H. Response interceptor — 5xx / 601 / 4xx
// ============================================================================

describe('H. response interceptor: server / business errors', () => {
  it('H1. code=500 shows ElMessage(error) and rejects with Error(msg)', async () => {
    mock.onGet('/api/x').reply(200, { code: 500, msg: 'Internal Error' })

    await expect(service.get('/api/x')).rejects.toThrow('Internal Error')
    expect(ElMessageSpy).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'error' }),
    )
  })

  it('H2. code=502 and 503 both fall under code>=500 and reject with Error(msg)', async () => {
    mock.onGet('/502').reply(200, { code: 502, msg: 'Bad Gateway' })
    mock.onGet('/503').reply(200, { code: 503, msg: 'Service Unavailable' })

    await expect(service.get('/502')).rejects.toThrow('Bad Gateway')
    await expect(service.get('/503')).rejects.toThrow('Service Unavailable')
  })

  it('H3. code=601 is currently caught by the code>=500 branch (601-branch is DEAD code)', async () => {
    // KNOWN BEHAVIOR (potentially a bug in request.ts): the if/else chain is
    //   else if (code >= 500)  -> type:'error'
    //   else if (code === 601) -> type:'warning'   <-- never reached
    // Because 601 satisfies `code >= 500`, the 601 branch is dead. We pin the
    // actual behavior here so a future refactor that reorders the branches
    // (making the 601 branch live) gets flagged.
    mock.onGet('/api/x').reply(200, { code: 601, msg: 'Business warning' })

    await expect(service.get('/api/x')).rejects.toThrow('Business warning')
    expect(ElMessageSpy).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'error' }),
    )
  })

  it('H4. code=403 shows ElNotification.error and rejects with "error"', async () => {
    mock.onGet('/api/x').reply(200, { code: 403, msg: 'No permission' })

    await expect(service.get('/api/x')).rejects.toBe('error')
    expect(ElNotificationSpy.error).toHaveBeenCalledWith(
      expect.objectContaining({ title: '当前操作没有权限' }),
    )
  })

  it('H5. code=404 shows ElNotification.error and rejects with "error"', async () => {
    mock.onGet('/api/x').reply(200, { code: 404, msg: 'Not found' })

    await expect(service.get('/api/x')).rejects.toBe('error')
    expect(ElNotificationSpy.error).toHaveBeenCalledWith(
      expect.objectContaining({ title: '访问资源不存在' }),
    )
  })
})

// ============================================================================
// I. Response interceptor — transport-level errors
// ============================================================================

describe('I. response interceptor: network / timeout', () => {
  it('I1. "Network Error" maps to "后端接口连接异常"', async () => {
    mock.onGet('/api/x').networkError()

    await expect(service.get('/api/x')).rejects.toBeDefined()
    expect(ElMessageSpy).toHaveBeenCalledWith(
      expect.objectContaining({ message: '后端接口连接异常' }),
    )
  })

  it('I2. timeout maps to "系统接口请求超时"', async () => {
    mock.onGet('/api/x').timeout()

    await expect(service.get('/api/x')).rejects.toBeDefined()
    expect(ElMessageSpy).toHaveBeenCalledWith(
      expect.objectContaining({ message: '系统接口请求超时' }),
    )
  })
})

// ============================================================================
// J. download() helper
// ============================================================================

describe('J. download() helper', () => {
  it('J1. valid blob: opens ElLoading, calls saveAs, closes loading', async () => {
    const blob = new Blob(['hello'], { type: 'application/octet-stream' })
    mock.onPost('/api/export').reply(200, blob)

    await download('/api/export', { id: 1 }, 'report.xlsx')

    expect(ElLoadingSpy.service).toHaveBeenCalledWith(
      expect.objectContaining({
        text: '正在下载数据，请稍候',
        background: 'rgba(0, 0, 0, 0.7)',
      }),
    )
    expect(mocks.saveAs).toHaveBeenCalledTimes(1)
    const [blobArg, filenameArg] = mocks.saveAs.mock.calls[0]
    expect(blobArg).toBeInstanceOf(Blob)
    expect(filenameArg).toBe('report.xlsx')
  })

  it('J2. JSON error blob: ElMessage.error with mapped code, saveAs NOT called', async () => {
    const errJson = JSON.stringify({ code: 500, msg: 'export failed' })
    const jsonBlob = new Blob([errJson], { type: 'application/json' })
    mock.onPost('/api/export').reply(200, jsonBlob)

    await download('/api/export', {}, 'report.xlsx')

    expect(mocks.saveAs).not.toHaveBeenCalled()
    // download() uses the ElMessage.error() shortcut (not the callable),
    // passing through errorCode[code] or rspObj.msg or errorCode['default'].
    expect((ElMessageSpy as any).error).toHaveBeenCalledWith('export failed')
  })

  it('J3. network error during download: ElMessage.error("下载文件出现错误...") + close loading', async () => {
    mock.onPost('/api/export').networkError()

    await download('/api/export', {}, 'report.xlsx')

    // Response interceptor also fires ElMessage({message:'后端接口连接异常'}).
    // download()'s own .catch then fires ElMessage.error('下载文件出现错误...').
    expect((ElMessageSpy as any).error).toHaveBeenCalledWith(
      expect.stringContaining('下载文件出现错误'),
    )
    expect(mocks.saveAs).not.toHaveBeenCalled()
  })

  it('J4. ElLoading opens BEFORE the request resolves (verified by call order)', async () => {
    const calls: string[] = []
    ElLoadingSpy.service.mockImplementationOnce(() => {
      calls.push('loading.open')
      return { close: vi.fn(() => calls.push('loading.close')) } as any
    })
    mocks.saveAs.mockImplementationOnce(() => calls.push('saveAs'))

    const blob = new Blob(['x'], { type: 'application/octet-stream' })
    mock.onPost('/api/export').reply(200, blob)

    await download('/api/export', {}, 'r.xlsx')

    expect(calls[0]).toBe('loading.open')
    expect(calls).toContain('saveAs')
    expect(calls[calls.length - 1]).toBe('loading.close')
  })
})