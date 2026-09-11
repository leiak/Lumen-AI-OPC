<template>
  <div class="page market-page">
    <!-- Header -->
    <el-card class="hero">
      <div class="hero-content">
        <div class="hero-text">
          <h1>🛒 OPC 模块市场</h1>
          <p>发现、安装、评分 OPC 生态里的扩展模块。从 CRM 到财务,从 HR 到 AI Agent,一站式浏览所有能力。</p>
        </div>
        <div class="hero-stats">
          <div class="stat">
            <div class="num">{{ stats.total }}</div>
            <div class="label">模块</div>
          </div>
          <div class="stat">
            <div class="num">{{ stats.categories }}</div>
            <div class="label">分类</div>
          </div>
          <div class="stat">
            <div class="num">{{ stats.installs }}</div>
            <div class="label">安装</div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- Filters -->
    <el-card class="filters">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="分类">
          <el-select v-model="query.category" placeholder="全部分类" clearable style="width: 160px" @change="load">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="搜索模块名 / 描述 / 标签" clearable style="width: 280px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">搜索</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Module grid -->
    <div v-loading="loading" class="module-grid">
      <el-empty v-if="!loading && modules.length === 0" description="暂无模块" />
      <el-card
        v-for="m in modules"
        :key="m.id"
        class="module-card"
        :body-style="{ padding: '16px' }"
        shadow="hover"
        @click="openDetail(m)"
      >
        <div class="card-top">
          <div class="icon">{{ m.icon || '📦' }}</div>
          <div class="title-block">
            <div class="name">{{ m.name }}</div>
            <div class="category">{{ m.category }}</div>
          </div>
          <div class="rating">
            <el-icon class="star"><StarFilled /></el-icon>
            <span>{{ formatRating(m.rating) }}</span>
          </div>
        </div>
        <div class="desc">{{ m.description }}</div>
        <div class="tags">
          <el-tag v-for="t in splitTags(m.tags)" :key="t" size="small" effect="plain">{{ t }}</el-tag>
        </div>
        <div class="card-bottom">
          <div class="meta">
            <span><el-icon><Download /></el-icon> {{ m.installCount }}</span>
            <span><el-icon><ChatLineRound /></el-icon> {{ m.commentCount }}</span>
          </div>
          <el-button type="primary" size="small" @click.stop="onInstall(m)">安装</el-button>
        </div>
      </el-card>
    </div>

    <!-- Detail drawer -->
    <el-drawer v-model="drawerVisible" :title="current?.name || '模块详情'" direction="rtl" size="560px">
      <div v-if="current" class="drawer-body">
        <div class="drawer-hero">
          <div class="big-icon">{{ current.icon || '📦' }}</div>
          <div>
            <h2>{{ current.name }}</h2>
            <el-tag>{{ current.category }}</el-tag>
            <span class="owner">by {{ current.ownerName || 'Anonymous' }}</span>
          </div>
        </div>
        <p class="drawer-desc">{{ current.description }}</p>

        <el-divider />

        <div class="drawer-stats">
          <div class="stat-cell">
            <div class="num">{{ formatRating(current.rating) }}</div>
            <div class="lbl">评分</div>
          </div>
          <div class="stat-cell">
            <div class="num">{{ current.ratingCount || 0 }}</div>
            <div class="lbl">评分数</div>
          </div>
          <div class="stat-cell">
            <div class="num">{{ current.installCount || 0 }}</div>
            <div class="lbl">安装数</div>
          </div>
          <div class="stat-cell">
            <div class="num">{{ current.commentCount || 0 }}</div>
            <div class="lbl">评论数</div>
          </div>
        </div>

        <el-divider />

        <h3>用户评论 ({{ comments.length }})</h3>
        <div class="comment-list">
          <el-empty v-if="comments.length === 0" description="暂无评论" :image-size="60" />
          <div v-for="c in comments" :key="c.id" class="comment">
            <div class="comment-meta">
              <strong>{{ c.userName || 'User #' + c.userId }}</strong>
              <span class="time">{{ c.createTime }}</span>
            </div>
            <p>{{ c.content }}</p>
          </div>
        </div>

        <div class="comment-input">
          <el-input v-model="newComment" type="textarea" :rows="2" placeholder="写下你的评论…" />
          <el-button type="primary" :disabled="!newComment.trim()" @click="onAddComment">发表</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  StarFilled,
  Download,
  ChatLineRound
} from '@element-plus/icons-vue'
import {
  listModules,
  getModule,
  installModule,
  listComments,
  addComment,
  type CommunityModule,
  type CommunityComment
} from '@/api/opc/community'

const loading = ref(false)
const modules = ref<CommunityModule[]>([])
const query = ref({ category: '', keyword: '' })

const drawerVisible = ref(false)
const current = ref<CommunityModule | null>(null)
const comments = ref<CommunityComment[]>([])
const newComment = ref('')

const categories = computed(() => {
  const set = new Set<string>()
  modules.value.forEach((m) => m.category && set.add(m.category))
  return Array.from(set).sort()
})

const stats = computed(() => ({
  total: modules.value.length,
  categories: categories.value.length,
  installs: modules.value.reduce((sum, m) => sum + (m.installCount || 0), 0)
}))

function formatRating(r?: number) {
  if (r === undefined || r === null) return '—'
  return Number(r).toFixed(1)
}

function splitTags(tags?: string): string[] {
  if (!tags) return []
  return tags.split(/[,,]/).map((t) => t.trim()).filter(Boolean)
}

async function load() {
  loading.value = true
  try {
    const res = await listModules({
      category: query.value.category || undefined,
      keyword: query.value.keyword || undefined,
      page: 1,
      pageSize: 50
    })
    modules.value = res.data?.rows || []
  } catch (e: any) {
    ElMessage.error('加载模块失败: ' + (e?.message || 'unknown'))
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.value = { category: '', keyword: '' }
  load()
}

async function openDetail(m: CommunityModule) {
  try {
    const res = await getModule(m.id!)
    current.value = res.data || m
    drawerVisible.value = true
    await loadComments()
  } catch {
    current.value = m
    drawerVisible.value = true
    await loadComments()
  }
}

async function loadComments() {
  if (!current.value?.id) return
  try {
    const res = await listComments(current.value.id)
    comments.value = res.data || []
  } catch {
    comments.value = []
  }
}

async function onInstall(m: CommunityModule) {
  try {
    await installModule(m.id!)
    ElMessage.success(`已安装 ${m.name}`)
    m.installCount = (m.installCount || 0) + 1
  } catch (e: any) {
    ElMessage.error('安装失败: ' + (e?.message || 'unknown'))
  }
}

async function onAddComment() {
  if (!current.value?.id || !newComment.value.trim()) return
  try {
    await addComment({
      moduleId: current.value.id,
      content: newComment.value.trim()
    })
    newComment.value = ''
    ElMessage.success('评论已发布')
    await loadComments()
    current.value.commentCount = (current.value.commentCount || 0) + 1
  } catch (e: any) {
    ElMessage.error('评论失败: ' + (e?.message || 'unknown'))
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
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
      text-transform: uppercase;
    }
  }
}

.filters {
  :deep(.el-card__body) {
    padding: 16px;
  }
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  min-height: 200px;
}

.module-card {
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
    font-size: 36px;
    width: 56px;
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #f0f4ff;
    border-radius: 12px;
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
}

.drawer-body {
  padding: 0 8px;
}

.drawer-hero {
  display: flex;
  gap: 16px;
  align-items: center;

  .big-icon {
    font-size: 56px;
    width: 80px;
    height: 80px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #f0f4ff;
    border-radius: 16px;
  }

  h2 {
    margin: 0 0 8px;
    font-size: 20px;
  }

  .owner {
    margin-left: 8px;
    color: #6b7280;
    font-size: 13px;
  }
}

.drawer-desc {
  margin: 16px 0;
  color: #4b5563;
  line-height: 1.6;
}

.drawer-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  text-align: center;

  .stat-cell {
    background: #f9fafb;
    padding: 12px 4px;
    border-radius: 8px;

    .num {
      font-size: 20px;
      font-weight: 700;
      color: #1f2937;
    }

    .lbl {
      font-size: 11px;
      color: #6b7280;
      text-transform: uppercase;
    }
  }
}

.comment-list {
  max-height: 300px;
  overflow-y: auto;
  margin-bottom: 12px;
}

.comment {
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
  margin-bottom: 8px;

  .comment-meta {
    display: flex;
    justify-content: space-between;
    margin-bottom: 6px;
  }

  .time {
    font-size: 11px;
    color: #9ca3af;
  }

  p {
    margin: 0;
    color: #374151;
    font-size: 13px;
  }
}

.comment-input {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>