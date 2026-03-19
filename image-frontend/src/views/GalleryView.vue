<template>
  <div class="gallery-page">
    <header class="topbar">
      <router-link to="/" class="back-link">
        <span class="back-arrow">←</span> 编辑器
      </router-link>
      <div class="topbar-title-area">
        <h1 class="title">主题画廊</h1>
        <span class="subtitle">由社区创作，共同分享</span>
      </div>
      <router-link to="/themes/edit" class="btn-primary create-btn" style="text-decoration:none">
        <span style="opacity:0.75">+</span> 创建主题
      </router-link>
    </header>

    <div class="toolbar">
      <div class="search-wrap">
        <span class="search-icon">⌕</span>
        <input
          v-model="searchQuery"
          placeholder="搜索主题名称…"
          class="search-input"
          @input="onSearchInput"
        />
      </div>
      <div class="sort-buttons">
        <button :class="['sort-btn', sort === 'star_count' && 'active']" @click="setSort('star_count')">
          ★ 最热
        </button>
        <button :class="['sort-btn', sort === 'created_at' && 'active']" @click="setSort('created_at')">
          ↑ 最新
        </button>
      </div>
    </div>

    <div class="grid" ref="gridRef">
      <!-- Skeleton while loading first page -->
      <template v-if="loading && themes.length === 0">
        <div v-for="n in 12" :key="n" class="card skeleton" />
      </template>

      <div v-for="theme in themes" :key="theme.id" class="card">
        <div class="card-thumb">
          <img v-if="theme.thumbnailUrl" :src="theme.thumbnailUrl" :alt="theme.name" loading="lazy" />
          <div v-else class="no-thumb" :style="{ background: getThemeGradient(theme.name) }">
            <span class="no-thumb-letter">{{ theme.name[0] }}</span>
          </div>
        </div>
        <div class="card-body">
          <div class="card-name">{{ theme.name }}</div>
          <div class="card-meta">
            <span class="card-author">{{ theme.authorName }}</span>
            <button
              class="star-btn"
              :class="{ starred: isStarred(theme.id) }"
              @click="handleStar(theme)"
              :title="isStarred(theme.id) ? '已点赞' : '点赞'"
            >
              {{ isStarred(theme.id) ? '★' : '☆' }}
              <span class="star-count">{{ theme.starCount }}</span>
            </button>
          </div>
          <div class="card-actions">
            <button class="card-apply btn-primary" @click="applyTheme(theme)">应用主题</button>
            <router-link
              :to="`/themes/edit?fork=${theme.id}`"
              class="card-fork btn-secondary"
              style="text-decoration:none"
            >编辑</router-link>
          </div>
        </div>
      </div>
    </div>

    <div class="load-more" v-if="!loading && hasMore">
      <button class="btn-secondary load-more-btn" @click="loadMore">加载更多</button>
    </div>
    <div class="load-more" v-if="loading && themes.length > 0">
      <span class="loading-dots"><span>·</span><span>·</span><span>·</span></span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listThemes, starTheme, type Theme } from '../api/themes'
import { saveActiveTheme, isStarred as checkStarred, markStarred } from '../utils/storage'

const router = useRouter()
const themes = ref<Theme[]>([])
const loading = ref(false)
const hasMore = ref(true)
const currentPage = ref(1)
const sort = ref('star_count')
const searchQuery = ref('')
let searchTimer: ReturnType<typeof setTimeout> | null = null

function isStarred(id: number) {
  return checkStarred(id)
}

function getThemeGradient(name: string) {
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  const hue1 = ((hash % 360) + 360) % 360
  const hue2 = (hue1 + 40) % 360
  return `linear-gradient(135deg, hsl(${hue1},28%,18%) 0%, hsl(${hue2},32%,24%) 100%)`
}

async function fetchThemes(reset = false) {
  if (loading.value) return
  loading.value = true
  if (reset) {
    currentPage.value = 1
    themes.value = []
    hasMore.value = true
  }
  try {
    const result = await listThemes({
      page: currentPage.value,
      size: 20,
      sort: sort.value,
      q: searchQuery.value || undefined,
    })
    themes.value.push(...result.records)
    hasMore.value = currentPage.value < result.pages
    currentPage.value++
  } finally {
    loading.value = false
  }
}

function loadMore() {
  fetchThemes(false)
}

function setSort(s: string) {
  sort.value = s
  fetchThemes(true)
}

function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => fetchThemes(true), 400)
}

async function handleStar(theme: Theme) {
  if (checkStarred(theme.id)) return
  await starTheme(theme.id)
  markStarred(theme.id)
  theme.starCount++
}

function applyTheme(theme: Theme) {
  saveActiveTheme({ id: theme.id, name: theme.name, css: theme.css })
  router.push('/')
}

onMounted(() => fetchThemes(true))
</script>

<style scoped>
.gallery-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}

/* ── Topbar ── */
.topbar {
  height: 54px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
}

.back-link {
  color: var(--text-muted);
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 5px;
  transition: color 0.15s;
  flex-shrink: 0;
}

.back-link:hover { color: var(--text); }

.back-arrow {
  font-size: 14px;
  opacity: 0.6;
}

.topbar-title-area {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
  letter-spacing: -0.02em;
}

.subtitle {
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 500;
}

.create-btn {
  font-size: 13px;
  padding: 6px 14px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  text-decoration: none;
}

/* ── Toolbar ── */
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 20px;
  border-bottom: 1px solid var(--border);
  background: var(--surface);
  flex-shrink: 0;
}

.search-wrap {
  flex: 1;
  max-width: 320px;
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 10px;
  color: var(--text-muted);
  font-size: 16px;
  pointer-events: none;
  line-height: 1;
  font-style: normal;
}

.search-input {
  width: 100%;
  padding-left: 32px;
  height: 32px;
  font-size: 13px;
}

.sort-buttons {
  display: flex;
  gap: 4px;
  background: var(--surface2);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 3px;
}

.sort-btn {
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 600;
  background: transparent;
  color: var(--text-muted);
  border-radius: calc(var(--radius) - 2px);
  border: none;
  letter-spacing: 0.01em;
  transition: background 0.15s, color 0.15s;
}

.sort-btn.active {
  background: var(--accent);
  color: #fff;
  box-shadow: 0 1px 6px var(--accent-glow);
}

.sort-btn:not(.active):hover {
  color: var(--text);
  background: var(--surface3);
}

/* ── Grid ── */
.grid {
  flex: 1;
  overflow-y: auto;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 18px;
  padding: 22px;
  align-content: start;
}

/* ── Cards ── */
.card {
  background: var(--surface);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: 0 1px 0 var(--border), 0 4px 20px rgba(0, 0, 0, 0.35);
  transition: transform 0.22s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.22s;
  cursor: default;
}

.card:hover {
  transform: translateY(-4px);
  box-shadow:
    0 1px 0 var(--border-light),
    0 14px 40px rgba(0, 0, 0, 0.5),
    0 0 0 1px var(--accent-dim);
}

.skeleton {
  height: 220px;
  background: linear-gradient(90deg, var(--surface) 25%, var(--surface2) 50%, var(--surface) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.6s ease-in-out infinite;
  pointer-events: none;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.card-thumb {
  height: 148px;
  overflow: hidden;
  background: var(--surface2);
}

.card-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.3s ease;
}

.card:hover .card-thumb img {
  transform: scale(1.03);
}

.no-thumb {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.no-thumb-letter {
  font-size: 40px;
  font-weight: 800;
  color: rgba(255, 255, 255, 0.18);
  letter-spacing: -0.04em;
  line-height: 1;
  text-shadow: 0 2px 12px rgba(0,0,0,0.4);
}

.card-body {
  padding: 13px 14px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.card-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  letter-spacing: -0.01em;
}

.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-author {
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 120px;
}

.star-btn {
  font-size: 13px;
  background: transparent;
  color: var(--text-muted);
  padding: 3px 7px;
  border-radius: 5px;
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  gap: 4px;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}

.star-btn:hover:not(.starred) {
  border-color: var(--amber);
  color: var(--amber);
  background: var(--amber-dim);
}

.star-btn.starred {
  color: var(--amber);
  border-color: var(--amber);
  background: var(--amber-dim);
}

.star-count {
  font-size: 11px;
  font-weight: 600;
}

.card-actions {
  display: flex;
  gap: 7px;
  margin-top: 2px;
}

.card-apply {
  flex: 1;
  font-size: 12px;
  padding: 6px 10px;
  font-weight: 600;
}

.card-fork {
  font-size: 12px;
  padding: 6px 12px;
  font-weight: 500;
}

/* ── Load more ── */
.load-more {
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  padding: 18px;
}

.load-more-btn {
  font-size: 13px;
  padding: 8px 24px;
  font-weight: 500;
}

.loading-dots {
  display: flex;
  gap: 4px;
  align-items: center;
}

.loading-dots span {
  font-size: 20px;
  color: var(--text-muted);
  animation: dot-bounce 1.2s ease-in-out infinite;
  line-height: 1;
}

.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes dot-bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.4; }
  40% { transform: translateY(-5px); opacity: 1; }
}
</style>
