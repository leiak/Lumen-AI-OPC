<template>
  <div class="page market-page">
    <!-- Hero -->
    <el-card class="hero">
      <div class="hero-content">
        <div class="hero-text">
          <h1>🛍️ 开发者集市</h1>
          <p>开发者自己上架的智能体与功能模块 —— 发现、订阅、评价,审核通过即刻可见。平台自营的数字员工在<router-link to="/opc/agent/market">官方数字员工</router-link>。</p>
          <div class="hero-actions">
            <el-button type="primary" plain @click="router.push('/opc/marketplace/author-center')">我要当作者</el-button>
          </div>
        </div>
        <div class="hero-stats">
          <div class="stat">
            <div class="num">{{ total }}</div>
            <div class="label">在售商品</div>
          </div>
          <div class="stat">
            <div class="num">{{ categories.length }}</div>
            <div class="label">类目</div>
          </div>
          <div class="stat">
            <div class="num">{{ pageInstalls }}</div>
            <div class="label">本页安装</div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 商品类型 Tabs -->
    <el-card class="filters">
      <el-tabs v-model="query.productType" class="type-tabs" @tab-change="onProductTypeChange">
        <el-tab-pane label="全部" name="" />
        <el-tab-pane label="智能体" name="AGENT" />
        <el-tab-pane label="功能模块" name="MODULE" />
      </el-tabs>

      <el-form :inline="true" :model="query" class="filter-form" @submit.prevent>
        <el-form-item label="分类">
          <el-select
            v-model="query.category"
            placeholder="全部分类"
            clearable
            style="width: 180px"
            @change="onFilterChange"
          >
            <el-option v-for="c in categories" :key="c.code" :label="c.name" :value="c.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <!-- 搜索/排序都必须走 onFilterChange（它把 page 复位到 1），不能直接调 load：
               在第 3 页搜一个只有 3 条命中的词会得到 rows=[] + total=3，
               页面显示「暂无上架商品」且分页器因 3 < pageSize 被隐藏 —— 用户被卡死在空页上。 -->
          <el-input
            v-model="query.keyword"
            placeholder="搜索商品名称 / 描述"
            clearable
            style="width: 260px"
            @keyup.enter="onFilterChange"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-select v-model="query.sortBy" style="width: 140px" @change="onFilterChange">
            <el-option label="最新上架" value="newest" />
            <el-option label="评分最高" value="rating" />
            <el-option label="安装最多" value="installs" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onFilterChange">搜索</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 商品卡片网格 -->
    <div v-loading="loading" class="pub-grid">
      <el-empty v-if="!loading && publications.length === 0" description="暂无上架商品" />
      <el-card
        v-for="p in publications"
        :key="p.id"
        class="pub-card"
        :body-style="{ padding: '16px' }"
        shadow="hover"
        @click="openDetail(p)"
      >
        <div class="card-top">
          <div class="icon">
            <el-image v-if="p.iconUrl" :src="p.iconUrl" fit="cover" class="icon-img" />
            <span v-else>{{ p.productType === 'AGENT' ? '🤖' : '📦' }}</span>
          </div>
          <div class="title-block">
            <div class="name">{{ p.name }}</div>
            <div class="category">{{ p.categoryName || p.categoryCode || '未分类' }} · {{ typeLabel(p.productType) }}</div>
          </div>
          <div class="rating">
            <el-icon class="star"><StarFilled /></el-icon>
            <span>{{ formatRating(p.avgRating) }}</span>
          </div>
        </div>
        <div class="desc">{{ p.summary || '暂无简介' }}</div>
        <div class="tags">
          <el-tag v-for="t in splitTags(p.tags)" :key="t" size="small" effect="plain">{{ t }}</el-tag>
        </div>
        <div class="card-bottom">
          <div class="meta">
            <span><el-icon><Download /></el-icon> {{ p.installCount || 0 }}</span>
            <!-- 浏览量：由后端 GET /publication/{id} 在 200 路径上自增。这里的数字来自列表快照，
                 只有重新 load 才刷新 —— 刚点进详情再退回来仍是旧值，这是预期延迟不是 bug。 -->
            <span><el-icon><View /></el-icon> {{ p.viewCount || 0 }}</span>
            <span><el-icon><ChatLineRound /></el-icon> {{ p.ratingCount || 0 }}</span>
            <!-- 订阅数(W80 P2):口径 COUNT(mp_subscription WHERE status='ACTIVE')。
                 以前这个数字在页面上展示着,底下却没有任何一条记录支撑它
                 (后端 incrementSubscriptionCount 零调用方)—— 现在它有真实来源了。 -->
            <span><el-icon><Star /></el-icon> {{ p.subscriptionCount || 0 }}</span>
          </div>
          <span class="author">
            <!--
              集市里的商品都是开发者自己上架的(mp_author.author_company_id 绑租户公司),
              没有「平台自营」这种作者 —— 所以这里标的是「开发者自营」,
              与官方数字员工页的「平台直营」徽标正好互为对照。
            -->
            <el-tag size="small" type="warning" effect="plain">开发者自营</el-tag>
            {{ p.authorName || 'Anonymous' }}
          </span>
        </div>
      </el-card>
    </div>

    <div v-if="total > pageSize" class="pager">
      <el-pagination
        v-model:current-page="query.page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next, total"
        background
        @current-change="load"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { StarFilled, Star, Download, View, ChatLineRound } from '@element-plus/icons-vue'
import {
  listPublications,
  listCategories,
  type PublicationVO,
  type CategoryVO,
  type PublicationListQuery
} from '@/api/opc/marketplace'

const router = useRouter()

const loading = ref(false)
const publications = ref<PublicationVO[]>([])
const categories = ref<CategoryVO[]>([])
const total = ref(0)

const pageSize = 12

const query = reactive<PublicationListQuery>({
  productType: '',
  category: undefined,
  keyword: undefined,
  sortBy: 'newest',
  page: 1,
  pageSize
})

const pageInstalls = computed(() =>
  publications.value.reduce((sum, p) => sum + (p.installCount || 0), 0)
)

function typeLabel(t?: string) {
  if (t === 'AGENT') return '智能体'
  if (t === 'MODULE') return '模块'
  return '商品'
}

function formatRating(r?: number | string | null) {
  if (r === undefined || r === null || r === '') return '—'
  return Number(r).toFixed(1)
}

function splitTags(tags?: string): string[] {
  if (!tags) return []
  return tags.split(/[,，]/).map((t) => t.trim()).filter(Boolean)
}

async function loadCategories() {
  try {
    const res = await listCategories()
    // 后端 P1 返回扁平列表，按当前商品类型过滤，避免跨类型类目混在一起
    const all = res.data || []
    categories.value = query.productType
      ? all.filter((c) => c.productType === query.productType)
      : all
  } catch {
    categories.value = []
    ElMessage.error('加载类目失败')
  }
}

async function load() {
  loading.value = true
  try {
    const res = await listPublications({
      // 后端 PublicationListQuery.productType 为 null/空表示不限，公开端点恒定只返回 PUBLISHED
      productType: query.productType || undefined,
      category: query.category || undefined,
      keyword: query.keyword || undefined,
      sortBy: query.sortBy || 'newest',
      page: query.page || 1,
      pageSize
    })
    publications.value = res.data?.rows || []
    total.value = res.data?.total || 0
  } catch {
    publications.value = []
    total.value = 0
    ElMessage.error('加载商品失败')
  } finally {
    loading.value = false
  }
}

function onProductTypeChange() {
  // 切类型后旧类目可能已不适用
  query.category = undefined
  query.page = 1
  loadCategories()
  load()
}

/**
 * 所有「缩小结果集」的控件（分类 / 关键词 / 排序）统一走这里。
 * 必须先复位 page —— 见模板里关键词输入框上的注释：在第 3 页收窄条件会拿到空 rows，
 * 而分页器因 total < pageSize 被隐藏，用户会被困在一个没有返回路径的空页上。
 */
function onFilterChange() {
  query.page = 1
  load()
}

function resetQuery() {
  query.productType = ''
  query.category = undefined
  query.keyword = undefined
  query.sortBy = 'newest'
  query.page = 1
  loadCategories()
  load()
}

function openDetail(p: PublicationVO) {
  if (!p.id) return
  router.push({ name: 'OpcMarketplaceDetail', params: { id: String(p.id) } })
}

onMounted(() => {
  loadCategories()
  load()
})
</script>

<style scoped lang="scss">
/* 断点与 src/assets/styles/responsive.scss 的 $bp-sm(768px) 对齐 ——
   等价于 `respond-below(sm)`。此处用裸 @media 而非 @use mixin，是因为仓库内
   其它 OPC 视图页都不在 scoped style 里 @use 全局 scss（vite 未为 sass 配 alias），
   保持一致以免构建期解析差异。 */
$bp-sm: 768px;

.market-page {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero {
  background: linear-gradient(135deg, #5b8def 0%, #6c5ce7 100%);
  color: #fff;
  border: none;

  :deep(.el-card__body) {
    padding: 24px;
  }
}

.hero-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;

  .hero-text {
    flex: 1;

    h1 {
      margin: 0 0 8px;
      font-size: 24px;
    }

    p {
      margin: 0;
      opacity: 0.9;
      max-width: 600px;

      a {
        color: #fff;
        font-weight: 600;
        text-decoration: underline;
      }
    }
  }

  .hero-stats {
    display: flex;
    gap: 32px;
  }

  .stat {
    text-align: center;

    .num {
      font-size: 32px;
      font-weight: 700;
    }

    .label {
      font-size: 12px;
      opacity: 0.85;
    }
  }
}

.filters {
  :deep(.el-card__body) {
    padding: 8px 16px 16px;
  }

  .type-tabs {
    :deep(.el-tabs__header) {
      margin-bottom: 8px;
    }
  }
}

/* 说明：卡片网格用普通 CSS Grid 而非 ResponsiveTable.vue ——
   ResponsiveTable 在 >=768px 强制渲染 el-table，与卡片网格的设计互相打架。
   ResponsiveTable 用在 author-center.vue 的「我的作品」表格里，那里才是真表格。 */
.pub-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  min-height: 200px;
}

.pub-card {
  cursor: pointer;
  transition: transform 0.2s ease;

  &:hover {
    transform: translateY(-2px);
  }

  .card-top {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;
  }

  .icon {
    font-size: 32px;
    width: 56px;
    height: 56px;
    flex: 0 0 56px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #f0f4ff;
    border-radius: 12px;
    overflow: hidden;
  }

  .icon-img {
    width: 56px;
    height: 56px;
  }

  .title-block {
    flex: 1;
    min-width: 0;
  }

  .name {
    font-weight: 600;
    font-size: 16px;
    color: #1f2937;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .category {
    font-size: 12px;
    color: #6b7280;
    margin-top: 2px;
  }

  .rating {
    display: flex;
    align-items: center;
    gap: 4px;
    font-weight: 600;
    color: #f59e0b;

    .star {
      color: #f59e0b;
    }
  }

  .desc {
    font-size: 13px;
    color: #4b5563;
    line-height: 1.5;
    margin-bottom: 12px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    min-height: 40px;
  }

  .tags {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    margin-bottom: 12px;
  }

  .card-bottom {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-top: 12px;
    border-top: 1px solid #f3f4f6;
  }

  .meta {
    display: flex;
    gap: 12px;
    font-size: 12px;
    color: #6b7280;

    span {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
  }

  .author {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: #9ca3af;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 160px;

    :deep(.el-tag) {
      flex-shrink: 0;
    }
  }
}

.pager {
  display: flex;
  justify-content: center;
}

// ---------- 移动端 (< 768px) ----------
@media (max-width: #{$bp-sm - 0.02}) {
  .market-page {
    padding: 12px;
    gap: 12px;
  }

  .hero-content {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;

    :deep(.el-card__body) {
      padding: 16px;
    }

    h1 {
      font-size: 20px;
    }

    .hero-stats {
      width: 100%;
      justify-content: space-between;
      gap: 8px;
    }

    .stat .num {
      font-size: 22px;
    }
  }

  // 单列卡片，触控目标 ≥44px
  .pub-grid {
    grid-template-columns: 1fr;
  }

  .filter-form {
    :deep(.el-form-item) {
      display: flex;
      margin-right: 0;
      margin-bottom: 12px;
    }

    :deep(.el-input),
    :deep(.el-select) {
      flex: 1;
      width: auto !important;
    }

    :deep(.el-button) {
      min-height: 44px;
    }
  }
}
</style>