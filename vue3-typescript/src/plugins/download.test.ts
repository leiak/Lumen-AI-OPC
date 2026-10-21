// W12.2 — plugins/download.ts spec. The file had no spec at all before this
// one, which is why the `errorCode[code] || msg || default` precedence bug in
// printErrMsg() survived the response-interceptor flip in utils/request.ts.
// Section P pins printErrMsg(); section Q (added later) pins zip().
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// ---------------------------------------------------------------------------
// Module mocks — must come before importing @/plugins/download
// ---------------------------------------------------------------------------

const mocks = vi.hoisted(() => ({
  saveAs: vi.fn(),
  getToken: vi.fn().mockReturnValue(undefined as string | undefined),
}))

// ElMessage is BOTH callable and carries shortcut methods (.error etc).
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
    ElLoading: { service: vi.fn(() => ({ close: vi.fn() })) },
  }
})

vi.mock('file-saver', () => ({ saveAs: mocks.saveAs }))
vi.mock('@/utils/auth', async () => {
  const actual = await vi.importActual<typeof import('@/utils/auth')>('@/utils/auth')
  return { ...actual, getToken: mocks.getToken }
})

import downloadPlugin from '@/plugins/download'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import MockAdapter from 'axios-mock-adapter'

const ElMessageSpy = vi.mocked(ElMessage)
const elMessageError = () => (ElMessageSpy as any).error

let mock: MockAdapter
let consoleErrorSpy: any

beforeEach(() => {
  mock = new MockAdapter(axios)
  mocks.saveAs.mockReset()
  mocks.getToken.mockReset().mockReturnValue(undefined)
  ;(ElMessageSpy as any).error.mockReset()
  // The .catch path deliberately logs; keep the run output readable.
  consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
})

afterEach(() => {
  mock.restore()
  consoleErrorSpy.mockRestore()
})

// Build the minimal stand-in for the non-blob `res.data` that printErrMsg
// expects: an object whose .text() resolves to a raw body string.
const fakeBody = (body: string) => ({ text: () => Promise.resolve(body) })

// ============================================================================
// P. printErrMsg() — backend msg vs errorCode table precedence
// ============================================================================

describe('P. printErrMsg(): backend msg beats the errorCode table', () => {
  it('P1. code=403 with backend msg: shows the backend msg, NOT errorCode[403]', async () => {
    // THE pin. Under the old order `errorCode[code] || msg || default`,
    // '当前操作没有权限' would win and '无权下载该租户数据' would be dropped.
    await downloadPlugin.printErrMsg(
      fakeBody(JSON.stringify({ code: 403, msg: '无权下载该租户数据' })),
    )

    expect(elMessageError()).toHaveBeenCalledWith('无权下载该租户数据')
    expect(elMessageError()).not.toHaveBeenCalledWith('当前操作没有权限')
  })

  it('P2. code=403 with NO backend msg: falls back to errorCode[403]', async () => {
    await downloadPlugin.printErrMsg(fakeBody(JSON.stringify({ code: 403 })))

    expect(elMessageError()).toHaveBeenCalledWith('当前操作没有权限')
  })

  it('P3. code=403 with empty-string backend msg: empty string is falsy, falls through', async () => {
    await downloadPlugin.printErrMsg(fakeBody(JSON.stringify({ code: 403, msg: '' })))

    expect(elMessageError()).toHaveBeenCalledWith('当前操作没有权限')
  })

  it('P4. code=500 (no errorCode entry) with null backend msg: falls all the way to errorCode.default', async () => {
    // Order-insensitive on its own (500 has no table entry) — it pins that the
    // chain never yields undefined for a valid JSON error body.
    await downloadPlugin.printErrMsg(fakeBody(JSON.stringify({ code: 500, msg: null })))

    expect(elMessageError()).toHaveBeenCalledWith('系统未知错误，请反馈给管理员')
  })
})

// ============================================================================
// Q. zip() — .catch shape + the un-awaited printErrMsg
// ============================================================================

describe('Q. zip(): error surfacing', () => {
  it('Q1. valid zip blob: saveAs is called with the blob and the given name', async () => {
    const zipBlob = new Blob(['PK'], { type: 'application/octet-stream' })
    mock.onGet(/.*/).reply(200, zipBlob)

    downloadPlugin.zip('/api/export/bundle', 'bundle.zip')
    await vi.waitFor(() => expect(mocks.saveAs).toHaveBeenCalledTimes(1))

    const [blobArg, nameArg] = mocks.saveAs.mock.calls[0]
    expect(blobArg).toBeInstanceOf(Blob)
    expect(nameArg).toBe('bundle.zip')
  })

  it('Q2. JSON error body: printErrMsg runs and shows the backend msg', async () => {
    const errBlob = new Blob(
      [JSON.stringify({ code: 403, msg: '无权下载该租户数据' })],
      { type: 'application/json' },
    )
    mock.onGet(/.*/).reply(200, errBlob)

    downloadPlugin.zip('/api/export/bundle', 'bundle.zip')
    await vi.waitFor(() => expect(elMessageError()).toHaveBeenCalled())

    expect(elMessageError()).toHaveBeenCalledWith('无权下载该租户数据')
    expect(mocks.saveAs).not.toHaveBeenCalled()
  })

  it('Q3. non-JSON body (nginx 502 HTML page): surfaces an ElMessage instead of silently rejecting', async () => {
    // W12.2 printErrMsg 此前在 L26 被无 await 调用。响应体不是 JSON 时
    // JSON.parse 抛 SyntaxError 到一个无主的 promise —— unhandled rejection,
    // 用户看不到任何提示。改为 `await this.printErrMsg(res.data)` 后异常落进
    // .catch,SyntaxError 没有 .msg,于是显示硬编码兜底。
    // (真机上上游代理 502 时就是 application/json 的 content-type 配 HTML body。)
    const htmlBlob = new Blob(['<html><head><title>502 Bad Gateway</title></head></html>'], {
      type: 'application/json',
    })
    mock.onGet(/.*/).reply(200, htmlBlob)

    downloadPlugin.zip('/api/export/bundle', 'bundle.zip')

    // 这条断言本身就是 pin:没有 await 时这里永远等不到任何 ElMessage,
    // 而 SyntaxError 会以 "Unhandled Rejection" 的形式把整个测试运行打红。
    await vi.waitFor(() =>
      expect(elMessageError()).toHaveBeenCalledWith('下载文件出现错误，请联系管理员！'),
    )
    expect(mocks.saveAs).not.toHaveBeenCalled()
  })

  it('Q4. transport failure: axios error has no .msg, so the hardcoded fallback shows', async () => {
    mock.onGet(/.*/).networkError()

    downloadPlugin.zip('/api/export/bundle', 'bundle.zip')

    // axios 的网络错误是裸 Error,没有 .msg —— 兜底分支必须留着。
    await vi.waitFor(() =>
      expect(elMessageError()).toHaveBeenCalledWith('下载文件出现错误，请联系管理员！'),
    )
  })
})