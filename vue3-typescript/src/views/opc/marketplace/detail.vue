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
            <el-button v-if="!installed" type="primary" :loading="installing" @click="onInstall">
              <el-icon><Download /></el-icon>&nbsp;安装
            </el-button>
            <el-button v-else type="success" plain @click="onUninstall">
              <el-icon><CircleCheck /></el-icon>&nbsp;已安装
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- 统计条 -->
      <div class="stat-strip">
        <div class="stat-cell">
          <div class="num">{{ pub.installCount || 0 }}</div>
          <div class="lbl">安装数</div>
        </div>
        <div class="stat-cell">
          <div class="num">{{ pub.subscriptionCount || 0 }}</div>
          <div class="lbl">订阅数</div>
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
const installed = ref(false)
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

/** 当前登录用户在我的评价里占一行（后端 list 返回裸 MpReview，只有 userId 可比对） */
const myReview = computed(() => reviews.value.find((r) => currentUserId.value && String(r.userId) === currentUserId.value))

// 路由约定 detail/:id(\\d+)；同时兼容 plan 里的 ?id= 直链
const pubId = computed(() => {
  const raw = route.params.id || route.query.id
  const n = Number(raw)
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
  loading.value = true
  try {
    // 公开详情端点：SQL 层硬过滤 status='PUBLISHED'，DRAFT 打开本页必然 404 —— 这是预期行为
    const res = await getPublication(pubId.value)
    pub.value = res.data || null
  } catch (e: any) {
    pub.value = null
    ElMessage.error('加载商品详情失败: ' + (e?.message || 'unknown'))
  } finally {
    loading.value = false
  }
  loadInstallStatus()
}

async function loadInstallStatus() {
  if (!pubId.value) return
  try {
    // 未安装时后端返回 R.ok(null)，data 为 null —— 不能当成错误
    const res = await installStatus(pubId.value)
    installed.value = !!res.data && res.data.status === 'INSTALLED'
  } catch {
    // 未关联企业 / 未登录时后端 400，这里静默降级为「未安装」
    installed.value = false
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
  } catch (e: any) {
    ElMessage.error('安装失败: ' + (e?.message || 'unknown'))
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
  } catch (e: any) {
    ElMessage.error('卸载失败: ' + (e?.message || 'unknown'))
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
  } catch {
    reviews.value = []
    reviewTotal.value = 0
  } finally {
    reviewLoading.value = false
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
    if (reviewForm.id) {
      await updateReview(reviewForm.id, { rating: reviewForm.rating, content: reviewForm.content })
      ElMessage.success('评价已更新')
    } else {
      await createReview({
        publicationId: pubId.value,
        rating: reviewForm.rating,
        content: reviewForm.content
      })
      ElMessage.success('评价已发布')
    }
    reviewDialog.value = false
    await loadReviews()
  } catch (e: any) {
    ElMessage.error('提交失败: ' + (e?.message || 'unknown'))
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
    await loadReviews()
  } catch (e: any) {
    ElMessage.error('删除失败: ' + (e?.message || 'unknown'))
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
  grid-template-columns: repeat(4, 1fr);
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