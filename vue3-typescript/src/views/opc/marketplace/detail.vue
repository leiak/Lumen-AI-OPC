<template>
  <div class="page detail-page" v-loading="loading">
    <template v-if="pub">
      <!-- 顶部 -->
      <el-card class="detail-hero">
        <div class="hero-content">
          <div class="big-icon">
            <el-image v-if="pub.iconUrl" :src="pub.iconUrl" fit="cover" class="icon-img" />
            <span v-else>{{ pub.productType === 'AGENT' ? '🤖' : '📦' }}</span>
          </div>
          <div class="hero-text">
            <h2>{{ pub.name }}</h2>
            <div class="hero-meta">
              <el-tag size="small">{{ pub.productType === 'AGENT' ? '智能体' : '模块' }}</el-tag>
              <el-tag size="small" type="info">{{ pub.categoryName || pub.categoryCode }}</el-tag>
              <span class="code">{{ pub.code }}</span>
            </div>
            <div class="hero-author">
              <el-avatar :size="24" :src="pub.authorAvatar" />
              <span>{{ pub.authorName || 'Anonymous' }}</span>
              <span v-if="pub.publishedAt" class="time">· 发布于 {{ pub.publishedAt }}</span>
            </div>
          </div>
          <div class="hero-actions">
            <div class="rating-block">
              <div class="num"><el-icon class="star"><StarFilled /></el-icon> {{ formatRating(pub.avgRating) }}</div>
              <div class="lbl">{{ pub.ratingCount || 0 }} 条评价</div>
            </div>
            <!-- installed === null 表示「安装状态还没问出来」,此时按钮禁用:
                 在状态回来之前画「安装」等于骗人 —— 装了的人点一下必然 409。
                 状态查询失败也停在 null:那种情况(未关联企业/未登录)下安装本来就会同样被拒。 -->
            <el-button
              v-if="!installed"
              type="primary"
              :loading="installing"
              :disabled="installed === null"
              @click="onInstall"
            >
              <el-icon><Download /></el-icon>&nbsp;安装
            </el-button>
            <!-- :loading 必须挂 —— request.ts 的防重复提交只拦 post/put,不拦 delete,
                 不挂的话连点两次会发出两个 DELETE:一个成功一个 409,同屏弹两个矛盾的 toast -->
            <el-button v-else type="success" plain :loading="installing" @click="onUninstall">
              <el-icon><CircleCheck /></el-icon>&nbsp;已安装
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- 统计条。刻意不展示「订阅数」:P1 没有订阅这个产品动作,后端
           MpPublicationMapper#incrementSubscriptionCount 也没有任何调用方(见其 javadoc)。
           展示一个永远为 0 的计数,比不展示更容易让人以为是 bug。P2 做订阅时再补。 -->
      <div class="stat-strip">
        <div class="stat-cell">
          <div class="num">{{ pub.installCount || 0 }}</div>
          <div class="lbl">安装数</div>
        </div>
        <div class="stat-cell">
          <div class="num">{{ pub.viewCount || 0 }}</div>
          <div class="lbl">浏览量</div>
        </div>
        <div class="stat-cell">
          <div class="num">{{ pub.ratingCount || 0 }}</div>
          <div class="lbl">评分数</div>
        </div>
      </div>

      <!-- Tabs -->
      <el-card class="tabs-card">
        <el-tabs v-model="activeTab">
          <!-- 简介 -->
          <el-tab-pane label="简介" name="intro">
            <h3 class="section">摘要</h3>
            <p class="summary">{{ pub.summary || '暂无摘要' }}</p>

            <h3 class="section">详细介绍</h3>
            <p class="long-desc">{{ pub.longDescription || '暂无详细介绍' }}</p>

            <template v-if="splitTags(pub.tags).length">
              <h3 class="section">标签</h3>
              <div class="tag-row">
                <el-tag v-for="t in splitTags(pub.tags)" :key="t" effect="plain">{{ t }}</el-tag>
              </div>
            </template>
          </el-tab-pane>

          <!-- 版本 -->
          <el-tab-pane label="版本信息" name="version">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="商品 ID">{{ pub.id }}</el-descriptions-item>
              <el-descriptions-item label="编码">{{ pub.code }}</el-descriptions-item>
              <el-descriptions-item label="类型">{{ pub.productType }}</el-descriptions-item>
              <el-descriptions-item label="类目">{{ pub.categoryName || pub.categoryCode }}</el-descriptions-item>
              <el-descriptions-item v-if="pub.subCategoryCode" label="子类目">
                {{ pub.subCategoryCode }}
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <!-- 与后端 PublicationStatus 对齐 -->
                <el-tag :type="statusTagType(pub.status)" size="small">{{ statusLabel(pub.status) }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item v-if="pub.agentDefinitionId" label="智能体定义 ID">
                {{ pub.agentDefinitionId }}
              </el-descriptions-item>
              <el-descriptions-item v-if="pub.moduleId" label="模块 ID">{{ pub.moduleId }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ pub.createTime || '—' }}</el-descriptions-item>
              <el-descriptions-item label="发布时间">{{ pub.publishedAt || '—' }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <!-- 评论 -->
          <el-tab-pane name="reviews">
            <template #label>
              评论 <span class="tab-count">({{ reviewTotal }})</span>
            </template>

            <div class="review-head">
              <div class="avg">
                <div class="avg-num">{{ formatRating(pub.avgRating) }}</div>
                <el-rate :model-value="Number(pub.avgRating || 0)" disabled allow-half />
              </div>
              <el-button v-if="!myReview" type="primary" @click="openReviewDialog">
                <el-icon><EditPen /></el-icon>&nbsp;我要评价
              </el-button>
              <el-button v-else type="primary" plain @click="openReviewDialog">
                <el-icon><EditPen /></el-icon>&nbsp;修改我的评价
              </el-button>
            </div>

            <div v-loading="reviewLoading" class="review-list">
              <el-empty v-if="!reviewLoading && reviews.length === 0" description="暂无评论" :image-size="60" />
              <div v-for="r in reviews" :key="r.id" class="review">
                <div class="review-meta">
                  <strong>{{ r.userName || 'User #' + r.userId }}</strong>
                  <el-rate :model-value="r.rating || 0" disabled size="small" />
                  <span class="time">{{ r.createTime }}</span>
                  <span class="ops">
                    <el-button
                      v-if="isMine(r)"
                      link
                      type="primary"
                      size="small"
                      @click="openReviewDialog(r)"
                    >
                      编辑
                    </el-button>
                    <el-button
                      v-if="isMine(r)"
                      link
                      type="danger"
                      size="small"
                      @click="onDeleteReview(r)"
                    >
                      删除
                    </el-button>
                  </span>
                </div>
                <p>{{ r.content || '（无文字评价）' }}</p>
              </div>
            </div>

            <div v-if="reviewTotal > reviewPageSize" class="pager">
              <el-pagination
                v-model:current-page="reviewPage"
                :page-size="reviewPageSize"
                :total="reviewTotal"
                layout="prev, pager, next"
                background
                @current-change="loadReviews"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>

    <!-- 评价弹窗 -->
    <el-dialog v-model="reviewDialog" :title="myReview ? '修改评价' : '发表评价'" width="520px">
      <el-form :model="reviewForm" label-width="70px">
        <el-form-item label="评分" required>
          <el-rate v-model="reviewForm.rating" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input
            v-model="reviewForm.content"
            type="textarea"
            :rows="4"
            maxlength="1024"
            show-word-limit
            placeholder="说说你的使用感受…"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialog = false">取消</el-button>
        <el-button type="primary" :loading="reviewSubmitting" @click="onSubmitReview">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { StarFilled, Download, CircleCheck, EditPen } from '@element-plus/icons-vue'
import useUserStore from '@/store/modules/user'
import {
  getPublication,
  installPublication,
  uninstallPublication,
  installStatus,
  listReviews,
  createReview,
  updateReview,
  deleteReview,
  type PublicationVO,
  type ReviewVO
} from '@/api/opc/marketplace'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const installing = ref(false)
const pub = ref<PublicationVO | null>(null)
/**
 * 三态而不是 boolean：null = 安装状态还在问 / 问失败了。
 * 之前默认 false，意味着按钮在状态回来之前就画成「安装」——已安装的用户点一下必然 409。
 */
const installed = ref<boolean | null>(null)
const activeTab = ref('intro')

const reviewLoading = ref(false)
const reviews = ref<ReviewVO[]>([])
const reviewTotal = ref(0)
const reviewPage = ref(1)
const reviewPageSize = 10
const userStore = useUserStore()
const currentUserId = computed(() => String(userStore.id || ''))

const reviewDialog = ref(false)
const reviewSubmitting = ref(false)
const reviewForm = reactive({ id: undefined as number | undefined, rating: 5, content: '' })

/**
 * 我自己的评价 —— **独立于当前页**存放（后端 list 返回裸 MpReview，只有 userId 可比对）。
 *
 * 之前是从 `reviews` 里 find 的，也就是「只认当前页」。后果：评价在第 3 页而用户停在第 1 页时，
 * 按钮显示「我要评价」，提交走 createReview → 后端 uk_review(publication_id,user_id) 冲突 409。
 *
 * P1 没有「我的评价」单条端点，只有分页的 /review/list/{publicationId}，所以首次加载时额外拉
 * 一页 pageSize=100（ReviewServiceImpl#listByPublication 的硬上限）来定位自己那条，之后不再重扫；
 * 翻页途中如果本页命中也会就地补上。超过 100 条评价的商品在这一点上仍可能漏判 —— 那时候应该
 * 补后端端点，而不是继续在前端堆扫描。
 */
const myReview = ref<ReviewVO | null>(null)
const myReviewScanned = ref(false)

// 路由固定是 detail/:id(\\d+)，命中该路由时 params.id 必然存在（?id=5 会匹配到别的路径），
// 所以这里只读 params.id，不做 query 兜底 —— 兜底分支永远走不到，留着只会误导。
const pubId = computed(() => {
  const n = Number(route.params.id)
  return Number.isFinite(n) && n > 0 ? n : undefined
})

function formatRating(r?: number | string | null) {
  if (r === undefined || r === null || r === '') return '—'
  return Number(r).toFixed(1)
}

function splitTags(tags?: string): string[] {
  if (!tags) return []
  return tags.split(/[,，]/).map((t) => t.trim()).filter(Boolean)
}

/** 与后端 PublicationStatus 枚举对齐 —— 不新增后端不存在的状态 */
function statusLabel(s?: string) {
  switch (s) {
    case 'DRAFT': return '草稿'
    case 'PENDING': return '待审核'
    case 'PUBLISHED': return '已发布'
    case 'SUSPENDED': return '已封禁'
    case 'DEPRECATED': return '已下架'
    default: return s || '未知'
  }
}

function statusTagType(s?: string) {
  switch (s) {
    case 'PUBLISHED': return 'success'
    case 'DRAFT': return 'info'
    case 'PENDING': return 'warning'
    case 'SUSPENDED': return 'danger'
    default: return 'info'
  }
}

function isMine(r: ReviewVO) {
  return !!currentUserId.value && String(r.userId) === currentUserId.value
}

async function load() {
  if (!pubId.value) {
    ElMessage.error('缺少商品 ID')
    return
  }
  // 安装状态查询只依赖 pubId（路由参数，进来就有），不等详情接口 —— 两个请求并发跑，
  // 「详情到了但按钮还画着安装」的窗口因此消失。之前是 await 完详情才查状态。
  loadInstallStatus()
  loadReviews()
  ensureMyReview()
  loading.value = true
  try {
    // 公开详情端点：SQL 层硬过滤 status='PUBLISHED'，DRAFT 打开本页必然 404 —— 这是预期行为
    const res = await getPublication(pubId.value)
    pub.value = res.data || null
  } catch {
    pub.value = null
    // 不拼 e?.message：request.ts 的 code!==200 分支 reject 的是裸字符串 'error'（没有 .message），
    // 拼上去只会得到 "加载商品详情失败: unknown"；真实文案它已经用通知弹过了。
    ElMessage.error('加载商品详情失败')
  } finally {
    loading.value = false
  }
}

async function loadInstallStatus() {
  if (!pubId.value) return
  try {
    // 未安装时后端 R.ok(null)，但本模块开了 default-property-inclusion: non_null，
    // 所以响应里**没有 data 这个键**，res.data 是 undefined —— 同样不能当成错误。
    const res = await installStatus(pubId.value)
    // 注意：MpInstall.status 的取值是 ACTIVE / UNINSTALLED（InstallServiceImpl 写入的也是
    // "ACTIVE" / "UNINSTALLED"），没有 INSTALLED 这个值。比对错了会导致刷新后按钮永远退回「安装」。
    installed.value = !!res.data && res.data.status === 'ACTIVE'
  } catch {
    // 未关联企业 / 未登录时后端 400。停在 null（按钮禁用）而不是假装 false：
    // 那种前提下的「安装」请求会以同样的理由被拒，画个能点的「安装」是骗人。
    installed.value = null
  }
}

async function onInstall() {
  if (!pubId.value) return
  installing.value = true
  try {
    await installPublication(pubId.value)
    installed.value = true
    if (pub.value) pub.value.installCount = (pub.value.installCount || 0) + 1
    ElMessage.success('安装成功')
  } catch {
    ElMessage.error('安装失败')
  } finally {
    installing.value = false
  }
}

async function onUninstall() {
  if (!pubId.value) return
  installing.value = true
  try {
    await uninstallPublication(pubId.value)
    installed.value = false
    if (pub.value) pub.value.installCount = Math.max(0, (pub.value.installCount || 0) - 1)
    ElMessage.success('已卸载')
  } catch {
    ElMessage.error('卸载失败')
  } finally {
    installing.value = false
  }
}

async function loadReviews() {
  if (!pubId.value) return
  reviewLoading.value = true
  try {
    const res = await listReviews(pubId.value, { page: reviewPage.value, pageSize: reviewPageSize })
    reviews.value = res.data?.rows || []
    reviewTotal.value = res.data?.total || 0
    // 本页命中就就地补上 —— myReview 不随翻页清空，所以翻到自己的那条时按钮自然切成「修改」
    const mine = reviews.value.find((r) => isMine(r))
    if (mine) myReview.value = mine
  } catch {
    reviews.value = []
    reviewTotal.value = 0
  } finally {
    reviewLoading.value = false
  }
}

/**
 * 首次加载时扫一遍全部评价来定位「我的那条」（见 myReview 的注释）。
 * 只跑一次；扫失败不影响评论列表，按钮退化成「我要评价」。
 */
async function ensureMyReview() {
  if (myReviewScanned.value || !pubId.value || !currentUserId.value) return
  myReviewScanned.value = true
  try {
    // 100 = ReviewServiceImpl#listByPublication 的 pageSize 上限，再大也会被夹回 100
    const res = await listReviews(pubId.value, { page: 1, pageSize: 100 })
    const mine = res.data?.rows?.find((r) => isMine(r))
    if (mine) myReview.value = mine
  } catch {
    // 静默：评论列表本身由 loadReviews 负责报错，这里只是补一个按钮态
  }
}

function openReviewDialog(existing?: ReviewVO) {
  if (existing) {
    reviewForm.id = existing.id
    reviewForm.rating = existing.rating || 5
    reviewForm.content = existing.content || ''
  } else {
    reviewForm.id = myReview.value?.id
    reviewForm.rating = myReview.value?.rating || 5
    reviewForm.content = myReview.value?.content || ''
  }
  reviewDialog.value = true
}

async function onSubmitReview() {
  if (!pubId.value) return
  if (reviewForm.rating < 1 || reviewForm.rating > 5) {
    ElMessage.warning('请选择 1-5 星评分')
    return
  }
  reviewSubmitting.value = true
  try {
    let saved: ReviewVO | undefined
    if (reviewForm.id) {
      saved = (await updateReview(reviewForm.id, { rating: reviewForm.rating, content: reviewForm.content })).data
      ElMessage.success('评价已更新')
    } else {
      saved = (await createReview({
        publicationId: pubId.value,
        rating: reviewForm.rating,
        content: reviewForm.content
      })).data
      ElMessage.success('评价已发布')
    }
    // 直接用响应更新 myReview：新行按 create_time DESC 排在最前，落在当前页上，
    // 但不依赖这一点 —— myReview 是独立状态，即使它不在本页也要记下来。
    if (saved) myReview.value = saved
    myReviewScanned.value = true
    reviewDialog.value = false
    await loadReviews()
  } catch {
    ElMessage.error('提交失败')
  } finally {
    reviewSubmitting.value = false
  }
}

async function onDeleteReview(r: ReviewVO) {
  try {
    await ElMessageBox.confirm(`确定删除这条评价？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteReview(r.id)
    ElMessage.success('已删除')
    // 后端是软删（HIDDEN），公开列表按 status='VISIBLE' 过滤，所以这一行确实会消失
    if (isMine(r)) myReview.value = null
    // 删掉本页最后一条且不在第 1 页 → 先退一页，否则会停在一个已经空了的页码上
    if (reviews.value.length === 1 && reviewPage.value > 1) {
      reviewPage.value -= 1
    }
    await loadReviews()
  } catch {
    ElMessage.error('删除失败')
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
$bp-sm: 768px;

.detail-page {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-hero {
  :deep(.el-card__body) {
    padding: 24px;
  }
}

.hero-content {
  display: flex;
  align-items: flex-start;
  gap: 24px;
}

.big-icon {
  font-size: 48px;
  width: 96px;
  height: 96px;
  flex: 0 0 96px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f4ff;
  border-radius: 16px;
  overflow: hidden;
}

.icon-img {
  width: 96px;
  height: 96px;
}

.hero-text {
  flex: 1;
  min-width: 0;

  h2 {
    margin: 0 0 8px;
    font-size: 22px;
  }
}

.hero-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;

  .code {
    font-size: 12px;
    color: #9ca3af;
    font-family: monospace;
  }
}

.hero-author {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #6b7280;

  .time {
    color: #9ca3af;
  }
}

.hero-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
  flex-shrink: 0;
}

.rating-block {
  text-align: right;

  .num {
    font-size: 22px;
    font-weight: 700;
    color: #1f2937;
  }

  .num .star {
    color: #f59e0b;
  }

  .lbl {
    font-size: 12px;
    color: #6b7280;
  }
}

.stat-strip {
  display: grid;
  // 与上方 3 个 stat-cell 对齐（安装数 / 浏览量 / 评分数）
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;

  .stat-cell {
    background: #fff;
    border-radius: 8px;
    padding: 16px 8px;
    text-align: center;
    border: 1px solid #ebeef5;

    .num {
      font-size: 22px;
      font-weight: 700;
      color: #1f2937;
    }

    .lbl {
      font-size: 12px;
      color: #6b7280;
      margin-top: 2px;
    }
  }
}

.tabs-card {
  :deep(.el-card__body) {
    padding: 8px 16px 16px;
  }
}

.section {
  margin: 16px 0 8px;
  font-size: 15px;
}

.summary,
.long-desc {
  margin: 0;
  color: #4b5563;
  line-height: 1.7;
  white-space: pre-wrap;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tab-count {
  color: #909399;
  font-size: 12px;
}

.review-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 0 16px;

  .avg {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .avg-num {
    font-size: 30px;
    font-weight: 700;
    color: #f59e0b;
  }
}

.review-list {
  min-height: 60px;
}

.review {
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
  margin-bottom: 8px;

  .review-meta {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 6px;
    flex-wrap: wrap;
  }

  .time {
    font-size: 12px;
    color: #9ca3af;
  }

  .ops {
    margin-left: auto;
    display: inline-flex;
    gap: 4px;
  }

  p {
    margin: 0;
    color: #374151;
    font-size: 13px;
    white-space: pre-wrap;
  }
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}

// ---------- 移动端 (< 768px) ----------
@media (max-width: #{$bp-sm - 0.02}) {
  .detail-page {
    padding: 12px;
    gap: 12px;
  }

  .hero-content {
    flex-direction: column;
    gap: 16px;
  }

  .detail-hero :deep(.el-card__body) {
    padding: 16px;
  }

  .big-icon {
    width: 72px;
    height: 72px;
    flex: 0 0 72px;
    font-size: 34px;
  }

  .icon-img {
    width: 72px;
    height: 72px;
  }

  .hero-actions {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    width: 100%;
  }

  .stat-strip {
    grid-template-columns: repeat(2, 1fr);
  }

  .review-head {
    flex-direction: column;
    align-items: flex-start;

    :deep(.el-button) {
      min-height: 44px;
      width: 100%;
    }
  }
}
</style>