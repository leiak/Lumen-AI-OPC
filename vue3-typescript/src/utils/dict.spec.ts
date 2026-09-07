// W16 — src/utils/dict.spec.ts.
//
// useDict(...args) is a Composition API helper that returns a toRefs object
// where each key is the dictType arg and the value is the cached/loaded dict
// entries. Source flow:
//   1. res.value[dictType] = []                     (initial empty)
//   2. dicts = useDictStore().getDict(dictType)    (cache check)
//   3. if (dicts) res.value[dictType] = dicts       (cache hit → use it)
//   4. else getDicts(dictType).then(resp => {...})  (cache miss → API)
//        - maps resp.data: dictLabel→label, dictValue→value,
//                          listClass→elTagType, cssClass→elTagClass
//        - calls useDictStore().setDict(dictType, ...) to cache
//   5. returns toRefs(res.value)
//
// Pinned behaviors:
// - Returns object with one entry per arg key
// - Cache hit skips getDicts; cache miss triggers one API call per arg
// - After API resolves, res.value[key] is set to the mapped array
// - setDict is called with the mapped array (caches for future calls)
// - resp.data undefined defaults to []
// - Empty array cache hit is treated as truthy (skips API) — quirk pinned
// - Synchronously, res.value[key] is []; updates after .then resolves
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  getDicts: vi.fn(),
}))

vi.mock('@/api/system/dict/data', () => ({ getDicts: mocks.getDicts }))

import { useDict } from '@/utils/dict'
import useDictStore from '@/store/modules/dict'

// Flush microtasks so the .then() callback inside useDict runs before assertions.
const flush = () => new Promise(r => setTimeout(r, 0))

beforeEach(() => {
  vi.clearAllMocks()
})

describe('useDict() — shape', () => {
  it('1. returns an object with one entry per arg key', () => {
    mocks.getDicts.mockResolvedValue({ data: [] })
    const r = useDict('sys_user_sex', 'sys_job_status')
    expect(Object.keys(r).sort()).toEqual(['sys_job_status', 'sys_user_sex'])
  })

  it('2. each entry is a ref-like object with a .value property', () => {
    mocks.getDicts.mockResolvedValue({ data: [] })
    const r = useDict('sys_user_sex')
    expect(r.sys_user_sex).toBeDefined()
    // Vue's ref-like has a `value` getter.
    expect('value' in r.sys_user_sex).toBe(true)
    expect(Array.isArray(r.sys_user_sex.value)).toBe(true)
  })

  it('3. no args → empty toRefs object, no API call', () => {
    const r = useDict()
    expect(Object.keys(r)).toHaveLength(0)
    expect(mocks.getDicts).not.toHaveBeenCalled()
  })
})

describe('useDict() — cache hit (no API call)', () => {
  it('4. cache hit: pre-populated store value is returned, getDicts NOT called', () => {
    const store = useDictStore()
    const cached = [{ value: '0', label: '男' }]
    store.setDict('sys_user_sex', cached)
    mocks.getDicts.mockResolvedValue({ data: [] })

    const r = useDict('sys_user_sex')
    expect(r.sys_user_sex.value).toEqual(cached)
    expect(mocks.getDicts).not.toHaveBeenCalled()
  })

  it('5. KNOWN QUIRK: empty array cache hit is truthy → skips API', () => {
    // Source: `if (dicts)` — `[]` is truthy in JS, so an empty cached array
    // prevents the API call even though the dict is "empty". Pin the behavior
    // so future refactors don't accidentally change the semantics.
    const store = useDictStore()
    store.setDict('empty_k', [])
    mocks.getDicts.mockResolvedValue({
      data: [{ dictLabel: 'late', dictValue: 'L' }],
    })

    const r = useDict('empty_k')
    expect(r.empty_k.value).toEqual([])
    expect(mocks.getDicts).not.toHaveBeenCalled()
  })
})

describe('useDict() — cache miss (API call)', () => {
  it('6. cache miss: calls getDicts once per arg', async () => {
    mocks.getDicts.mockResolvedValue({ data: [] })
    useDict('sys_user_sex', 'sys_job_status')
    await flush()
    expect(mocks.getDicts).toHaveBeenCalledTimes(2)
    expect(mocks.getDicts).toHaveBeenCalledWith('sys_user_sex')
    expect(mocks.getDicts).toHaveBeenCalledWith('sys_job_status')
  })

  it('7. synchronously returns [] for each key; updates after API resolves', async () => {
    mocks.getDicts.mockResolvedValue({
      data: [{ dictLabel: 'late', dictValue: 'L' }],
    })
    const r = useDict('k')
    // Synchronous: res.value['k'] was initialized to [] before the cache
    // check (which misses). The .then() callback hasn't run yet.
    expect(r.k.value).toEqual([])
    // Flush microtasks: the .then() updates res.value['k'].
    await flush()
    expect(r.k.value).toEqual([
      { label: 'late', value: 'L', elTagType: undefined, elTagClass: undefined },
    ])
  })

  it('8. maps resp.data fields: dictLabel→label, dictValue→value, listClass→elTagType, cssClass→elTagClass', async () => {
    mocks.getDicts.mockResolvedValue({
      data: [
        { dictLabel: '男', dictValue: '0', listClass: 'primary', cssClass: 'css1' },
        { dictLabel: '女', dictValue: '1', listClass: 'success', cssClass: 'css2' },
      ],
    })
    const r = useDict('sys_user_sex')
    await flush()
    expect(r.sys_user_sex.value).toEqual([
      { label: '男', value: '0', elTagType: 'primary', elTagClass: 'css1' },
      { label: '女', value: '1', elTagType: 'success', elTagClass: 'css2' },
    ])
  })

  it('9. resp.data = undefined defaults to [] (no throw, sets empty cache)', async () => {
    mocks.getDicts.mockResolvedValue({ data: undefined })
    const r = useDict('k')
    await flush()
    expect(r.k.value).toEqual([])
    // setDict was still called (with []), caching the empty result.
    const store = useDictStore()
    expect(store.getDict('k')).toEqual([])
  })

  it('10. after API resolves, setDict caches the result', async () => {
    mocks.getDicts.mockResolvedValue({
      data: [{ dictLabel: 'x', dictValue: '1', listClass: 'danger', cssClass: 'c' }],
    })
    useDict('sys_user_sex')
    await flush()
    const store = useDictStore()
    expect(store.getDict('sys_user_sex')).toEqual([
      { label: 'x', value: '1', elTagType: 'danger', elTagClass: 'c' },
    ])
  })
})

describe('useDict() — multiple keys', () => {
  it('11. multiple args in one call → multiple toRefs entries', async () => {
    mocks.getDicts.mockResolvedValue({ data: [] })
    const r = useDict('a', 'b', 'c')
    expect(Object.keys(r).sort()).toEqual(['a', 'b', 'c'])
    await flush()
    expect(mocks.getDicts).toHaveBeenCalledTimes(3)
  })

  it('12. second useDict call for the same key is a cache hit', async () => {
    mocks.getDicts.mockResolvedValueOnce({
      data: [{ dictLabel: 'first', dictValue: '1' }],
    })
    useDict('k')
    await flush()
    expect(mocks.getDicts).toHaveBeenCalledTimes(1)

    // Second call: cache is now populated, so no API call.
    const r = useDict('k')
    expect(r.k.value).toEqual([
      { label: 'first', value: '1', elTagType: undefined, elTagClass: undefined },
    ])
    expect(mocks.getDicts).toHaveBeenCalledTimes(1)
  })
})