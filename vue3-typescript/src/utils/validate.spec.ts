import { describe, expect, it } from 'vitest'
import {
  isArray,
  isEmpty,
  isExternal,
  isHttp,
  isPathMatch,
  isString,
  validAlphabets,
  validEmail,
  validLowerCase,
  validUpperCase,
  validURL,
  validUsername,
} from '@/utils/validate'

describe('isPathMatch', () => {
  it('matches simple wildcard', () => {
    expect(isPathMatch('/user/*', '/user/123')).toBe(true)
  })

  it('does not cross slashes with single star', () => {
    expect(isPathMatch('/user/*', '/user/123/edit')).toBe(false)
  })

  it('matches double-star across segments', () => {
    expect(isPathMatch('/user/**', '/user/123/edit')).toBe(true)
  })

  it('matches single-character ?', () => {
    expect(isPathMatch('/user/?', '/user/1')).toBe(true)
  })

  it('rejects multi-character for ?', () => {
    expect(isPathMatch('/user/?', '/user/12')).toBe(false)
  })

  it('escapes regex specials (.)', () => {
    expect(isPathMatch('/v1.0/x', '/v1.0/x')).toBe(true)
    expect(isPathMatch('/v1.0/x', '/v1X0/x')).toBe(false)
  })

  it('returns false for non-matching', () => {
    expect(isPathMatch('/foo/*', '/bar/1')).toBe(false)
  })

  it('matches literal path with no wildcards', () => {
    expect(isPathMatch('/foo', '/foo')).toBe(true)
    expect(isPathMatch('/foo', '/bar')).toBe(false)
  })
})

describe('isEmpty', () => {
  it('returns true for null', () => expect(isEmpty(null)).toBe(true))
  it('returns true for undefined', () => expect(isEmpty(undefined)).toBe(true))
  it('returns true for empty string', () => expect(isEmpty('')).toBe(true))
  it('returns true for the literal string "undefined"', () =>
    expect(isEmpty('undefined')).toBe(true))
  // NOTE: `isEmpty` uses `==` coercion; known quirks:
  //  - `0 == ""` is true  → isEmpty(0) returns true
  //  - `[] == ""` is true → isEmpty([]) returns true
  // These are documented as bugs to fix; W11 pins current behavior.
  it('returns true for 0 (== "" coercion quirk)', () => expect(isEmpty(0)).toBe(true))
  it('returns true for empty array (== "" coercion quirk)', () =>
    expect(isEmpty([])).toBe(true))
  it('returns false for non-empty string', () =>
    expect(isEmpty('hello')).toBe(false))
  it('returns false for non-empty array', () =>
    expect(isEmpty([1, 2])).toBe(false))
})

describe('isHttp', () => {
  it('matches http://', () => expect(isHttp('http://a.com')).toBe(true))
  it('matches https://', () => expect(isHttp('https://a.com')).toBe(true))
  it('returns false for protocol-relative', () => expect(isHttp('//a.com')).toBe(false))
  it('returns false for plain path', () => expect(isHttp('/foo')).toBe(false))
  it('returns false for ftp://', () => expect(isHttp('ftp://a.com')).toBe(false))
})

describe('isExternal', () => {
  it('matches http(s)', () => expect(isExternal('https://a.com')).toBe(true))
  it('matches mailto:', () => expect(isExternal('mailto:foo@bar.com')).toBe(true))
  it('matches tel:', () => expect(isExternal('tel:13800000000')).toBe(true))
  it('returns false for relative', () => expect(isExternal('/foo')).toBe(false))
  it('returns false for plain text', () => expect(isExternal('foo')).toBe(false))
})

describe('validUsername', () => {
  it('accepts admin', () => expect(validUsername('admin')).toBe(true))
  it('accepts editor', () => expect(validUsername('editor')).toBe(true))
  it('rejects unknown', () => expect(validUsername('root')).toBe(false))
  it('trims whitespace', () => expect(validUsername('  admin  ')).toBe(true))
  it('is case-sensitive', () => expect(validUsername('Admin')).toBe(false))
})

describe('validURL', () => {
  it('accepts https URL', () =>
    expect(validURL('https://example.com/a/b')).toBe(true))
  // NOTE: regex requires a TLD, so bare hostnames like "localhost" are rejected.
  it('accepts http with port and TLD', () =>
    expect(validURL('http://example.com:8080/api')).toBe(true))
  it('rejects http with bare hostname (no TLD)', () =>
    expect(validURL('http://localhost:8080/api')).toBe(false))
  it('accepts ftp URL', () => expect(validURL('ftp://example.com')).toBe(true))
  it('rejects plain string', () => expect(validURL('not-a-url')).toBe(false))
  it('rejects bare domain without protocol', () =>
    expect(validURL('example.com')).toBe(false))
})

describe('validLowerCase', () => {
  it('accepts pure lowercase', () => expect(validLowerCase('abc')).toBe(true))
  it('rejects uppercase', () => expect(validLowerCase('Abc')).toBe(false))
  it('rejects digits', () => expect(validLowerCase('abc1')).toBe(false))
  it('rejects empty', () => expect(validLowerCase('')).toBe(false))
})

describe('validUpperCase', () => {
  it('accepts pure uppercase', () => expect(validUpperCase('ABC')).toBe(true))
  it('rejects lowercase', () => expect(validUpperCase('Abc')).toBe(false))
  it('rejects digits', () => expect(validUpperCase('ABC1')).toBe(false))
  it('rejects empty', () => expect(validUpperCase('')).toBe(false))
})

describe('validAlphabets', () => {
  it('accepts mixed case', () => expect(validAlphabets('AbCdEf')).toBe(true))
  it('rejects digits', () => expect(validAlphabets('abc1')).toBe(false))
  it('rejects non-letter chars', () =>
    expect(validAlphabets('abc-def')).toBe(false))
  it('rejects empty', () => expect(validAlphabets('')).toBe(false))
})

describe('validEmail', () => {
  it('accepts plain email', () => expect(validEmail('foo@bar.com')).toBe(true))
  it('accepts subdomain', () =>
    expect(validEmail('foo@mail.bar.co.uk')).toBe(true))
  it('accepts quoted local', () => expect(validEmail('"a.b"@x.com')).toBe(true))
  it('rejects missing @', () => expect(validEmail('foobar.com')).toBe(false))
  it('rejects missing domain', () => expect(validEmail('foo@')).toBe(false))
})

describe('isString', () => {
  it('accepts string literal', () => expect(isString('hello')).toBe(true))
  it('accepts String object', () => expect(isString(new String('x'))).toBe(true))
  it('rejects number', () => expect(isString(123)).toBe(false))
  it('rejects null', () => expect(isString(null)).toBe(false))
  it('rejects undefined', () => expect(isString(undefined)).toBe(false))
})

describe('isArray', () => {
  it('accepts array literal', () => expect(isArray([])).toBe(true))
  it('accepts Array constructor', () => expect(isArray(new Array(3))).toBe(true))
  it('rejects object', () => expect(isArray({})).toBe(false))
  it('rejects string', () => expect(isArray('abc')).toBe(false))
  it('rejects null', () => expect(isArray(null)).toBe(false))
})
