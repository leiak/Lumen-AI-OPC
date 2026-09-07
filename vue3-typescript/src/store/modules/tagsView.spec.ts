// W12.3 — tagsView store spec. Largest store; ~28 tests covering add/del/
// persist semantics. Persistence is gated by useSettingsStore().tagsViewPersist.
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import cache from '@/plugins/cache'
import useSettingsStore from '@/store/modules/settings'
import useTagsViewStore from '@/store/modules/tagsView'

// We need to control settings.tagsViewPersist; mock the store with a vi.fn()
// so we can swap the implementation per test.
const tagsViewPersistRef = { tagsViewPersist: false }
vi.mock('@/store/modules/settings', () => ({
  default: vi.fn(() => tagsViewPersistRef),
}))

const useSettingsStoreMock = vi.mocked(useSettingsStore)

function mockPersist(enabled: boolean) {
  tagsViewPersistRef.tagsViewPersist = enabled
}

const v = (path: string, name: string, opts: Partial<{ title: string; noCache: boolean; affix: boolean; link: string }> = {}) => ({
  path,
  name,
  meta: {
    title: opts.title ?? name,
    ...(opts.noCache ? { noCache: true } : {}),
    ...(opts.affix ? { affix: true } : {}),
    ...(opts.link ? { link: opts.link } : {}),
  },
})

beforeEach(() => {
  setActivePinia(createPinia())
  cache.local.remove('tags-view-visited')
  tagsViewPersistRef.tagsViewPersist = false
  useSettingsStoreMock.mockClear()
})

// ============================================================================
// A. addView / addVisitedView / addCachedView / addAffixView / addIframeView
// ============================================================================

describe('A. addView variants', () => {
  it('1. addView pushes both visitedView AND cachedView by default', () => {
    const tv = useTagsViewStore()
    tv.addView(v('/a', 'A'))

    expect(tv.visitedViews).toHaveLength(1)
    expect(tv.cachedViews).toEqual(['A'])
  })

  it('2. addView skips cachedView when meta.noCache=true', () => {
    const tv = useTagsViewStore()
    tv.addView(v('/a', 'A', { noCache: true }))

    expect(tv.visitedViews).toHaveLength(1)
    expect(tv.cachedViews).toEqual([])
  })

  it('3. addVisitedView dedupes by path (no duplicate)', () => {
    const tv = useTagsViewStore()
    tv.addVisitedView(v('/a', 'A'))
    tv.addVisitedView(v('/a', 'A'))
    expect(tv.visitedViews).toHaveLength(1)
  })

  it('4. addCachedView dedupes by name (no duplicate)', () => {
    const tv = useTagsViewStore()
    tv.addCachedView(v('/a', 'A'))
    tv.addCachedView(v('/a', 'A'))
    expect(tv.cachedViews).toEqual(['A'])
  })

  it('5. addAffixView prepends (unshift) and tolerates missing meta.title', () => {
    const tv = useTagsViewStore()
    tv.addVisitedView(v('/regular', 'Regular'))
    tv.addAffixView({
      path: '/pinned',
      name: 'Pinned',
      meta: {} as any, // missing title
    } as any)
    // Pinned should be FIRST (unshift).
    expect(tv.visitedViews[0].path).toBe('/pinned')
    expect(tv.visitedViews[0].title).toBe('no-name')
  })

  it('6. addIframeView dedupes by path; appends only first occurrence', () => {
    const tv = useTagsViewStore()
    tv.addIframeView({ path: '/ext1', meta: { title: 'X' } } as any)
    tv.addIframeView({ path: '/ext1', meta: { title: 'X' } } as any)
    tv.addIframeView({ path: '/ext2', meta: {} } as any) // missing title → 'no-name'

    expect(tv.iframeViews).toHaveLength(2)
    expect(tv.iframeViews[1].title).toBe('no-name')
  })
})

// ============================================================================
// B. delView / delVisitedView / delCachedView / delIframeView
// ============================================================================

describe('B. delView variants', () => {
  it('7. delView removes from BOTH visitedViews and cachedViews', async () => {
    const tv = useTagsViewStore()
    tv.addView(v('/a', 'A'))
    tv.addView(v('/b', 'B'))

    await tv.delView(v('/a', 'A'))

    expect(tv.visitedViews.map((x) => x.path)).toEqual(['/b'])
    expect(tv.cachedViews).toEqual(['B'])
  })

  it('8. delVisitedView only removes from visitedViews', async () => {
    const tv = useTagsViewStore()
    tv.addView(v('/a', 'A'))

    await tv.delVisitedView(v('/a', 'A'))

    expect(tv.visitedViews).toEqual([])
    expect(tv.cachedViews).toEqual(['A']) // untouched
  })

  it('9. delCachedView only removes from cachedViews', async () => {
    const tv = useTagsViewStore()
    tv.addView(v('/a', 'A'))

    await tv.delCachedView(v('/a', 'A'))

    expect(tv.visitedViews).toHaveLength(1)
    expect(tv.cachedViews).toEqual([])
  })

  it('10. delView with unknown path is a no-op (no error)', async () => {
    const tv = useTagsViewStore()
    tv.addView(v('/a', 'A'))

    await tv.delView(v('/ghost', 'Ghost'))

    expect(tv.visitedViews).toHaveLength(1)
    expect(tv.cachedViews).toEqual(['A'])
  })

  it('11. delIframeView removes from iframeViews only', async () => {
    const tv = useTagsViewStore()
    tv.addIframeView({ path: '/ext', meta: {} } as any)
    tv.addVisitedView(v('/x', 'X'))

    await tv.delIframeView({ path: '/ext', meta: {} } as any)

    expect(tv.iframeViews).toEqual([])
    expect(tv.visitedViews).toHaveLength(1)
  })

  it('12. delVisitedView also removes the matching iframeView', async () => {
    // KNOWN: delVisitedView cross-cleans iframeViews. Pin this.
    const tv = useTagsViewStore()
    tv.addIframeView({ path: '/ext', meta: {} } as any)
    tv.addVisitedView({ path: '/ext', name: 'Ext', meta: { link: '/ext' } })

    await tv.delVisitedView({ path: '/ext', name: 'Ext', meta: { link: '/ext' } })

    expect(tv.iframeViews).toEqual([])
  })
})

// ============================================================================
// C. delOthers / delAll
// ============================================================================

describe('C. delOthers / delAll', () => {
  it('13. delOthersViews keeps only the target + affix tags', async () => {
    const tv = useTagsViewStore()
    tv.addVisitedView(v('/a', 'A'))
    tv.addVisitedView(v('/b', 'B'))
    tv.addAffixView({ path: '/pinned', name: 'Pinned', meta: { affix: true, title: 'Pinned' } } as any)
    tv.addVisitedView(v('/c', 'C'))

    await tv.delOthersViews(v('/b', 'B'))

    const paths = tv.visitedViews.map((x) => x.path)
    expect(paths).toContain('/pinned') // affix preserved
    expect(paths).toContain('/b') // target kept
    expect(paths).not.toContain('/a')
    expect(paths).not.toContain('/c')
  })

  it('14. delOthersVisitedViews filters visited list (no iframe cross-cleanup)', async () => {
    const tv = useTagsViewStore()
    tv.addVisitedView(v('/a', 'A'))
    tv.addVisitedView(v('/b', 'B'))
    tv.addIframeView({ path: '/ext-a', meta: {} } as any)

    await tv.delOthersVisitedViews(v('/b', 'B'))

    expect(tv.visitedViews.map((x) => x.path)).toEqual(['/b'])
    // iframeViews is filtered to ONLY match the target's path:
    expect(tv.iframeViews).toEqual([]) // /ext-a was filtered out (path !== /b)
  })

  it('15. delAllVisitedViews preserves affix tags and clears iframes', async () => {
    const tv = useTagsViewStore()
    tv.addVisitedView(v('/a', 'A'))
    tv.addAffixView({ path: '/pinned', name: 'Pinned', meta: { affix: true, title: 'Pinned' } } as any)
    tv.addIframeView({ path: '/ext', meta: {} } as any)

    await tv.delAllVisitedViews({})

    const paths = tv.visitedViews.map((x) => x.path)
    expect(paths).toEqual(['/pinned']) // affix preserved
    expect(tv.iframeViews).toEqual([])
  })

  it('16. delAllCachedViews empties cachedViews (no filtering)', async () => {
    const tv = useTagsViewStore()
    tv.addCachedView(v('/a', 'A'))
    tv.addCachedView(v('/b', 'B'))

    await tv.delAllCachedViews({})

    expect(tv.cachedViews).toEqual([])
  })

  it('17. delAllViews combines delAllVisited + delAllCached', async () => {
    const tv = useTagsViewStore()
    tv.addView(v('/a', 'A'))
    tv.addAffixView({ path: '/pinned', name: 'Pinned', meta: { affix: true, title: 'Pinned' } } as any)

    await tv.delAllViews({})

    expect(tv.visitedViews.map((x) => x.path)).toEqual(['/pinned'])
    expect(tv.cachedViews).toEqual([])
  })
})

// ============================================================================
// D. delRightTags / delLeftTags
// ============================================================================

describe('D. delRightTags / delLeftTags', () => {
  it('18. delRightTags removes right-of-target; keeps target + everything left + affixes', async () => {
    const tv = useTagsViewStore()
    tv.addVisitedView(v('/a', 'A'))
    tv.addVisitedView(v('/b', 'B'))
    tv.addVisitedView(v('/c', 'C'))
    tv.addAffixView({ path: '/pinned', name: 'Pinned', meta: { affix: true, title: 'P' } } as any)

    // After addAffixView, /pinned is FIRST. Order: [/pinned, /a, /b, /c]
    await tv.delRightTags(v('/b', 'B'))

    const paths = tv.visitedViews.map((x) => x.path)
    expect(paths).toEqual(['/pinned', '/a', '/b'])
  })

  it('19. delLeftTags removes left-of-target; keeps target + everything right + affixes', async () => {
    const tv = useTagsViewStore()
    tv.addVisitedView(v('/a', 'A'))
    tv.addVisitedView(v('/b', 'B'))
    tv.addVisitedView(v('/c', 'C'))
    tv.addAffixView({ path: '/pinned', name: 'Pinned', meta: { affix: true, title: 'P' } } as any)

    // Order: [/pinned, /a, /b, /c]
    await tv.delLeftTags(v('/b', 'B'))

    const paths = tv.visitedViews.map((x) => x.path)
    // /pinned is affix, /a is dropped, /b kept (target), /c kept (right)
    expect(paths).toEqual(['/pinned', '/b', '/c'])
  })

  it('20. delRightTags with unknown path: state unchanged, but Promise HANGS (KNOWN bug, not fixed)', () => {
    // KNOWN BUG: delRightTags has `if (index === -1) return` inside the
    // Promise executor — the Promise NEVER resolves. The synchronous state
    // change is correctly a no-op, but awaiting the call would hang.
    // We verify state without awaiting.
    const tv = useTagsViewStore()
    tv.addVisitedView(v('/a', 'A'))
    tv.addVisitedView(v('/b', 'B'))

    tv.delRightTags(v('/ghost', 'Ghost')) // do NOT await

    expect(tv.visitedViews.map((x) => x.path)).toEqual(['/a', '/b'])
  })
})

// ============================================================================
// E. updateVisitedView
// ============================================================================

describe('E. updateVisitedView', () => {
  it('21. updateVisitedView is BROKEN — `v = Object.assign(v, view)` reassigns local but does not mutate array element (KNOWN bug)', () => {
    // KNOWN BUG: source loop does `v = Object.assign(v, view)` which only
    // reassigns the local loop variable. Pin the broken behavior so a
    // future fix to `this.visitedViews[i] = Object.assign(...)` is flagged.
    const tv = useTagsViewStore()
    tv.addVisitedView(v('/a', 'A', { title: 'Old' }))

    tv.updateVisitedView({
      path: '/a',
      name: 'A',
      meta: { title: 'New' },
    })

    const target = tv.visitedViews.find((x) => x.path === '/a')
    expect(target!.title).toBe('Old') // unchanged due to bug
  })

  it('22. updateVisitedView with unknown path is a no-op (synchronous)', () => {
    const tv = useTagsViewStore()
    tv.addVisitedView(v('/a', 'A', { title: 'A' }))

    tv.updateVisitedView({
      path: '/ghost',
      name: 'Ghost',
      meta: { title: 'X' },
    })

    expect(tv.visitedViews.find((x) => x.path === '/a')!.title).toBe('A')
  })
})

// ============================================================================
// F. loadPersistedViews
// ============================================================================

describe('F. loadPersistedViews (cache.local persistence)', () => {
  it('23. tagsViewPersist=false: loadPersistedViews still LOADS (no flag gate — KNOWN)', () => {
    // KNOWN: the source has no `if (tagsViewPersist)` guard around
    // loadPersistedViews. Cache is loaded regardless; only `saveVisitedViews`
    // (called inside addVisitedView) respects the flag. This means with
    // persist=false, loaded views appear in-memory but are NOT re-saved.
    mockPersist(false)
    cache.local.setJSON('tags-view-visited', [
      { path: '/x', name: 'X', meta: { title: 'X' } },
    ])

    const tv = useTagsViewStore()
    tv.loadPersistedViews()

    expect(tv.visitedViews).toHaveLength(1)
  })

  it('24. empty cache: loadPersistedViews is a no-op', () => {
    mockPersist(true)
    cache.local.remove('tags-view-visited')

    const tv = useTagsViewStore()
    tv.loadPersistedViews()

    expect(tv.visitedViews).toEqual([])
  })

  it('25. tagsViewPersist=true + cached views: each gets added via addVisitedView (dedup applies)', () => {
    mockPersist(true)
    // Pre-add /x to the store BEFORE loading /y. The load loop dedupes /x
    // (already present) and adds /y fresh.
    const tv = useTagsViewStore()
    tv.addVisitedView(v('/x', 'X'))

    // Now seed cache with both /x and /y. (Note: previous addVisitedView
    // saved visitedViews (=[/x]) to cache — re-seed to add /y.)
    cache.local.setJSON('tags-view-visited', [
      { path: '/x', name: 'X', meta: { title: 'X' } },
      { path: '/y', name: 'Y', meta: { title: 'Y' } },
    ])

    tv.loadPersistedViews()

    // /x dedup'd (already present), /y added
    expect(tv.visitedViews.map((x) => x.path).sort()).toEqual(['/x', '/y'])
  })

  it('26. tagsViewPersist=true + affix=true cache items: addAffixView is NOT called (still uses addVisitedView)', () => {
    // KNOWN: loadPersistedViews calls addVisitedView for every cached view,
    // even those with meta.affix=true. Pin the actual behavior so a future
    // refactor that branches on affix is flagged.
    mockPersist(true)
    cache.local.setJSON('tags-view-visited', [
      { path: '/pinned', name: 'Pinned', meta: { affix: true, title: 'Pinned' } },
    ])

    const tv = useTagsViewStore()
    tv.loadPersistedViews()

    expect(tv.visitedViews).toHaveLength(1)
    expect(tv.visitedViews[0].path).toBe('/pinned')
  })
})

// ============================================================================
// G. cache.local side effects (saveVisitedViews / clearVisitedViews)
// ============================================================================

describe('G. persistence side effects', () => {
  it('27. addVisitedView saves to cache.local when tagsViewPersist=true', () => {
    mockPersist(true)
    const tv = useTagsViewStore()
    tv.addVisitedView(v('/a', 'A'))

    const stored = cache.local.getJSON('tags-view-visited')
    expect(stored).toEqual([
      expect.objectContaining({ path: '/a', name: 'A' }),
    ])
  })

  it('28. addVisitedView does NOT save to cache.local when tagsViewPersist=false', () => {
    mockPersist(false)
    const tv = useTagsViewStore()
    tv.addVisitedView(v('/a', 'A'))

    // Persist OFF → saveVisitedViews early-returns → cache stays empty.
    expect(cache.local.get('tags-view-visited')).toBeNull()
  })

  it('29. delAllVisitedViews clears cache.local when persist is enabled', async () => {
    mockPersist(true)
    const tv = useTagsViewStore()
    tv.addVisitedView(v('/a', 'A'))
    expect(cache.local.get('tags-view-visited')).not.toBeNull()

    await tv.delAllVisitedViews({})

    expect(cache.local.get('tags-view-visited')).toBeNull()
  })
})