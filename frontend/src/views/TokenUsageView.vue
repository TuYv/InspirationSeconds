<template>
  <div class="page">
    <header class="page-header">
      <button class="back-btn" @click="router.push('/settings')">← 返回设置</button>
      <h1>Token 用量</h1>
      <p>仅统计使用自定义 AI Key 的调用。</p>
    </header>

    <section class="panel">
      <div v-if="loading" class="state empty">加载中...</div>

      <div v-else-if="records.length === 0" class="state empty">
        暂无用量记录，使用自定义 AI Key 后将在此显示。
      </div>

      <template v-else>
        <table class="usage-table">
          <thead>
            <tr>
              <th>日期</th>
              <th class="num">输入 tokens</th>
              <th class="num">输出 tokens</th>
              <th class="num">合计</th>
              <th class="num">调用次数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in records" :key="String(r.usageDate)">
              <td>{{ r.usageDate }}</td>
              <td class="num">{{ r.promptTokens.toLocaleString() }}</td>
              <td class="num">{{ r.completionTokens.toLocaleString() }}</td>
              <td class="num bold">{{ r.totalTokens.toLocaleString() }}</td>
              <td class="num">{{ r.callCount }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="total-row">
              <td>合计</td>
              <td class="num">{{ totals.prompt.toLocaleString() }}</td>
              <td class="num">{{ totals.completion.toLocaleString() }}</td>
              <td class="num bold">{{ totals.total.toLocaleString() }}</td>
              <td class="num">{{ totals.calls }}</td>
            </tr>
          </tfoot>
        </table>
      </template>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { apiFetch } from '../utils/api';

const router = useRouter();

type UsageRecord = {
  usageDate: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  callCount: number;
};

const records = ref<UsageRecord[]>([]);
const loading = ref(true);

const totals = computed(() => ({
  prompt: records.value.reduce((s, r) => s + r.promptTokens, 0),
  completion: records.value.reduce((s, r) => s + r.completionTokens, 0),
  total: records.value.reduce((s, r) => s + r.totalTokens, 0),
  calls: records.value.reduce((s, r) => s + r.callCount, 0),
}));

onMounted(async () => {
  try {
    const resp = await apiFetch('/api/user/token-usage');
    if (resp.ok) {
      records.value = await resp.json();
    }
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 40px 24px 64px;
  max-width: 700px;
  margin: 0 auto;
}

.page-header h1 { margin: 8px 0; font-size: 28px; }
.page-header p { margin: 0; color: var(--muted); }

.back-btn {
  font-size: 14px;
  font-weight: 600;
  color: var(--muted);
  background: none;
  padding: 0;
  cursor: pointer;
}
.back-btn:hover { color: var(--ink); }

.panel {
  background: var(--surface);
  border-radius: 20px;
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
  padding: 28px;
  overflow-x: auto;
}

.state {
  padding: 16px;
  border-radius: 12px;
  font-size: 14px;
  color: #475569;
  background: rgba(148, 163, 184, .15);
  text-align: center;
}

.usage-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.usage-table th {
  text-align: left;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: .06em;
  color: var(--muted);
  padding: 8px 12px;
  border-bottom: 1px solid var(--border);
}

.usage-table td {
  padding: 10px 12px;
  border-bottom: 1px solid rgba(15, 23, 42, .05);
  color: var(--ink);
}

.usage-table th.num,
.usage-table td.num {
  text-align: right;
}

.bold { font-weight: 700; }

.total-row td {
  font-weight: 700;
  border-top: 2px solid var(--border);
  border-bottom: none;
  padding-top: 12px;
}
</style>
