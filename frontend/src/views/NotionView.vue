<template>
  <div class="notion-page">
    <!-- Left: heatmap panel -->
    <div class="heatmap-panel">
      <!-- Month navigation -->
      <div class="month-nav">
        <button class="nav-btn" @click="changeMonth(-1)" aria-label="上个月">‹</button>
        <span class="month-label">{{ year }}年{{ month }}月</span>
        <button class="nav-btn" @click="changeMonth(1)" aria-label="下个月">›</button>
      </div>

      <!-- Weekday headers (Monday-first) -->
      <div class="weekday-row">
        <span v-for="d in weekdays" :key="d">{{ d }}</span>
      </div>

      <!-- Calendar grid -->
      <div class="cal-grid">
        <!-- Leading empty cells -->
        <div
          v-for="i in leadingBlanks"
          :key="`blank-${i}`"
          class="cal-cell empty"
        />
        <!-- Day cells -->
        <div
          v-for="day in daysInMonth"
          :key="day"
          class="cal-cell"
          :class="{ active: !!pageMap[dateKey(day)], selected: selectedDate === dateKey(day) }"
          :style="{ backgroundColor: cellColor(day) }"
          :title="cellTitle(day)"
          @click="onDayClick(day)"
        >
          <span class="day-num">{{ day }}</span>
        </div>
      </div>

      <!-- Color legend -->
      <div class="legend">
        <span class="legend-label">少</span>
        <div v-for="c in colorScale" :key="c" class="legend-dot" :style="{ backgroundColor: c }" />
        <span class="legend-label">多</span>
      </div>
    </div>

    <!-- Right: detail panel -->
    <div class="detail-panel">
      <!-- Month loading -->
      <div v-if="monthLoading" class="detail-empty detail-loading-month">
        <p class="loading-inspiration">✦ 正在加载你的灵感...</p>
      </div>

      <!-- No date selected -->
      <div v-else-if="!selectedDate" class="detail-empty">
        <p>点击左侧日期查看当天记录</p>
      </div>

      <!-- No page for selected date -->
      <div v-else-if="selectedDate && !pageMap[selectedDate]" class="detail-empty">
        <p>{{ formatDisplayDate(selectedDate) }} 暂无记录</p>
      </div>

      <!-- Loading content -->
      <div v-else-if="contentLoading" class="detail-loading">
        <p>加载中…</p>
      </div>

      <!-- Content loaded -->
      <div v-else-if="pageContent" class="detail-content">
        <h2 class="detail-title">{{ formatDisplayDate(selectedDate) }}</h2>

        <!-- Markdown body -->
        <section class="content-section">
          <h3 class="section-title">今日记录</h3>
          <div
            class="markdown-body"
            v-html="renderedMarkdown"
          />
        </section>

        <!-- AI summary (collapsible, only if non-empty) -->
        <section v-if="pageContent.aiSummary" class="content-section ai-section">
          <button class="collapse-toggle" @click="aiExpanded = !aiExpanded">
            <span class="section-title">AI 日报</span>
            <span class="toggle-icon">{{ aiExpanded ? '▲' : '▼' }}</span>
          </button>
          <div v-if="aiExpanded" class="ai-body">
            {{ pageContent.aiSummary }}
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { marked } from 'marked';
import { apiFetch } from '../utils/api';

// ── Types ──────────────────────────────────────────────────────────────
interface PageEntry { date: string; pageId: string }
interface PageContent { markdown: string; aiSummary: string }

// ── State ──────────────────────────────────────────────────────────────
const today = new Date();
const year = ref(today.getFullYear());
const month = ref(today.getMonth() + 1); // 1-based

const pageMap = ref<Record<string, string>>({});         // date → pageId
const blockCountMap = ref<Record<string, number>>({});   // date → blockCount
const selectedDate = ref<string | null>(null);
const pageContent = ref<PageContent | null>(null);
const contentLoading = ref(false);
const monthLoading = ref(false);
const aiExpanded = ref(false);

// ── Constants ──────────────────────────────────────────────────────────
const weekdays = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];
const colorScale = ['#eef2ee', '#a8d5b5', '#52a96e', '#2d7a4a', '#1a4a2e'];

function blockColor(count: number | undefined): string {
  if (count === undefined || count === 0) return colorScale[0];
  if (count <= 3) return colorScale[1];
  if (count <= 8) return colorScale[2];
  if (count <= 15) return colorScale[3];
  return colorScale[4];
}

// ── Calendar helpers ───────────────────────────────────────────────────
const daysInMonth = computed(() => {
  return new Date(year.value, month.value, 0).getDate();
});

/** Day-of-week index of the 1st (0=Mon … 6=Sun, Monday-first) */
const leadingBlanks = computed(() => {
  const dow = new Date(year.value, month.value - 1, 1).getDay(); // 0=Sun
  return (dow + 6) % 7; // convert to Mon=0
});

function dateKey(day: number): string {
  return `${year.value}-${String(month.value).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function cellColor(day: number): string {
  const key = dateKey(day);
  if (!pageMap.value[key]) return colorScale[0];
  return blockColor(blockCountMap.value[key]);
}

function cellTitle(day: number): string {
  const key = dateKey(day);
  const count = blockCountMap.value[key];
  if (!pageMap.value[key]) return `${key} — 无记录`;
  if (count === undefined) return `${key} — 有记录`;
  return `${key} — ${count} 个 block`;
}

function formatDisplayDate(dateStr: string | null): string {
  if (!dateStr) return '';
  const [, m, d] = dateStr.split('-');
  return `${parseInt(m)}月${parseInt(d)}日`;
}

// ── Month navigation ───────────────────────────────────────────────────
let fetchAbortController: AbortController | null = null;

function changeMonth(delta: number) {
  let m = month.value + delta;
  let y = year.value;
  if (m > 12) { m = 1; y++; }
  if (m < 1) { m = 12; y--; }
  month.value = m;
  year.value = y;
  selectedDate.value = null;
  pageContent.value = null;
  monthLoading.value = true;
}

// ── Data fetching ──────────────────────────────────────────────────────
async function loadMonth() {
  if (fetchAbortController) fetchAbortController.abort();
  fetchAbortController = new AbortController();
  const signal = fetchAbortController.signal;

  monthLoading.value = true;
  pageMap.value = {};
  blockCountMap.value = {};

  try {
    const resp = await apiFetch(
      `/api/notion/pages?year=${year.value}&month=${month.value}`,
      { signal }
    );
    if (!resp.ok || signal.aborted) return;
    const pages: PageEntry[] = await resp.json();

    // Phase 1: populate pageMap (shows has/hasn't)
    const newMap: Record<string, string> = {};
    for (const p of pages) { newMap[p.date] = p.pageId; }
    pageMap.value = newMap;
    monthLoading.value = false;

    if (pages.length === 0) return;

    // Phase 2: parallel block-count with max 5 concurrent
    await fetchBlockCountsParallel(pages, signal);
  } catch (e: any) {
    if (e?.name !== 'AbortError') console.error('loadMonth error', e);
  } finally {
    monthLoading.value = false;
  }
}

async function fetchBlockCountsParallel(pages: PageEntry[], signal: AbortSignal) {
  const CONCURRENCY = 5;
  let i = 0;

  async function next(): Promise<void> {
    if (i >= pages.length || signal.aborted) return;
    const page = pages[i++];
    try {
      const resp = await apiFetch(`/api/notion/pages/${page.pageId}/block-count`, { signal });
      if (!resp.ok || signal.aborted) return;
      const data = await resp.json();
      blockCountMap.value = { ...blockCountMap.value, [page.date]: data.blockCount };
    } catch { /* ignore individual failures */ }
    return next();
  }

  await Promise.all(Array.from({ length: Math.min(CONCURRENCY, pages.length) }, next));
}

async function loadContent(pageId: string, date: string) {
  contentLoading.value = true;
  pageContent.value = null;
  aiExpanded.value = false;
  try {
    const resp = await apiFetch(`/api/notion/pages/${pageId}/content`);
    if (!resp.ok) { contentLoading.value = false; return; }
    pageContent.value = await resp.json();
  } catch (e) {
    console.error('loadContent error', e);
  } finally {
    contentLoading.value = false;
  }
}

// ── Event handlers ─────────────────────────────────────────────────────
function onDayClick(day: number) {
  const key = dateKey(day);
  if (!pageMap.value[key]) {
    selectedDate.value = key;
    pageContent.value = null;
    return;
  }
  selectedDate.value = key;
  loadContent(pageMap.value[key], key);
}

// ── Markdown rendering ─────────────────────────────────────────────────
const renderedMarkdown = computed(() => {
  if (!pageContent.value?.markdown) return '';
  // marked v5+ returns string synchronously by default
  return marked.parse(pageContent.value.markdown, { async: false }) as string;
});

// ── Watchers ───────────────────────────────────────────────────────────
watch([year, month], loadMonth);
onMounted(loadMonth);
</script>

<style scoped>
.notion-page {
  display: flex;
  gap: 24px;
  padding: 32px 24px;
  max-width: 1100px;
  margin: 0 auto;
  align-items: flex-start;
}

/* ── Heatmap panel ── */
.heatmap-panel {
  flex: 0 0 auto;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 20px;
  box-shadow: var(--shadow);
  padding: 24px;
  min-width: 300px;
}

.month-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.month-label {
  font-weight: 700;
  font-size: 16px;
}

.nav-btn {
  background: none;
  border: 1px solid var(--border);
  border-radius: 8px;
  width: 32px;
  height: 32px;
  cursor: pointer;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ink);
  transition: background 0.15s;
}
.nav-btn:hover { background: rgba(0,0,0,.05); }

.weekday-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
  margin-bottom: 4px;
  text-align: center;
  font-size: 11px;
  color: var(--muted);
  font-weight: 600;
  letter-spacing: .06em;
}

.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.cal-cell {
  aspect-ratio: 1;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: default;
  transition: transform 0.1s, box-shadow 0.1s;
  background: #eef2ee;
  position: relative;
}

.cal-cell.empty {
  background: transparent;
  pointer-events: none;
}

.cal-cell.active {
  cursor: pointer;
}

.cal-cell.active:hover {
  transform: scale(1.1);
  box-shadow: 0 2px 8px rgba(0,0,0,.15);
  z-index: 1;
}

.cal-cell.selected {
  outline: 2px solid #2d7a4a;
  outline-offset: 1px;
}

.day-num {
  font-size: 11px;
  font-weight: 500;
  color: rgba(0,0,0,.55);
}

.cal-cell.active .day-num {
  color: rgba(0,0,0,.7);
}

.legend {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 16px;
  justify-content: center;
}

.legend-label {
  font-size: 11px;
  color: var(--muted);
}

.legend-dot {
  width: 14px;
  height: 14px;
  border-radius: 3px;
}

/* ── Detail panel ── */
.detail-panel {
  flex: 1;
  min-width: 0;
}

.detail-empty {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 48px 24px;
  text-align: center;
  color: var(--muted);
  box-shadow: var(--shadow);
}

.detail-loading-month {
  border-color: rgba(45, 122, 74, 0.2);
  background: rgba(212, 234, 217, 0.3);
}

.loading-inspiration {
  color: #2d7a4a;
  font-weight: 600;
  font-size: 15px;
  animation: pulse-text 1.6s ease-in-out infinite;
}

@keyframes pulse-text {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}

.detail-loading {
  padding: 48px 24px;
  text-align: center;
  color: var(--muted);
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-title {
  font-size: 22px;
  font-weight: 700;
  margin: 0;
}

.content-section {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 16px;
  box-shadow: var(--shadow);
  padding: 20px 24px;
}

.section-title {
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: .08em;
  color: var(--muted);
  margin: 0 0 12px;
}

.markdown-body {
  font-size: 15px;
  line-height: 1.7;
  color: var(--ink);
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: 1em 0 .4em;
  font-weight: 700;
}

.markdown-body :deep(h1) { font-size: 1.4em; }
.markdown-body :deep(h2) { font-size: 1.2em; }
.markdown-body :deep(h3) { font-size: 1.05em; }

.markdown-body :deep(p) { margin: .6em 0; }

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 1.5em;
  margin: .5em 0;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid #52a96e;
  margin: .6em 0;
  padding: .2em .8em;
  color: var(--muted);
}

.markdown-body :deep(pre) {
  background: #f3f4f6;
  border-radius: 8px;
  padding: 12px 16px;
  overflow-x: auto;
  font-size: 13px;
}

.markdown-body :deep(code) {
  background: #f3f4f6;
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 13px;
}

.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
}

.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: 8px;
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 1em 0;
}

/* AI summary */
.ai-section { padding: 0; }

.collapse-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  background: none;
  border: none;
  cursor: pointer;
  padding: 20px 24px;
  text-align: left;
}

.toggle-icon {
  font-size: 11px;
  color: var(--muted);
}

.ai-body {
  padding: 0 24px 20px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--ink);
  white-space: pre-wrap;
  border-top: 1px solid var(--border);
  padding-top: 12px;
  margin-top: -4px;
}

/* ── Responsive ── */
@media (max-width: 768px) {
  .notion-page {
    flex-direction: column;
    padding: 20px 16px;
  }

  .heatmap-panel {
    width: 100%;
    min-width: unset;
  }
}
</style>
