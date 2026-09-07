// W14 — src/utils/index.spec.ts.
// Pinned behaviors:
// - formatDate: null/undefined/'' → ''; valid input → 'YYYY-MM-DD HH:MM:SS'
// - formatTime: relative buckets (<30s 刚刚 / <1h N分钟前 / <24h N小时前 / <48h
//   1天前 / else 'M月D日H时M分' or parseTime format); 10-digit unix seconds
//   are multiplied by 1000 (else branch goes to '+time')
// - getQueryObject: parses url.lastIndexOf('?')+1; defaults to
//   window.location.href when url is null/undefined
// - byteLength: ASCII char count; BMP chars in 0x800-0xffff add 2
// - cleanArray: filters out falsy
// - param: skips undefined; encodes key + value; cleanArray filters ''s
// - param2Obj: parses '?...' section; '+' decoded as ' '; URL without '?'
//   returns {}
// - html2Text: extracts textContent / innerText from div
// - objectMerge: source arrays short-circuit to source.slice() (no merge);
//   nested objects recurse; non-object target is reset to {}
// - toggleClass: adds with leading space when absent, removes when present;
//   no-ops on null element
// - hasClass / addClass / removeClass: standard classList helpers
// - getTime(type='start') → 90 days ago; default → today midnight
// - deepClone: deep-clones object/array; throws on null/undefined/0/false
// - uniqueArr: dedup via Set
// - createUniqueString: returns base32-ish string, unique across calls
// - makeMap: builds predicate from comma-separated string; optional lowercase
// - titleCase / camelCase / isNumberStr: string transforms
// - constants: exportDefault, beautifierConf shape
import { beforeEach, describe, expect, it } from 'vitest'

import {
  formatDate,
  formatTime,
  getQueryObject,
  byteLength,
  cleanArray,
  param,
  param2Obj,
  html2Text,
  objectMerge,
  toggleClass,
  getTime,
  deepClone,
  uniqueArr,
  createUniqueString,
  hasClass,
  addClass,
  removeClass,
  makeMap,
  titleCase,
  camelCase,
  isNumberStr,
  exportDefault,
  beautifierConf,
} from '@/utils/index'

// Helper: format a timestamp as the local-time string formatDate() emits.
// Tests can't pin a hardcoded UTC time because happy-dom uses the host TZ
// (UTC+8 on this Windows box). Compute the expected value dynamically.
const localDate = (ts: number): string => {
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  )
}

describe('formatDate()', () => {
  it('1. returns "" for null', () => {
    expect(formatDate(null)).toBe('')
  })

  it('2. returns "" for undefined', () => {
    expect(formatDate(undefined)).toBe('')
  })

  it('3. returns "" for empty string', () => {
    expect(formatDate('')).toBe('')
  })

  it('4. formats ISO string to "YYYY-MM-DD HH:MM:SS"', () => {
    // Pin local time (test host is UTC+8 so ISO Z time gets +8h offset).
    const ts = Date.parse('2024-01-15T13:45:30Z')
    expect(formatDate('2024-01-15T13:45:30Z')).toBe(localDate(ts))
  })

  it('5. formats unix-ms timestamp number', () => {
    // 1705320000000 = 2024-01-15T12:00:00Z
    expect(formatDate(1705320000000)).toBe(localDate(1705320000000))
  })
})

describe('formatTime()', () => {
  it('6. < 30 sec → "刚刚"', () => {
    expect(formatTime(Date.now() - 1000)).toBe('刚刚')
  })

  it('7. < 1 hour → "N分钟前"', () => {
    // 5 minutes ago
    const fiveMinAgo = Date.now() - 5 * 60 * 1000
    expect(formatTime(fiveMinAgo)).toBe('5分钟前')
  })

  it('8. < 24 hours → "N小时前"', () => {
    // 3 hours ago
    const threeHoursAgo = Date.now() - 3 * 3600 * 1000
    expect(formatTime(threeHoursAgo)).toBe('3小时前')
  })

  it('9. < 48 hours → "1天前"', () => {
    // 30 hours ago
    const thirtyHoursAgo = Date.now() - 30 * 3600 * 1000
    expect(formatTime(thirtyHoursAgo)).toBe('1天前')
  })

  it('10. > 48 hours with no option → "M月D日H时M分" format', () => {
    // 3 days ago
    const threeDaysAgo = Date.now() - 3 * 24 * 3600 * 1000
    const out = formatTime(threeDaysAgo)
    expect(out).toMatch(/^\d+月\d+日\d+时\d+分$/)
  })

  it('11. 10-digit string (unix seconds) is multiplied by 1000', () => {
    // 1700000000 sec = 2023-11-14T22:13:20Z. > 48h ago → falls to option
    // branch (parseTime). Pin: option '{y}-{m}-{d} {h}:{i}:{s}' yields
    // local-time string equivalent to that exact moment.
    const ts = 1700000000 * 1000
    const out = formatTime('1700000000', '{y}-{m}-{d} {h}:{i}:{s}')
    expect(out).toBe(localDate(ts))
  })

  it('12. 13-digit string (unix ms) is parsed via unary plus', () => {
    const out = formatTime('1705320000000', '{y}-{m}-{d}')
    expect(out).toBe('2024-01-15')
  })
})

describe('getQueryObject()', () => {
  it('13. parses explicit URL query string into object', () => {
    expect(getQueryObject('https://x.com/a?foo=bar&n=42')).toEqual({
      foo: 'bar',
      n: '42',
    })
  })

  it('14. URL-encoded keys/values are decoded', () => {
    expect(getQueryObject('https://x.com/a?name=%E5%BC%A0%E4%B8%89&v=1')).toEqual({
      name: '张三',
      v: '1',
    })
  })

  it('15. URL without "?" → empty object', () => {
    expect(getQueryObject('https://x.com/a')).toEqual({})
  })

  it('16. defaults to window.location.href when url is null/undefined', () => {
    // happy-dom default location.href is 'about:blank' or similar — pin shape
    const before = window.location.href
    try {
      // happy-dom: window.location.href is writable in some setups; try to set
      // it to a known URL with a query.
      Object.defineProperty(window, 'location', {
        value: { href: 'https://x.com/page?id=99' },
        writable: true,
        configurable: true,
      })
      expect(getQueryObject()).toEqual({ id: '99' })
      expect(getQueryObject(undefined)).toEqual({ id: '99' })
    } finally {
      Object.defineProperty(window, 'location', {
        value: { href: before },
        writable: true,
        configurable: true,
      })
    }
  })
})

describe('byteLength()', () => {
  it('17. ASCII string → character count (1 byte each)', () => {
    expect(byteLength('hello')).toBe(5)
    expect(byteLength('')).toBe(0)
  })

  it('18. BMP chars > 0x7ff add 2 extra bytes (UTF-8 3-byte)', () => {
    // Each Chinese char is 1 JS char but 3 UTF-8 bytes → length + 2.
    expect(byteLength('中')).toBe(3)
    expect(byteLength('中文')).toBe(6)
  })

  it('19. Latin chars in 0x80-0x7ff add 1 extra byte (UTF-8 2-byte)', () => {
    // 'ñ' = U+00F1, UTF-8 = 2 bytes, length contribution = 1
    expect(byteLength('ñ')).toBe(2)
  })
})

describe('cleanArray()', () => {
  it('20. filters out falsy values (null, undefined, 0, "", false)', () => {
    expect(cleanArray([0, 1, false, 2, '', 3, null, undefined, NaN])).toEqual([
      1,
      2,
      3,
    ])
  })

  it('21. preserves truthy values', () => {
    expect(cleanArray(['a', 'b', 'c'])).toEqual(['a', 'b', 'c'])
  })
})

describe('param()', () => {
  it('22. simple object → "k1=v1&k2=v2" with URL-encoded values', () => {
    expect(param({ a: 1, b: 'x' })).toBe('a=1&b=x')
  })

  it('23. URL-encodes keys and values', () => {
    expect(param({ name: '张三', tag: 'a b' })).toBe('name=%E5%BC%A0%E4%B8%89&tag=a%20b')
  })

  it('24. skips undefined values (replaced with empty string and dropped by cleanArray)', () => {
    expect(param({ a: 1, b: undefined, c: 3 })).toBe('a=1&c=3')
  })

  it('25. empty object → ""', () => {
    expect(param({})).toBe('')
  })
})

describe('param2Obj()', () => {
  it('26. URL with query → object', () => {
    expect(param2Obj('https://x.com/a?foo=bar&n=42')).toEqual({
      foo: 'bar',
      n: '42',
    })
  })

  it('27. "+" is decoded as space', () => {
    expect(param2Obj('https://x.com/a?tag=hello+world')).toEqual({
      tag: 'hello world',
    })
  })

  it('28. URL without "?" → empty object', () => {
    expect(param2Obj('https://x.com/a')).toEqual({})
  })
})

describe('html2Text()', () => {
  it('29. extracts textContent from HTML string', () => {
    expect(html2Text('<p>hello <b>world</b></p>')).toBe('hello world')
  })

  it('30. plain text → unchanged', () => {
    expect(html2Text('just text')).toBe('just text')
  })
})

describe('objectMerge()', () => {
  it('31. simple merge, source wins for overlapping keys', () => {
    expect(objectMerge({ a: 1, b: 2 }, { b: 9, c: 3 })).toEqual({
      a: 1,
      b: 9,
      c: 3,
    })
  })

  it('32. nested objects are deep-merged', () => {
    expect(
      objectMerge({ a: { x: 1, y: 2 }, b: 1 }, { a: { y: 9, z: 3 } }),
    ).toEqual({ a: { x: 1, y: 9, z: 3 }, b: 1 })
  })

  it('33. array source short-circuits to source.slice() — no merge', () => {
    // Arrays are NOT merged element-wise; target arrays are ignored entirely.
    expect(objectMerge({ a: [1, 2, 3] }, { a: [9, 9] })).toEqual({ a: [9, 9] })
  })

  it('34. non-string/number target (e.g. string) is reset to {}', () => {
    // typeof string !== 'object' → target = {} → { a: 1 }
    expect(objectMerge('not-an-object', { a: 1 })).toEqual({ a: 1 })
  })

  it('34b. objectMerge(null, ...) THROWS — typeof null === "object" bypasses the reset guard', () => {
    // KNOWN BUG (do not fix per W12 rule): typeof null === 'object' so the
    // `if (typeof target !== 'object') target = {}` guard does NOT reset
    // null. The inner `target[property] = ...` then crashes with TypeError.
    expect(() => objectMerge(null, { a: 1 })).toThrow(TypeError)
  })
})

describe('DOM class helpers (toggleClass / hasClass / addClass / removeClass)', () => {
  let el: HTMLElement

  beforeEach(() => {
    el = document.createElement('div')
    el.className = 'base'
  })

  it('35. toggleClass adds className with leading space when absent', () => {
    toggleClass(el, 'active')
    expect(el.className).toBe('base active')
  })

  it('36. toggleClass removes className when present', () => {
    el.className = 'base active other'
    toggleClass(el, 'active')
    expect(el.className).toBe('base  other') // source leaves a double-space
  })

  it('37. toggleClass with null/undefined element → no-op (no throw)', () => {
    expect(() => toggleClass(null as any, 'x')).not.toThrow()
    expect(() => toggleClass(undefined as any, 'x')).not.toThrow()
  })

  it('38. hasClass matches whole-word class surrounded by whitespace', () => {
    el.className = 'foo bar baz'
    expect(hasClass(el, 'foo')).toBe(true)
    expect(hasClass(el, 'ba')).toBe(false) // partial match → false
    expect(hasClass(el, 'qux')).toBe(false)
  })

  it('39. addClass adds once (idempotent)', () => {
    addClass(el, 'x')
    expect(el.className).toBe('base x')
    addClass(el, 'x')
    expect(el.className).toBe('base x')
    addClass(el, 'y')
    expect(el.className).toBe('base x y')
  })

  it('40. removeClass strips whole-word class (replaces match with single space)', () => {
    // Source: `ele.className.replace(/(\s|^)b(\s|$)/, ' ')` — the leading
    // space is part of the match and consumed; only a single space remains.
    el.className = 'a b c'
    removeClass(el, 'b')
    expect(el.className).toBe('a c')
    removeClass(el, 'b')
    expect(el.className).toBe('a c') // idempotent (no-op when already gone)
  })
})

describe('getTime()', () => {
  it('41. type="start" returns ~90 days ago in ms', () => {
    const now = Date.now()
    const ninetyDays = 3600 * 1000 * 24 * 90
    expect(getTime('start')).toBeGreaterThan(now - ninetyDays - 100)
    expect(getTime('start')).toBeLessThan(now - ninetyDays + 100)
  })

  it('42. default (no arg) returns today midnight (server local time)', () => {
    const todayMidnight = new Date(new Date().toDateString()).getTime()
    expect(getTime()).toBe(todayMidnight)
  })
})

describe('deepClone()', () => {
  it('43. deep-clones nested object (no shared refs)', () => {
    const src = { a: 1, nested: { b: 2 } }
    const copy = deepClone(src)
    expect(copy).toEqual(src)
    expect(copy).not.toBe(src)
    expect(copy.nested).not.toBe(src.nested)
  })

  it('44. deep-clones array', () => {
    const src = [1, [2, 3], { a: 4 }]
    const copy = deepClone(src)
    expect(copy).toEqual(src)
    expect(copy).not.toBe(src)
    expect((copy as any[])[1]).not.toBe((src as any[])[1])
  })

  it('45. deepClone(null) throws TypeError — `typeof null === "object"` bypasses the !source guard', () => {
    // Source: `if (!source && typeof source !== 'object') throw` — for null,
    // !null is true AND typeof null === 'object', so the guard short-circuits
    // to false (no throw). The next line `(source as any).constructor` then
    // throws TypeError "Cannot read properties of null".
    expect(() => deepClone(null)).toThrow(TypeError)
  })

  it('46. throws on undefined via the explicit error-arguments guard', () => {
    // `!undefined` is true AND `typeof undefined !== 'object'` is true → the
    // guard fires with 'error arguments'.
    expect(() => deepClone(undefined)).toThrow('error arguments')
  })

  it('47. throws on 0 / false / "" via the explicit error-arguments guard', () => {
    // These are falsy AND typeof !== 'object', so the guard fires.
    expect(() => deepClone(0)).toThrow('error arguments')
    expect(() => deepClone(false)).toThrow('error arguments')
    expect(() => deepClone('')).toThrow('error arguments')
  })
})

describe('uniqueArr()', () => {
  it('48. dedups duplicate numbers', () => {
    expect(uniqueArr([1, 2, 2, 3, 1, 4])).toEqual([1, 2, 3, 4])
  })

  it('49. dedups mixed types', () => {
    expect(uniqueArr(['a', 'b', 'a', 1, 1, 'c'])).toEqual(['a', 'b', 1, 'c'])
  })
})

describe('createUniqueString()', () => {
  it('50. returns non-empty base32-ish string', () => {
    const s = createUniqueString()
    expect(typeof s).toBe('string')
    expect(s.length).toBeGreaterThan(0)
    expect(s).toMatch(/^[0-9a-z]+$/)
  })

  it('51. two consecutive calls return different values', () => {
    const a = createUniqueString()
    const b = createUniqueString()
    expect(a).not.toBe(b)
  })
})

describe('makeMap()', () => {
  it('52. basic lookup: known keys map to true, unknown keys map to undefined', () => {
    // Note: source returns `map[val]` directly — unknown keys return
    // `undefined` (not `false`), since the map is created with Object.create(null).
    // Callers should `!!inList(x)` if they need a boolean.
    const inList = makeMap('admin,user,guest')
    expect(inList('admin')).toBe(true)
    expect(inList('user')).toBe(true)
    expect(inList('guest')).toBe(true)
    expect(inList('other')).toBeUndefined()
  })

  it('53. case-sensitive by default', () => {
    const inList = makeMap('Admin,User')
    expect(inList('admin')).toBeUndefined()
    expect(inList('Admin')).toBe(true)
  })

  it('54. expectsLowerCase=true lowercases the QUERY side (caller must supply lowercase keys)', () => {
    // Source: `map[list[i]] = true` (no toLowerCase on insert) +
    // `(val) => map[val.toLowerCase()]`. So lowercase keys + any-case query
    // works.
    const inList = makeMap('admin,user', true)
    expect(inList('ADMIN')).toBe(true)
    expect(inList('Admin')).toBe(true)
    expect(inList('admin')).toBe(true)
    expect(inList('Other')).toBeUndefined()
  })

  it('54b. KNOWN BUG: expectsLowerCase does NOT lowercase the INSERT side', () => {
    // `makeMap('Admin,User', true)` stores keys 'Admin' / 'User' (case
    // preserved). Every query gets lowercased to 'admin' / 'user', which
    // never matches → always undefined. The fix would be to also lowercase
    // on insert: `map[list[i].toLowerCase()] = true`.
    const inList = makeMap('Admin,User', true)
    expect(inList('admin')).toBeUndefined()
    expect(inList('Admin')).toBeUndefined()
    expect(inList('ADMIN')).toBeUndefined()
  })
})

describe('titleCase()', () => {
  it('55. capitalizes first letter of string', () => {
    expect(titleCase('hello world')).toBe('Hello World')
  })

  it('56. capitalizes letters that follow a space', () => {
    expect(titleCase('foo bar baz')).toBe('Foo Bar Baz')
  })
})

describe('camelCase()', () => {
  it('57. converts underscore_case to camelCase', () => {
    expect(camelCase('hello_world')).toBe('helloWorld')
  })

  it('58. multi-segment underscores', () => {
    expect(camelCase('a_b_c_d')).toBe('aBCD')
  })
})

describe('isNumberStr()', () => {
  it('59. valid integer → true', () => {
    expect(isNumberStr('42')).toBe(true)
    expect(isNumberStr('-7')).toBe(true)
    expect(isNumberStr('+3')).toBe(true)
    expect(isNumberStr('0')).toBe(true)
  })

  it('60. valid decimal → true', () => {
    expect(isNumberStr('3.14')).toBe(true)
    expect(isNumberStr('-0.5')).toBe(true)
  })

  it('61. non-numeric string → false', () => {
    expect(isNumberStr('abc')).toBe(false)
    expect(isNumberStr('1.2.3')).toBe(false)
    expect(isNumberStr('')).toBe(false)
    expect(isNumberStr('1a')).toBe(false)
  })
})

describe('constants', () => {
  it('62. exportDefault is the literal "export default "', () => {
    expect(exportDefault).toBe('export default ')
  })

  it('63. beautifierConf has html + js config with key fields', () => {
    expect(beautifierConf.html.indent_size).toBe('2')
    expect(beautifierConf.html.wrap_line_length).toBe('110')
    expect(beautifierConf.js.indent_size).toBe('2')
    expect(beautifierConf.js.jslint_happy).toBe(true)
  })
})