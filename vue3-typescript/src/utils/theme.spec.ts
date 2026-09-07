// W12.2 — theme.ts spec. Color math + DOM CSS variable side effects.
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  hexToRgb,
  rgbToHex,
  mixHexColors,
  softenPrimaryForDark,
  getLightColor,
  getDarkColor,
  handleThemeStyle,
} from '@/utils/theme'

describe('theme', () => {
  // -----------------------------------------------------------------
  // hexToRgb / rgbToHex
  // -----------------------------------------------------------------
  describe('hexToRgb', () => {
    it('1. parses "#409eff" to [64, 158, 255] (0xff = 255, not 239)', () => {
      // Element-plus primary is #409eff = [64, 158, 255]. The plan stated
      // [64,158,239] (0xef); pin the actual contract here.
      expect(hexToRgb('#409eff')).toEqual([64, 158, 255])
    })

    it('2. 3-char hex ("FFF") is BROKEN — produces [255, NaN, NaN] (KNOWN bug, not fixed)', () => {
      // The source regex `/../g` over a 3-char string returns only the
      // first pair. Subsequent indices are undefined → parseInt(undefined,16)
      // = NaN. This is a real bug in theme.ts: callers must always pass
      // 6-char hex. Pin the actual behaviour so a future fix is flagged.
      expect(hexToRgb('FFF')).toEqual([255, NaN, NaN])
    })

    it('2b. accepts hex without leading "#"', () => {
      expect(hexToRgb('40ff80')).toEqual([64, 255, 128])
    })
  })

  describe('rgbToHex', () => {
    it('3. formats (64, 158, 255) to "#409eff"', () => {
      expect(rgbToHex(64, 158, 255)).toBe('#409eff')
    })

    it('4. pads single-digit channels (15 -> "0f")', () => {
      expect(rgbToHex(15, 15, 15)).toBe('#0f0f0f')
    })
  })

  // -----------------------------------------------------------------
  // mixHexColors
  // -----------------------------------------------------------------
  describe('mixHexColors', () => {
    it('5. t=0 returns fg unchanged', () => {
      expect(mixHexColors('#ff0000', '#00ff00', 0)).toBe('#ff0000')
    })

    it('6. t=1 returns bg unchanged', () => {
      expect(mixHexColors('#ff0000', '#00ff00', 1)).toBe('#00ff00')
    })

    it('7. t=0.5 of red+green yields mid-yellow', () => {
      expect(mixHexColors('#ff0000', '#00ff00', 0.5)).toBe('#808000')
    })
  })

  describe('softenPrimaryForDark', () => {
    it('8. mixes the input with #2d3036 at t=0.34', () => {
      const expected = mixHexColors('#409eff', '#2d3036', 0.34)
      expect(softenPrimaryForDark('#409eff')).toBe(expected)
    })
  })

  // -----------------------------------------------------------------
  // getLightColor / getDarkColor
  // -----------------------------------------------------------------
  describe('getLightColor (lighten)', () => {
    it('9. level=0 returns the input unchanged', () => {
      expect(getLightColor('#409eff', 0)).toBe('#409eff')
    })

    it('10. level=0.5 lightens each channel by ~half the way to 255', () => {
      // For [64, 158, 255]: r = floor((255-64)*0.5)+64 = 95+64 = 159 = 0x9f
      //                    g = floor((255-158)*0.5)+158 = 48+158 = 206 = 0xce
      //                    b = floor((255-255)*0.5)+255 = 0+255 = 255 = 0xff
      expect(getLightColor('#409eff', 0.5)).toBe('#9fceff')
    })

    it('11. level=1 returns pure white (#ffffff)', () => {
      expect(getLightColor('#409eff', 1)).toBe('#ffffff')
    })
  })

  describe('getDarkColor (darken)', () => {
    it('12. level=0 returns the input unchanged', () => {
      expect(getDarkColor('#409eff', 0)).toBe('#409eff')
    })

    it('13. level=0.5 darkens each channel by half', () => {
      // For [64, 158, 255]: r = floor(64*0.5)=32 = 0x20
      //                  g = floor(158*0.5)=79 = 0x4f
      //                  b = floor(255*0.5)=127 = 0x7f
      expect(getDarkColor('#409eff', 0.5)).toBe('#204f7f')
    })

    it('14. level=1 returns pure black (#000000)', () => {
      expect(getDarkColor('#409eff', 1)).toBe('#000000')
    })
  })

  // -----------------------------------------------------------------
  // handleThemeStyle — DOM side effects
  // -----------------------------------------------------------------
  describe('handleThemeStyle', () => {
    let setPropertySpy: ReturnType<typeof vi.spyOn>

    beforeEach(() => {
      setPropertySpy = vi.spyOn(document.documentElement.style, 'setProperty')
    })

    it('15. light mode (no .dark class): sets --el-color-primary to input + 9 light + 9 dark vars', () => {
      document.documentElement.classList.remove('dark')
      handleThemeStyle('#409eff')

      // 1 primary + 9 light + 9 dark = 19 setProperty calls total
      expect(setPropertySpy).toHaveBeenCalledTimes(19)
      expect(setPropertySpy).toHaveBeenCalledWith('--el-color-primary', '#409eff')
      // Spot-check one light and one dark variant name.
      expect(setPropertySpy).toHaveBeenCalledWith(
        '--el-color-primary-light-1',
        expect.stringMatching(/^#[0-9a-f]{6}$/),
      )
      expect(setPropertySpy).toHaveBeenCalledWith(
        '--el-color-primary-dark-9',
        expect.stringMatching(/^#[0-9a-f]{6}$/),
      )
    })

    it('16. dark mode (.dark class): primary is softened before variants are computed', () => {
      document.documentElement.classList.add('dark')
      handleThemeStyle('#409eff')

      const expectedSoften = softenPrimaryForDark('#409eff')
      expect(setPropertySpy).toHaveBeenCalledWith(
        '--el-color-primary',
        expectedSoften,
      )
      // Light/dark variants derive from the SOFTENED primary, not the input.
      expect(setPropertySpy).toHaveBeenCalledWith(
        '--el-color-primary-light-5',
        getLightColor(expectedSoften, 0.5),
      )
      document.documentElement.classList.remove('dark')
    })
  })
})