<template>
  <div class="page">
    <div v-if="loading" class="state empty">加载中...</div>
    <div v-else-if="error" class="state error">{{ error }}</div>
    <template v-else-if="config">
      <header class="profile-header">
        <div class="profile">
          <div class="avatar">
            <img v-if="config.avatarUrl" :src="config.avatarUrl" alt="avatar" />
            <span v-else>{{ displayName.slice(0, 1) }}</span>
          </div>
          <div>
            <h1>{{ displayName }}</h1>
            <p>最后更新：{{ formattedUpdatedAt }}</p>
          </div>
        </div>
        <span :class="['badge', statusBadgeClass]">{{ config.status }}</span>
      </header>

      <section class="panel">
        <div class="panel-header">
          <h2>配置概览</h2>
          <router-link to="/settings" class="btn-ghost">修改配置</router-link>
        </div>

        <div class="grid">
          <div class="field-card">
            <span class="label">OpenID</span>
            <span class="value truncate">{{ config.openId }}</span>
          </div>
          <div class="field-card">
            <span class="label">应用类型</span>
            <span class="value">{{ config.appType }}</span>
          </div>
          <div class="field-card">
            <span class="label">访客账号</span>
            <span class="value">{{ config.isGuest ? '是' : '否' }}</span>
          </div>
          <div class="field-card db-card">
            <span class="label">Notion 数据库 ID</span>
            <div class="db-value-row">
              <span class="value db-id">{{ truncatedDbId }}</span>
              <button class="copy-btn" :class="{ copied: copyDone }" @click="copyDbId">
                {{ copyDone ? '已复制' : '复制' }}
              </button>
            </div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { apiFetch } from '../utils/api';

const router = useRouter();

type ConfigView = {
  openId: string;
  appType: string;
  status: string;
  databaseId: string;
  isGuest: boolean | null;
  updatedAt: string | null;
  nickname?: string | null;
  avatarUrl?: string | null;
};

const config = ref<ConfigView | null>(null);
const loading = ref(true);
const error = ref('');
const copyDone = ref(false);

const displayName = computed(() => config.value?.nickname || '微信用户');

const statusBadgeClass = computed(() => {
  const s = (config.value?.status ?? '').toUpperCase();
  if (s === 'ACTIVE') return 'success';
  if (s === 'INACTIVE') return 'warning';
  return '';
});

const formattedUpdatedAt = computed(() => {
  if (!config.value?.updatedAt) return '-';
  const d = new Date(config.value.updatedAt);
  return isNaN(d.getTime()) ? config.value.updatedAt : d.toLocaleString();
});

const truncatedDbId = computed(() => {
  const id = config.value?.databaseId ?? '';
  return id.length > 24 ? id.slice(0, 12) + '...' + id.slice(-8) : id;
});

async function copyDbId() {
  const id = config.value?.databaseId ?? '';
  if (!id) return;
  await navigator.clipboard.writeText(id);
  copyDone.value = true;
  setTimeout(() => { copyDone.value = false; }, 2000);
}

onMounted(async () => {
  try {
    const resp = await apiFetch('/api/user/me');
    if (resp.status === 404) {
      router.push('/setup');
      return;
    }
    if (!resp.ok) throw new Error(`请求失败 (${resp.status})`);
    config.value = await resp.json();
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败';
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
  max-width: 900px;
  margin: 0 auto;
}

.profile-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.profile {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 20px;
  overflow: hidden;
  flex-shrink: 0;
}

.avatar img { width: 100%; height: 100%; object-fit: cover; }

.profile h1 { margin: 0 0 4px; font-size: 22px; }
.profile p { margin: 0; color: var(--muted); font-size: 13px; }

.panel {
  background: var(--surface);
  border-radius: 20px;
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
  padding: 28px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-header h2 { margin: 0; font-size: 20px; }

.btn-ghost {
  font-size: 14px;
  font-weight: 600;
  color: var(--accent-2);
  text-decoration: none;
  padding: 8px 14px;
  border-radius: 10px;
  background: rgba(37, 99, 235, 0.08);
  transition: background 0.15s;
}

.btn-ghost:hover { background: rgba(37, 99, 235, 0.15); }

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}

.field-card {
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.9);
}

.field-card.db-card { grid-column: 1 / -1; }

.label {
  display: block;
  font-size: 12px;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.value {
  display: block;
  font-size: 15px;
  font-weight: 600;
  margin-top: 6px;
}

.truncate {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.db-value-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 6px;
}

.db-id {
  margin-top: 0;
  font-family: monospace;
  font-size: 14px;
}

.copy-btn {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.06);
  color: var(--ink);
  cursor: pointer;
  transition: background 0.15s;
  flex-shrink: 0;
}

.copy-btn.copied { background: rgba(16, 185, 129, 0.15); color: #047857; }
.copy-btn:hover { background: rgba(15, 23, 42, 0.1); }

.state { padding: 16px; border-radius: 12px; font-size: 14px; }
.state.error { background: rgba(239, 68, 68, .12); color: #b91c1c; }
.state.empty { background: rgba(148, 163, 184, .15); color: #475569; }
</style>
