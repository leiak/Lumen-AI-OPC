// W12.2 — dynamicTitle spec. Tests document.title mutation based on
// the settings store's flag + title value.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import useSettingsStore from '@/store/modules/settings'
import { useDynamicTitle } from '@/utils/dynamicTitle'

// Mock @/settings so we control defaultSettings.title (which would otherwise
// be import.meta.env.VITE_APP_TITLE — undefined in vitest).
vi.mock('@/settings', () => ({
  default: { title: '若依管理系统' },
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

  it('1. dynamicTitle=true with title="Dashboard" -> "Dashboard - 若依管理系统"', () => {
    const store = useSettingsStore()
    store.dynamicTitle = true
    store.title = 'Dashboard'

    useDynamicTitle()

    expect(document.title).toBe('Dashboard - 若依管理系统')
  })

  it('2. dynamicTitle=false -> falls back to defaultSettings.title alone', () => {
    const store = useSettingsStore()
    store.dynamicTitle = false
    store.title = 'Should be ignored'

    useDynamicTitle()

    expect(document.title).toBe('若依管理系统')
  })

  it('3. dynamicTitle=true with title="" -> "- 若依管理系统" (leading space stripped by happy-dom)', () => {
    // KNOWN: source code is `title + ' - ' + defaultSettings.title` so the
    // literal would be " - 若依管理系统" (with leading space). However,
    // happy-dom (and most browsers) trim leading/trailing whitespace when
    // setting document.title. We pin the observed browser-realistic value
    // so the test matches what users actually see in the tab.
    const store = useSettingsStore()
    store.dynamicTitle = true
    store.title = ''

    useDynamicTitle()

    expect(document.title).toBe('- 若依管理系统')
  })
})