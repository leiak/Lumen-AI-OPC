import { describe, expect, it } from 'vitest'
import {
  addDateRange,
  blobValidate,
  getNormalPath,
  handleTree,
  mergeRecursive,
  parseStrEmpty,
  parseTime,
  selectDictLabel,
  selectDictLabels,
  sprintf,
  tansParams,
} from '@/utils/ruoyi'

// Note: resetForm is intentionally skipped — it relies on Vue 2 Options-API
// (this.$refs). Out of scope for W11.

describe('parseTime', () => {
  it('returns null when called with no arguments', () => {
    expect(parseTime()).toBeNull()
  })

  it('returns null for 0 / empty string', () => {
    expect(parseTime(0)).toBeNull()
    expect(parseTime('')).toBeNull()
  })

  it('formats 10-digit epoch (seconds) with default pattern', () => {
    // 2026-09-07 12:34:56 UTC → in CST (UTC+8) = 20:34:56
    const epoch10 = Math.floor(Date.UTC(2026, 8, 7, 12, 34, 56) / 1000)
    const out = parseTime(epoch10)
    expect(out).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
    // 2026-09-07 in CST
    expect(out!.startsWith('2026-09-07')).toBe(true)
  })

  it('formats 13-digit epoch (ms) — date portion', () => {
    const epoch13 = Date.UTC(2026, 0, 1, 0, 0, 0)
    const out = parseTime(epoch13)
    // Don't assert exact hour because happy-dom may interpret epoch vs local time
    // differently; just verify date part and pattern shape.
    expect(out).toMatch(/^2026-01-01 \d{2}:\d{2}:\d{2}$/)
  })

  it('parses ISO-like string', () => {
    const out = parseTime('2026-09-07T12:34:56')
    // Source code converts 'T' to ' ' then `new Date()` — local-time interpretation
    // means the time-of-day may shift depending on environment TZ. Pin date + pattern.
    expect(out).toMatch(/^2026-09-07 \d{2}:\d{2}:\d{2}$/)
  })

  it('accepts custom pattern', () => {
    const epoch13 = Date.UTC(2026, 0, 1, 0, 0, 0)
    expect(parseTime(epoch13, '{y}/{m}/{d}')).toBe('2026/01/01')
  })

  it('substitutes weekday token {a}', () => {
    const epoch13 = Date.UTC(2026, 8, 6, 0, 0, 0) // Sunday → 9-6 is Sunday in CST
    const out = parseTime(epoch13, '{a}')
    // Date.UTC returns UTC midnight; in CST it is also Sunday
    expect(out).toBe('日')
  })

  it('accepts Date object', () => {
    const d = new Date(2026, 0, 1, 8, 0, 0) // local time
    expect(parseTime(d, '{y}-{m}-{d}')).toBe('2026-01-01')
  })
})

describe('selectDictLabel', () => {
  const dict = {
    a: { value: '0', label: '男' },
    b: { value: '1', label: '女' },
  }

  it('returns label when value matches', () => {
    expect(selectDictLabel(dict, '0')).toBe('男')
  })

  it('returns original value when no match', () => {
    expect(selectDictLabel(dict, '9')).toBe('9')
  })
})

describe('selectDictLabels', () => {
  const dict = {
    a: { value: '0', label: '男' },
    b: { value: '1', label: '女' },
    c: { value: '2', label: '未知' },
  }

  it('returns empty string for undefined', () => {
    expect(selectDictLabels(dict, undefined)).toBe('')
  })

  it('returns empty string for empty array', () => {
    expect(selectDictLabels(dict, [])).toBe('')
  })

  it('joins multi-value array with default comma separator', () => {
    expect(selectDictLabels(dict, ['0', '1'])).toBe('男,女')
  })

  it('joins string with default comma separator', () => {
    expect(selectDictLabels(dict, '0,1')).toBe('男,女')
  })

  it('honors custom separator', () => {
    expect(selectDictLabels(dict, '0|1', '|')).toBe('男|女')
  })
})

describe('parseStrEmpty', () => {
  it('returns "" for null', () => expect(parseStrEmpty(null)).toBe(''))
  it('returns "" for undefined', () => expect(parseStrEmpty(undefined)).toBe(''))
  it('returns "" for empty', () => expect(parseStrEmpty('')).toBe(''))
  it('returns "" for literal "undefined"', () =>
    expect(parseStrEmpty('undefined')).toBe(''))
  it('returns "" for literal "null"', () => expect(parseStrEmpty('null')).toBe(''))
  it('returns "0" for the number-as-string "0" (known spec)', () =>
    expect(parseStrEmpty('0')).toBe('0'))
  it('returns value for plain string', () =>
    expect(parseStrEmpty('hello')).toBe('hello'))
})

describe('handleTree', () => {
  it('builds a 2-level tree from flat list with defaults', () => {
    const data = [
      { id: 1, parentId: 0, name: 'root' },
      { id: 2, parentId: 1, name: 'child1' },
      { id: 3, parentId: 1, name: 'child2' },
      { id: 4, parentId: 0, name: 'root2' },
    ]
    const tree = handleTree(data)
    expect(tree).toHaveLength(2)
    expect(tree[0].children).toHaveLength(2)
    // NOTE: handleTree injects `children: []` on EVERY node in the first pass,
    // so roots with no children still get an empty array — not undefined.
    expect(tree[1].children).toEqual([])
  })

  it('honors custom id/parentId/children field names', () => {
    const data = [
      { uid: 1, pid: 0, label: 'r' },
      { uid: 2, pid: 1, label: 'c' },
    ]
    const tree = handleTree(data, 'uid', 'pid', 'kids')
    expect(tree).toHaveLength(1)
    expect(tree[0].kids).toEqual([{ uid: 2, pid: 1, label: 'c', kids: [] }])
  })

  it('returns empty array for empty input', () => {
    expect(handleTree([])).toEqual([])
  })

  it('treats orphan parents as roots', () => {
    const data = [
      { id: 1, parentId: 999, name: 'orphan' },
    ]
    const tree = handleTree(data)
    expect(tree).toHaveLength(1)
    expect(tree[0].id).toBe(1)
  })

  it('deeply nests grandchildren', () => {
    const data = [
      { id: 1, parentId: 0, name: 'root' },
      { id: 2, parentId: 1, name: 'mid' },
      { id: 3, parentId: 2, name: 'leaf' },
    ]
    const tree = handleTree(data)
    expect(tree).toHaveLength(1)
    expect(tree[0].children).toHaveLength(1)
    expect(tree[0].children[0].children).toHaveLength(1)
    expect(tree[0].children[0].children[0].name).toBe('leaf')
  })
})

describe('mergeRecursive', () => {
  it('overwrites primitives from target into source', () => {
    const out = mergeRecursive({ a: 1 }, { a: 2, b: 3 })
    expect(out).toEqual({ a: 2, b: 3 })
  })

  it('recurses into nested objects', () => {
    const src = { a: { x: 1, y: 2 } }
    const tgt = { a: { y: 20, z: 30 } }
    const out = mergeRecursive(src, tgt)
    expect(out).toEqual({ a: { x: 1, y: 20, z: 30 } })
  })

  it('handles missing nested object in source (catch branch)', () => {
    const out = mergeRecursive({}, { a: { b: 1 } })
    expect(out).toEqual({ a: { b: 1 } })
  })

  it('does not mutate source reference identity for keys', () => {
    const src = { a: 1 }
    mergeRecursive(src, { a: 2 })
    expect(src.a).toBe(2)
  })
})

describe('tansParams', () => {
  it('encodes simple key-value', () => {
    expect(tansParams({ foo: 'bar' })).toBe('foo=bar&')
  })

  it('skips null / empty / undefined values', () => {
    expect(tansParams({ a: null, b: '', c: undefined, d: 'x' })).toBe('d=x&')
  })

  it('encodes nested objects as bracket notation', () => {
    const out = tansParams({ filter: { name: 'foo', age: 0 } })
    // age=0 is falsy via `value !== 0` check? re-read code: `value[key] !== null && value[key] !== "" && typeof value[key] !== 'undefined'` — 0 passes through.
    expect(out).toContain('filter%5Bname%5D=foo')
    expect(out).toContain('filter%5Bage%5D=0')
  })

  it('URL-encodes special chars', () => {
    expect(tansParams({ q: 'a b/c' })).toBe('q=a%20b%2Fc&')
  })
})

describe('getNormalPath', () => {
  it('returns "" for empty input', () => expect(getNormalPath('')).toBe(''))
  it('returns input for "undefined"', () =>
    expect(getNormalPath('undefined')).toBe('undefined'))
  // NOTE: implementation uses `replace('//', '/')` (single-pass) so it only
  // collapses the first '//'; subsequent doubles remain. This pins current
  // behavior — actual bug should be fixed in a separate commit.
  it('collapses first double slash only (known spec)', () =>
    expect(getNormalPath('//a//b')).toBe('/a//b'))
  it('strips trailing slash', () => expect(getNormalPath('/a/b/')).toBe('/a/b'))
})

describe('blobValidate', () => {
  it('returns true for non-json blob', () => {
    expect(blobValidate(new Blob(['x'], { type: 'application/octet-stream' }))).toBe(true)
  })

  it('returns false for application/json blob', () => {
    expect(blobValidate(new Blob(['{}'], { type: 'application/json' }))).toBe(false)
  })

  it('returns true for empty blob (type="")', () => {
    expect(blobValidate(new Blob())).toBe(true)
  })
})

describe('sprintf', () => {
  it('substitutes %s placeholders', () => {
    expect(sprintf('hello %s world', 0, 'foo')).toBe('hello foo world')
  })

  it('returns "" if any placeholder has no arg', () => {
    expect(sprintf('a %s b %s', 0, 'x')).toBe('')
  })

  it('returns original if no placeholders', () => {
    expect(sprintf('plain text', 0)).toBe('plain text')
  })
})

describe('addDateRange', () => {
  it('writes beginTime/endTime when propName is undefined', () => {
    const out = addDateRange({}, ['2026-01-01', '2026-01-31'])
    expect(out.params).toEqual({ beginTime: '2026-01-01', endTime: '2026-01-31' })
  })

  it('writes begin{Prop}/end{Prop} when propName given', () => {
    const out = addDateRange({}, ['2026-01-01', '2026-01-31'], 'CreateTime')
    expect(out.params).toEqual({
      beginCreateTime: '2026-01-01',
      endCreateTime: '2026-01-31',
    })
  })

  it('initializes params when source has no params object', () => {
    const out = addDateRange({}, [])
    expect(out.params).toEqual({ beginTime: undefined, endTime: undefined })
  })
})
