import { expect, vi } from 'vitest'
import request from '@/utils/request'

/**
 * API 契约体检工具。
 *
 * <p>opc 的 api 层是一堆「薄包装」——每个导出函数就是一句
 * {@code return request({url, method, ...})}。这些文件最容易出的错是
 * 拼错 url、写错动词、忘了插值 id、两个接口撞到同一个路由上，
 * 而这些错误在运行时才会暴露。</p>
 *
 * <p>这里把「每个导出函数」都跑一遍，断言它们共同遵守的契约：</p>
 * <ol>
 *   <li>恰好打一次 request（多打或漏打都不对）</li>
 *   <li>url 是非空字符串，且以模块前缀开头（网关只放行 /opc/**）</li>
 *   <li>url 里没有没插值干净的占位符（"undefined" / "NaN" / "{id}"）</li>
 *   <li>method 是合法动词</li>
 *   <li>request 的返回值被原样透出（包装层不吞结果）</li>
 *   <li>同一模块内，不同函数的 (url, method) 不重复（防复制粘贴撞路由）</li>
 * </ol>
 */
const VALID_METHODS = ['get', 'post', 'put', 'delete', 'patch']

/**
 * 逐个调用模块里的导出函数并检查契约。
 *
 * @param mod      api 模块的命名空间对象
 * @param prefix   该模块所有 url 都应带的前缀，例如 '/opc/erp/'
 */
export function expectApiContract(mod: Record<string, any>, prefix: string) {
  const requestMock = vi.mocked(request)
  const entries = Object.entries(mod).filter(([name, v]) => name !== 'default' && typeof v === 'function')

  expect(entries.length, '模块里应当至少有一个导出函数').toBeGreaterThan(0)

  const seen = new Map<string, string>()

  for (const [name, fn] of entries) {
    requestMock.mockClear()
    const sentinel = { code: 200, data: null }
    requestMock.mockReturnValue(sentinel as any)

    // 参数一律用 1 占位：api 层不校验参数（也不读参数上的属性），
    // 占位值足够把 url 拼出来供断言。
    const args = new Array((fn as any).length).fill(1)
    const ret = (fn as any)(...args)

    expect(requestMock, `${name} 应该恰好打一次 request`).toHaveBeenCalledTimes(1)

    const cfg = requestMock.mock.calls[0][0] as any
    expect(typeof cfg.url, `${name} 的 url 必须是字符串`).toBe('string')
    expect(cfg.url.length, `${name} 的 url 不能为空`).toBeGreaterThan(0)
    expect(cfg.url.startsWith(prefix), `${name} 的 url 应以 ${prefix} 开头，实际 ${cfg.url}`).toBe(true)
    expect(cfg.url, `${name} 的 url 里有没插值干净的占位符`).not.toMatch(/undefined|NaN|\{|\}/)
    expect(VALID_METHODS, `${name} 的 method 非法：${cfg.method}`).toContain(cfg.method)
    expect(ret, `${name} 应该把 request 的返回值原样透出`).toBe(sentinel)

    const key = `${cfg.method} ${cfg.url}`
    expect(seen.has(key), `${name} 与 ${seen.get(key)} 撞在了同一个路由 ${key}`).toBe(false)
    seen.set(key, name)
  }
}

/**
 * 断言某个 api 函数打出的 request 配置。
 *
 * <p>用于「手工点名」的关键接口 —— 这些接口的 url / method 写错了
 * 业务会直接跑偏（例如把切换租户的请求体字段写成 companyId，
 * 会被租户守卫改写掉）。</p>
 */
export function expectRequest(fn: () => any, expected: Record<string, any>) {
  const requestMock = vi.mocked(request)
  requestMock.mockClear()
  requestMock.mockReturnValue({ code: 200 } as any)
  fn()
  expect(requestMock).toHaveBeenCalledTimes(1)
  const cfg = requestMock.mock.calls[0][0] as any
  for (const [k, v] of Object.entries(expected)) {
    if (v instanceof RegExp) {
      expect(String(cfg[k]), `${k} 应匹配 ${v}，实际 ${cfg[k]}`).toMatch(v)
    } else {
      expect(cfg[k], `${k} 不匹配`).toEqual(v)
    }
  }
}
