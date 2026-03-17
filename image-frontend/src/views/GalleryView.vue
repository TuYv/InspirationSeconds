<template>
  <div class="gallery-page">
    <header class="topbar">
      <router-link to="/" class="back-link">← 返回编辑器</router-link>
      <h1 class="title">主题画廊</h1>
      <router-link to="/themes/edit" class="btn-primary" style="text-decoration:none;padding:7px 14px;border-radius:6px;font-size:13px">创建主题</router-link>
    </header>

    <div class="toolbar">
      <input
        v-model="searchQuery"
        placeholder="搜索主题名称…"
        class="search-input"
        @input="onSearchInput"
      />
      <div class="sort-buttons">
        <button :class="['sort-btn', sort === 'star_count' && 'active']" @click="setSort('star_count')">最热</button>
        <button :class="['sort-btn', sort === 'created_at' && 'active']" @click="setSort('created_at')">最新</button>
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
          <div v-else class="no-thumb">{{ theme.name[0] }}</div>
        </div>
        <div class="card-body">
          <div class="card-name">{{ theme.name }}</div>
          <div class="card-meta">
            <span class="card-author">by {{ theme.authorName }}</span>
            <button
              class="star-btn"
              :class="{ starred: isStarred(theme.id) }"
              @click="handleStar(theme)"
            >
              {{ isStarred(theme.id) ? '★' : '☆' }} {{ theme.starCount }}
            </button>
          </div>
          <div class="card-actions">
            <button class="btn-primary card-apply" @click="applyTheme(theme)">应用</button>
            <router-link :to="`/themes/edit?fork=${theme.id}`" class="btn-secondary card-fork" style="text-decoration:none;padding:5px 10px;border-radius:6px;font-size:12px;background:var(--surface2);border:1px solid var(--border);color:var(--text-muted)">编辑</router-link>
          </div>
        </div>
      </div>
    </div>

    <div class="load-more" v-if="!loading && hasMore">
      <button class="btn-secondary" @click="loadMore">加载更多</button>
    </div>
    <div class="load-more" v-if="loading && themes.length > 0">
      <span class="loading-text">加载中…</span>
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

.topbar {
  height: 50px;
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
}
.back-link:hover { color: var(--text); }

.title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--border);
  background: var(--surface);
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  max-width: 320px;
}

.sort-buttons { display: flex; gap: 4px; }

.sort-btn {
  padding: 5px 12px;
  font-size: 12px;
  background: var(--surface2);
  color: var(--text-muted);
  border: 1px solid var(--border);
  border-radius: 5px;
}

.sort-btn.active {
  background: var(--accent);
  color: #fff;
  border-color: var(--accent);
}

.grid {
  flex: 1;
  overflow-y: auto;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
  padding: 20px;
  align-content: start;
}

.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 10px;
  overflow: hidden;
  transition: border-color 0.15s, transform 0.15s;
}

.card:hover {
  border-color: var(--accent);
  transform: translateY(-2px);
}

.skeleton {
  height: 220px;
  background: linear-gradient(90deg, var(--surface) 25%, var(--surface2) 50%, var(--surface) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  pointer-events: none;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.card-thumb {
  height: 140px;
  overflow: hidden;
  background: var(--surface2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.no-thumb {
  font-size: 36px;
  font-weight: 700;
  color: var(--border);
}

.card-body {
  padding: 12px;
}

.card-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.card-author {
  font-size: 11px;
  color: var(--text-muted);
}

.star-btn {
  font-size: 12px;
  background: transparent;
  color: var(--text-muted);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid var(--border);
}

.star-btn.starred {
  color: #f5c542;
  border-color: #f5c542;
}

.card-actions {
  display: flex;
  gap: 6px;
}

.card-apply {
  flex: 1;
  padding: 5px 0;
  font-size: 12px;
}

.load-more {
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  padding: 16px;
}

.loading-text {
  font-size: 13px;
  color: var(--text-muted);
}
</style>
