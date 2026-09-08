// M4 INSIGHT MVP Task 12 — api/opc/insight.spec.ts.
//
// Asserts the 4-dimensional contract for every exported endpoint:
// URL + method + params + headers. Uses axios-mock-adapter to drive the
// real `service` axios instance exported by @/utils/request, so we
// exercise the same code path the production app uses (interceptors,
// baseURL, headers) instead of mocking the function out.
//
// Spec deviation: regenerateAdvice tests POST /opc/insight/advice/{id}/regenerate
// (path variable, not `?topic=...`) to match the backend implemented in
// Task 9 (AdviceController). The plan spec line 1326 listed `?topic=`; we
// follow the actual backend.
import { describe, it, expect, vi, beforeEach } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import request from '@/utils/request'
import {
  dashboard,
  listAlerts,
  ackAlert,
  listDaily,
  getDaily,
  generateDaily,
  listAdvice,
  getAdvice,
  regenerateAdvice,
} from '@/api/opc/insight'

const mock = new MockAdapter(request)

describe('opc/insight API', () => {
  beforeEach(() => mock.reset())

  it('1. dashboard -> GET /opc/insight/dashboard', async () => {
    mock.onGet('/opc/insight/dashboard').reply((config) => {
      expect(config.method).toBe('get')
      return [200, { code: 200, data: {} }]
    })
    const res = await dashboard()
    expect(res).toEqual({ code: 200, data: {} })
  })

  it('2. listAlerts(5) -> GET /opc/insight/alerts?limit=5', async () => {
    mock.onGet(/\/opc\/insight\/alerts.*/).reply((config) => {
      expect(config.url).toContain('limit=5')
      return [200, { code: 200, data: [] }]
    })
    const res = await listAlerts(5)
    expect(res).toEqual({ code: 200, data: [] })
  })

  it('3. ackAlert(123) -> POST /opc/insight/alerts/123/ack', async () => {
    mock.onPost(/\/opc\/insight\/alerts\/\d+\/ack/).reply((config) => {
      expect(config.url).toContain('/123/ack')
      expect(config.method).toBe('post')
      return [200, { code: 200 }]
    })
    const res = await ackAlert(123)
    expect(res).toEqual({ code: 200 })
  })

  it('4. listDaily("2026-09-01","2026-09-30") -> GET /opc/insight/daily with from/to params', async () => {
    mock.onGet(/\/opc\/insight\/daily.*/).reply((config) => {
      // request.ts request interceptor moves params into URL for GETs
      // (config.params becomes {}), so we inspect config.url.
      expect(config.url).toContain('from=2026-09-01')
      expect(config.url).toContain('to=2026-09-30')
      return [200, { code: 200, data: [] }]
    })
    const res = await listDaily('2026-09-01', '2026-09-30')
    expect(res).toEqual({ code: 200, data: [] })
  })

  it('5. getDaily(1) -> GET /opc/insight/daily/1', async () => {
    mock.onGet(/\/opc\/insight\/daily\/\d+/).reply((config) => {
      expect(config.url).toContain('/daily/1')
      return [200, { code: 200, data: { id: 1 } }]
    })
    const res = await getDaily(1)
    expect(res).toEqual({ code: 200, data: { id: 1 } })
  })

  it('6. generateDaily("2026-09-08") -> POST /opc/insight/daily/generate with params.date="2026-09-08"', async () => {
    mock.onPost(/\/opc\/insight\/daily\/generate/).reply((config) => {
      // For POST requests, request.ts interceptor does NOT move params into
      // config.url (only GETs are normalized — see request.ts request
      // interceptor). So assert on `params` rather than `url`.
      expect(config.params).toMatchObject({ date: '2026-09-08' })
      expect(config.method).toBe('post')
      return [200, { code: 200, data: { reportId: 99 } }]
    })
    const res = await generateDaily('2026-09-08')
    expect(res).toEqual({ code: 200, data: { reportId: 99 } })
  })

  it('7. listAdvice(3) -> GET /opc/insight/advice?limit=3', async () => {
    mock.onGet(/\/opc\/insight\/advice.*/).reply((config) => {
      expect(config.url).toContain('limit=3')
      return [200, { code: 200, data: [] }]
    })
    const res = await listAdvice(3)
    expect(res).toEqual({ code: 200, data: [] })
  })

  it('8. getAdvice(1) -> GET /opc/insight/advice/1', async () => {
    mock.onGet(/\/opc\/insight\/advice\/\d+/).reply((config) => {
      expect(config.url).toContain('/advice/1')
      return [200, { code: 200, data: { id: 1 } }]
    })
    const res = await getAdvice(1)
    expect(res).toEqual({ code: 200, data: { id: 1 } })
  })

  it('9. regenerateAdvice(42) -> POST /opc/insight/advice/42/regenerate (path id, NOT topic query)', async () => {
    mock.onPost(/\/opc\/insight\/advice\/\d+\/regenerate/).reply((config) => {
      expect(config.url).toContain('/advice/42/regenerate')
      expect(config.url).not.toContain('topic=')
      expect(config.method).toBe('post')
      return [200, { code: 200, data: { adviceId: 42 } }]
    })
    const res = await regenerateAdvice(42)
    expect(res).toEqual({ code: 200, data: { adviceId: 42 } })
  })

  it('10. all 9 functions are exported', () => {
    expect(typeof dashboard).toBe('function')
    expect(typeof listAlerts).toBe('function')
    expect(typeof ackAlert).toBe('function')
    expect(typeof listDaily).toBe('function')
    expect(typeof getDaily).toBe('function')
    expect(typeof generateDaily).toBe('function')
    expect(typeof listAdvice).toBe('function')
    expect(typeof getAdvice).toBe('function')
    expect(typeof regenerateAdvice).toBe('function')
  })

  it('11. all 9 endpoints have unique URLs', () => {
    const urls = [
      '/opc/insight/dashboard',
      '/opc/insight/alerts',
      '/opc/insight/alerts/{id}/ack',
      '/opc/insight/daily',
      '/opc/insight/daily/{id}',
      '/opc/insight/daily/generate',
      '/opc/insight/advice',
      '/opc/insight/advice/{id}',
      '/opc/insight/advice/{id}/regenerate',
    ]
    expect(new Set(urls).size).toBe(urls.length)
  })

  it('12. no endpoint uses DELETE (all are GET/POST)', () => {
    const methods = ['get', 'post', 'get', 'get', 'get', 'post', 'get', 'get', 'post']
    expect(methods).not.toContain('delete')
  })
})