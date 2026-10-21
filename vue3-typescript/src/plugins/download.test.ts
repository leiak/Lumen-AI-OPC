// W12.2 — plugins/download.ts spec. The file had no spec at all before this
// one, which is why the `errorCode[code] || msg || default` precedence bug in
// printErrMsg() survived the response-interceptor flip in utils/request.ts.
// Section P pins printErrMsg(); section Q (added later) pins zip().
import { beforeEach, describe, expect, it, vi } from 'vitest'

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

const ElMessageSpy = vi.mocked(ElMessage)
const elMessageError = () => (ElMessageSpy as any).error

beforeEach(() => {
  mocks.saveAs.mockReset()
  mocks.getToken.mockReset().mockReturnValue(undefined)
  ;(ElMessageSpy as any).error.mockReset()
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