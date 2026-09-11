// W17 — src/utils/dynamicTitle.spec.ts. W12.2 baseline was 3 tests covering
// the happy paths; W17 expands to comprehensive coverage.
//
// Source (14 lines, 4 lines of logic):
//   if (settingsStore.dynamicTitle) {
//     document.title = settingsStore.title + ' - ' + defaultSettings.title
//   } else {
//     document.title = defaultSettings.title
//   }
//
// Pinned behaviors:
// - dynamicTitle=true + title set → `<title> - <default>`
// - dynamicTitle=false → only `<default>`
// - dynamicTitle=true + title="" → "- <default>" (happy-dom strips leading space)
// - dynamicTitle=true + title=undefined → "undefined - <default>" (JS coercion)
// - dynamicTitle=true + title=null → "null - <default>" (JS coercion)
// - Title with Chinese / HTML / internal whitespace is preserved verbatim
//   (happy-dom strips ONLY leading/trailing whitespace, not internal)
// - setTitle(title) action captures title AND triggers useDynamicTitle()
// - Toggling dynamicTitle true↔false mid-flight changes title format
// - Multiple consecutive calls are idempotent (same document.title)
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import useSettingsStore from '@/store/modules/settings'
import { useDynamicTitle } from '@/utils/dynamicTitle'

// Mock @/settings so we control defaultSettings.title (which would otherwise
// be import.meta.env.VITE_APP_TITLE — undefined in vitest). dynamicTitle is
// also included so the settings store's default state has a meaningful
// boolean (otherwise store.dynamicTitle is undefined).
vi.mock('@/settings', () => ({
  default: {
    title: 'OPC 管理后台',
    dynamicTitle: false,
  },
}))

describe('dynamicTitle', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    // Pin a known starting title so we can detect mutations.
    document.title = 'placeholder'
  })

  afterEach(() => {
    document.title = 'placeholder'
  })

  // ---------------------------------------------------------------
  // W12.2 baseline — preserved
  // ---------------------------------------------------------------

  it('1. dynamicTitle=true with title="Dashboard" -> "Dashboard - OPC 管理后台"', () => {
    const store = useSettingsStore()
    store.dynamicTitle = true
    store.title = 'Dashboard'

    useDynamicTitle()

    expect(document.title).toBe('Dashboard - OPC 管理后台')
  })

  it('2. dynamicTitle=false -> falls back to defaultSettings.title alone', () => {
    const store = useSettingsStore()
    store.dynamicTitle = false
    store.title = 'Should be ignored'

    useDynamicTitle()

    expect(document.title).toBe('OPC 管理后台')
  })

  it('3. dynamicTitle=true with title="" -> "- OPC 管理后台" (leading space stripped by happy-dom)', () => {
    // KNOWN: source code is `title + ' - ' + defaultSettings.title` so the
    // literal would be " - OPC 管理后台" (with leading space). However,
    // happy-dom (and most browsers) trim leading/trailing whitespace when
    // setting document.title. We pin the observed browser-realistic value
    // so the test matches what users actually see in the tab.
    const store = useSettingsStore()
    store.dynamicTitle = true
    store.title = ''

    useDynamicTitle()

    expect(document.title).toBe('- OPC 管理后台')
  })

  // ---------------------------------------------------------------
  // Default state (no mutations)
  // ---------------------------------------------------------------

  it('4. fresh store: dynamicTitle=false, title="" — useDynamicTitle() sets ONLY defaultSettings.title', () => {
    const store = useSettingsStore()
    // Source-defined defaults (no mutations applied).
    expect(store.dynamicTitle).toBe(false)
    expect(store.title).toBe('')

    useDynamicTitle()

    expect(document.title).toBe('OPC 管理后台')
  })

  // ---------------------------------------------------------------
  // Title content preservation
  // ---------------------------------------------------------------

  it('5. dynamicTitle=true with Chinese title "用户管理" preserves Unicode verbatim', () => {
    const store = useSettingsStore()
    store.dynamicTitle = true
    store.title = '用户管理'

    useDynamicTitle()

    expect(document.title).toBe('用户管理 - OPC 管理后台')
  })

  it('6. dynamicTitle=true with HTML-looking title preserves verbatim (document.title is plain text)', () => {
    const store = useSettingsStore()
    store.dynamicTitle = true
    store.title = '<script>alert(1)</script>'

    useDynamicTitle()

    // document.title does NOT interpret HTML — the literal string is shown.
    // Pin to defend against any future sanitizer that would change behavior.
    expect(document.title).toBe('<script>alert(1)</script> - OPC 管理后台')
  })

  it('7. dynamicTitle=true with title="A B" preserves internal whitespace (no collapse)', () => {
    const store = useSettingsStore()
    store.dynamicTitle = true
    store.title = 'A  B' // double space between A and B

    useDynamicTitle()

    // happy-dom strips ONLY leading/trailing whitespace from document.title,
    // NOT internal whitespace. Source produces 'A  B - OPC 管理后台' — no
    // surrounding whitespace → exact preservation.
    expect(document.title).toBe('A  B - OPC 管理后台')
  })

  it('8. dynamicTitle=true with title containing quotes "She said \'hi\'" preserves verbatim', () => {
    const store = useSettingsStore()
    store.dynamicTitle = true
    store.title = "She said 'hi'"

    useDynamicTitle()

    expect(document.title).toBe("She said 'hi' - OPC 管理后台")
  })

  // ---------------------------------------------------------------
  // setTitle() roundtrip (the canonical integration path)
  // ---------------------------------------------------------------

  it('9. setTitle("Dashboard") with dynamicTitle=true: store.title + document.title both update', () => {
    const store = useSettingsStore()
    store.dynamicTitle = true

    store.setTitle('Dashboard')

    expect(store.title).toBe('Dashboard')
    // setTitle() calls useDynamicTitle() internally → document.title mutated.
    expect(document.title).toBe('Dashboard - OPC 管理后台')
  })

  it('10. setTitle("X") with dynamicTitle=false: store.title captured but document.title is default only', () => {
    const store = useSettingsStore()
    // dynamicTitle=false (default)

    store.setTitle('X')

    expect(store.title).toBe('X')
    // dynamicTitle is false → composite NOT applied; title goes nowhere.
    expect(document.title).toBe('OPC 管理后台')
  })

  it('11. setTitle called multiple times: document.title tracks the LATEST title', () => {
    const store = useSettingsStore()
    store.dynamicTitle = true

    store.setTitle('First')
    expect(document.title).toBe('First - OPC 管理后台')

    store.setTitle('Second')
    expect(document.title).toBe('Second - OPC 管理后台')

    store.setTitle('Third')
    expect(document.title).toBe('Third - OPC 管理后台')
  })

  // ---------------------------------------------------------------
  // Toggling dynamicTitle mid-flight
  // ---------------------------------------------------------------

  it('12. toggling dynamicTitle true→false mid-flight switches title from composite to default-only', () => {
    const store = useSettingsStore()
    store.dynamicTitle = true
    store.title = 'Profile'
    useDynamicTitle()
    expect(document.title).toBe('Profile - OPC 管理后台')

    store.dynamicTitle = false
    useDynamicTitle()

    expect(document.title).toBe('OPC 管理后台')
  })

  it('13. toggling dynamicTitle false→true mid-flight switches title from default-only to composite', () => {
    const store = useSettingsStore()
    store.dynamicTitle = false
    store.title = 'Settings'
    useDynamicTitle()
    expect(document.title).toBe('OPC 管理后台')

    store.dynamicTitle = true
    useDynamicTitle()

    expect(document.title).toBe('Settings - OPC 管理后台')
  })

  // ---------------------------------------------------------------
  // Idempotency
  // ---------------------------------------------------------------

  it('14. multiple consecutive useDynamicTitle() calls produce stable document.title', () => {
    const store = useSettingsStore()
    store.dynamicTitle = true
    store.title = 'Stable'

    useDynamicTitle()
    expect(document.title).toBe('Stable - OPC 管理后台')

    useDynamicTitle()
    expect(document.title).toBe('Stable - OPC 管理后台')

    useDynamicTitle()
    expect(document.title).toBe('Stable - OPC 管理后台')
  })

  // ---------------------------------------------------------------
  // Edge cases: undefined / null title
  // ---------------------------------------------------------------

  it('15. dynamicTitle=true with title=undefined produces "undefined - <default>" (JS coercion)', () => {
    // Pin the literal: JS coerces undefined → "undefined" string. This is
    // NOT a runtime error — title state is typed `string` but Pinia doesn't
    // strictly enforce, so undefined is allowed.
    const store = useSettingsStore()
    store.dynamicTitle = true
    store.title = undefined

    useDynamicTitle()

    expect(document.title).toBe('undefined - OPC 管理后台')
  })

  it('16. dynamicTitle=true with title=null produces "null - <default>" (JS coercion)', () => {
    const store = useSettingsStore()
    store.dynamicTitle = true
    store.title = null

    useDynamicTitle()

    expect(document.title).toBe('null - OPC 管理后台')
  })
})
