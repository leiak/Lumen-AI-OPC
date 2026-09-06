<template>
  <div class="share-poster">
    <div ref="posterCanvas" class="poster-canvas">
      <canvas ref="canvasRef" width="1080" height="1080" />
    </div>

    <div class="actions">
      <el-button type="primary" :loading="generating" @click="downloadPng">
        <el-icon><Download /></el-icon> 下载图片
      </el-button>
      <el-button @click="copyText">
        <el-icon><CopyDocument /></el-icon> 复制文字
      </el-button>
      <el-button @click="copyLink">
        <el-icon><Link /></el-icon> 复制链接
      </el-button>
    </div>

    <div class="share-text">
      <h4>📋 复制以下文字发到微信 / 朋友圈：</h4>
      <pre>{{ shareText }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts" name="SharePoster">
import { ref, onMounted, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, CopyDocument, Link } from '@element-plus/icons-vue'
import QRCode from 'qrcode'

const props = defineProps<{ invitation: any }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const generating = ref(false)

const shareUrl = ref('')
const shareText = ref('')

watch(() => props.invitation, (inv) => {
  if (!inv) return
  shareUrl.value = `${window.location.origin}/opc/invite?code=${inv.inviteCode}`
  shareText.value =
    `🎉 OPC-Agent-Community 邀请码：${inv.inviteCode}\n` +
    `一人公司 + N 个数字员工，让 AI 成为你的合伙人。\n` +
    `注册即送 50 元代金券 + 7 天免费体验。\n` +
    `${shareUrl.value}\n` +
    `(有效期至 ${new Date(inv.expireTime).toLocaleDateString('zh-CN')})`

  nextTick(drawPoster)
}, { immediate: true })

async function drawPoster() {
  if (!canvasRef.value) return
  const canvas = canvasRef.value
  const ctx = canvas.getContext('2d')!
  const W = 1080, H = 1080

  // 1. 背景渐变
  const grad = ctx.createLinearGradient(0, 0, W, H)
  grad.addColorStop(0, '#667eea')
  grad.addColorStop(1, '#764ba2')
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, W, H)

  // 2. 白色卡片
  const cardX = 60, cardY = 60, cardW = W - 120, cardH = H - 120, cardR = 32
  ctx.fillStyle = '#ffffff'
  roundRect(ctx, cardX, cardY, cardW, cardH, cardR)
  ctx.fill()

  // 3. Logo 区域
  ctx.fillStyle = '#667eea'
  roundRect(ctx, 100, 100, 80, 80, 16)
  ctx.fill()
  ctx.fillStyle = '#ffffff'
  ctx.font = 'bold 40px sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText('OPC', 140, 140)

  // 4. 标题
  ctx.fillStyle = '#1f2329'
  ctx.font = 'bold 48px sans-serif'
  ctx.textAlign = 'left'
  ctx.fillText('OPC-Agent-Community', 200, 140)

  // 5. 副标题
  ctx.fillStyle = '#4e5969'
  ctx.font = '24px sans-serif'
  ctx.fillText('一人公司 + N 个数字员工', 200, 180)

  // 6. 主标语
  ctx.fillStyle = '#1d2129'
  ctx.font = 'bold 56px sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText('让 AI 成为你的合伙人', W / 2, 380)

  // 7. 福利
  ctx.fillStyle = '#4e5969'
  ctx.font = '28px sans-serif'
  const benefits = [
    '✅ 注册即送 50 元代金券',
    '✅ 7 天免费体验全部 Agent',
    '✅ 1 对 1 新手引导',
  ]
  benefits.forEach((b, i) => ctx.fillText(b, W / 2, 460 + i * 44))

  // 8. 邀请码
  ctx.fillStyle = '#1d2129'
  ctx.font = '24px sans-serif'
  ctx.fillText('我的专属邀请码', W / 2, 640)

  ctx.fillStyle = '#667eea'
  ctx.font = 'bold 72px "Courier New", monospace'
  const code = props.invitation.inviteCode
  ctx.fillText(code, W / 2, 720)

  // 9. 二维码
  try {
    const qrDataUrl = await QRCode.toDataURL(shareUrl.value, {
      width: 360,
      margin: 1,
      color: { dark: '#1d2129', light: '#ffffff' }
    })
    const qrImg = await loadImage(qrDataUrl)
    const qrSize = 240, qrX = (W - qrSize) / 2, qrY = 780
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(qrX - 10, qrY - 10, qrSize + 20, qrSize + 20)
    ctx.drawImage(qrImg, qrX, qrY, qrSize, qrSize)
  } catch (e) {
    console.error('QR code generation failed', e)
  }

  // 10. 底部提示
  ctx.fillStyle = '#86909c'
  ctx.font = '20px sans-serif'
  ctx.fillText('微信扫码 / 长按识别', W / 2, 1050)
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = reject
    img.src = src
  })
}

async function downloadPng() {
  if (!canvasRef.value) return
  generating.value = true
  try {
    const dataUrl = canvasRef.value.toDataURL('image/png')
    const a = document.createElement('a')
    a.href = dataUrl
    a.download = `opc-invite-${props.invitation.inviteCode}.png`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    ElMessage.success('已下载')
  } catch (e: any) {
    ElMessage.error('下载失败：' + (e?.message || '未知错误'))
  } finally {
    generating.value = false
  }
}

async function copyText() {
  try {
    await navigator.clipboard.writeText(shareText.value)
    ElMessage.success('已复制邀请文案')
  } catch {
    ElMessage.error('复制失败')
  }
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    ElMessage.success('已复制邀请链接')
  } catch {
    ElMessage.error('复制失败')
  }
}

onMounted(() => {})
</script>

<style scoped lang="scss">
.share-poster {
  .poster-canvas {
    text-align: center;
    background: #f5f7fa;
    border-radius: 8px;
    padding: 16px;
    margin-bottom: 16px;

    canvas {
      max-width: 100%;
      height: auto;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
      border-radius: 8px;
    }
  }

  .actions {
    display: flex;
    gap: 8px;
    justify-content: center;
    margin-bottom: 16px;
    flex-wrap: wrap;
  }

  .share-text {
    background: #f5f7fa;
    border-radius: 8px;
    padding: 12px;

    h4 {
      margin: 0 0 8px;
      font-size: 13px;
      color: #4e5969;
    }

    pre {
      margin: 0;
      white-space: pre-wrap;
      word-break: break-all;
      font-family: inherit;
      font-size: 13px;
      color: #1d2129;
      line-height: 1.6;
    }
  }
}
</style>
