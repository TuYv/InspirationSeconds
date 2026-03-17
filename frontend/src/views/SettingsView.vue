<template>
  <div class="page">
    <header class="page-header">
      <h1>设置</h1>
      <p>管理你的账号与 Notion 连接。</p>
    </header>

    <!-- Account card (read-only) -->
    <section class="panel account-card">
      <div v-if="accountLoading" class="state empty">加载中...</div>
      <div v-else-if="account" class="account-inner">
        <div class="profile">
          <div class="avatar">
            <img v-if="account.avatarUrl" :src="account.avatarUrl" alt="avatar" />
            <span v-else>{{ displayName.slice(0, 1) }}</span>
          </div>
          <div class="profile-info">
            <span class="profile-name">{{ displayName }}</span>
            <span :class="['badge', statusBadgeClass]">{{ account.status }}</span>
          </div>
        </div>
        <div class="account-fields">
          <div class="field-row">
            <span class="field-label">应用类型</span>
            <span class="field-value">{{ account.appType }}</span>
          </div>
          <div class="field-row">
            <span class="field-label">账号类型</span>
            <span :class="['badge', account.isGuest ? 'guest' : 'regular']">
              {{ account.isGuest ? '访客用户' : '正式用户' }}
            </span>
          </div>
          <div class="field-row db-row">
            <span class="field-label">Notion 数据库 ID</span>
            <div class="db-value-row">
              <span class="field-value db-id">{{ truncatedDbId }}</span>
              <button class="copy-btn" :class="{ copied: copyDone }" @click="copyDbId">
                {{ copyDone ? '已复制' : '复制' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 访客升级提醒 -->
        <div v-if="account.isGuest" class="guest-banner">
          <div class="guest-banner-text">
            <span class="guest-banner-icon">✦</span>
            <div>
              <p class="guest-banner-title">当前使用访客模式</p>
              <p class="guest-banner-desc">笔记保存在公共工作区，配置专属 Notion 可升级为正式用户。</p>
            </div>
          </div>
          <button class="guest-banner-btn" @click="scrollToWizard">立即配置 →</button>
        </div>
      </div>
    </section>

    <!-- Notification preferences -->
    <section class="panel pref-panel">
      <div class="panel-title">通知偏好</div>
      <div class="pref-row">
        <div class="pref-info">
          <span class="pref-label">每日推图</span>
          <span class="pref-desc">每天 08:00 推送 AI 日签卡片到微信</span>
        </div>
        <button
          :class="['toggle', { on: dailyCardEnabled }]"
          :aria-checked="dailyCardEnabled"
          role="switch"
          @click="toggleDailyCard"
        >
          <span class="toggle-thumb" />
        </button>
      </div>
    </section>

    <!-- Change connection wizard -->
    <section class="panel" ref="wizardSection">
      <div class="panel-title">修改连接</div>

      <!-- Step indicator -->
      <div class="steps">
        <div v-for="(label, i) in stepLabels" :key="i"
             :class="['step', { active: step === i + 1, done: step > i + 1 }]">
          <span class="step-num">{{ step > i + 1 ? '✓' : i + 1 }}</span>
          <span class="step-label">{{ label }}</span>
        </div>
      </div>

      <!-- Step 1: Token input -->
      <div v-if="step === 1">
        <h2>更换 Notion Token</h2>
        <p class="hint">如不更换 Token，也可保留当前配置并只更换数据库（需重新验证）。</p>
        <div class="form">
          <label class="field">
            <span>Integration Token</span>
            <input v-model.trim="notionToken" type="password"
                   placeholder="secret_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" />
          </label>
        </div>
        <div v-if="tokenError" class="state error">{{ tokenError }}</div>
        <div class="actions">
          <router-link to="/notion" class="btn-ghost">取消</router-link>
          <button class="primary" :disabled="!notionToken || validating" @click="validateToken">
            {{ validating ? '验证中...' : '验证并继续' }}
          </button>
        </div>
      </div>

      <!-- Step 2: Database selection -->
      <div v-if="step === 2">
        <h2>选择数据库</h2>
        <p class="hint">选择用于存储灵感记录的数据库。</p>
        <div v-if="databases.length === 0" class="state empty">
          未找到可访问的数据库。请确保已在 Notion 数据库中添加此 Integration。
        </div>
        <div v-else class="db-list">
          <label v-for="db in databases" :key="db.id"
                 :class="['db-item', { selected: selectedDb === db.id }]">
            <input type="radio" :value="db.id" v-model="selectedDb" />
            <span class="db-title">{{ db.title }}</span>
            <span class="db-id-small">{{ db.id }}</span>
          </label>
        </div>
        <div v-if="saveError" class="state error">{{ saveError }}</div>
        <div class="actions">
          <button class="ghost" @click="step = 1">上一步</button>
          <button class="primary" :disabled="!selectedDb || saving" @click="saveConfig">
            {{ saving ? '保存中...' : '保存修改' }}
          </button>
        </div>
      </div>

      <!-- Success -->
      <div v-if="step === 3" class="success-msg">
        <span class="success-icon">✓</span>
        <p>配置已更新！正在跳转...</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import type { ComponentPublicInstance } from 'vue';
import { useRouter } from 'vue-router';
import { apiFetch } from '../utils/api';

const router = useRouter();

// ── Account card ──────────────────────────────────────────────────────
type ConfigView = {
  openId: string;
  appType: string;
  status: string;
  databaseId: string;
  isGuest: boolean | null;
  dailyCardEnabled: boolean;
  updatedAt: string | null;
  nickname?: string | null;
  avatarUrl?: string | null;
};

const account = ref<ConfigView | null>(null);
const accountLoading = ref(true);
const copyDone = ref(false);

const displayName = computed(() => account.value?.nickname || '微信用户');

const statusBadgeClass = computed(() => {
  const s = (account.value?.status ?? '').toUpperCase();
  if (s === 'ACTIVE') return 'success';
  if (s === 'INACTIVE') return 'warning';
  return '';
});

const truncatedDbId = computed(() => {
  const id = account.value?.databaseId ?? '';
  return id.length > 24 ? id.slice(0, 12) + '...' + id.slice(-8) : id;
});

async function copyDbId() {
  const id = account.value?.databaseId ?? '';
  if (!id) return;
  await navigator.clipboard.writeText(id);
  copyDone.value = true;
  setTimeout(() => { copyDone.value = false; }, 2000);
}

const wizardSection = ref<HTMLElement | null>(null);
const dailyCardEnabled = ref(true);

function scrollToWizard() {
  wizardSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// ── Wizard ────────────────────────────────────────────────────────────
const step = ref(1);
const stepLabels = ['验证 Token', '选择数据库'];

const notionToken = ref('');
const validating = ref(false);
const tokenError = ref('');

type DbInfo = { id: string; title: string };
const databases = ref<DbInfo[]>([]);
const selectedDb = ref('');

const saving = ref(false);
const saveError = ref('');

const validateToken = async () => {
  tokenError.value = '';
  validating.value = true;
  try {
    const resp = await apiFetch('/api/notion/validate-token', {
      method: 'POST',
      body: JSON.stringify({ notionToken: notionToken.value }),
    });
    const data = await resp.json();
    if (!resp.ok) {
      tokenError.value = data.error === 'invalid_token'
        ? 'Token 无效，请检查是否填写正确。'
        : '验证失败，请稍后重试。';
      return;
    }
    databases.value = data.databases as DbInfo[];
    step.value = 2;
  } catch {
    tokenError.value = '网络错误，请稍后重试。';
  } finally {
    validating.value = false;
  }
};

const saveConfig = async () => {
  saveError.value = '';
  saving.value = true;
  try {
    const resp = await apiFetch('/api/user/config', {
      method: 'PUT',
      body: JSON.stringify({ notionToken: notionToken.value, databaseId: selectedDb.value }),
    });
    if (!resp.ok) {
      saveError.value = '保存失败，请稍后重试。';
      return;
    }
    step.value = 3;
    setTimeout(() => router.push('/notion'), 1200);
  } catch {
    saveError.value = '网络错误，请稍后重试。';
  } finally {
    saving.value = false;
  }
};

async function toggleDailyCard() {
  const next = !dailyCardEnabled.value;
  dailyCardEnabled.value = next;
  await apiFetch('/api/user/preferences', {
    method: 'PATCH',
    body: JSON.stringify({ dailyCardEnabled: next }),
  });
}

onMounted(async () => {
  try {
    const resp = await apiFetch('/api/user/me');
    if (resp.ok) {
      const data: ConfigView = await resp.json();
      account.value = data;
      dailyCardEnabled.value = data.dailyCardEnabled ?? true;
    }
  } finally {
    accountLoading.value = false;
  }
});
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 40px 24px 64px;
  max-width: 640px;
  margin: 0 auto;
}

.page-header h1 { margin: 0 0 8px; font-size: 28px; }
.page-header p { margin: 0; color: var(--muted); }

/* Account card */
.account-card { padding: 24px 28px; }

.account-inner {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.profile {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(45, 122, 74, 0.1);
  color: #1a5c35;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 18px;
  overflow: hidden;
  flex-shrink: 0;
}

.avatar img { width: 100%; height: 100%; object-fit: cover; }

.profile-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.profile-name {
  font-weight: 700;
  font-size: 17px;
}

.account-fields {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.field-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}

.field-label {
  color: var(--muted);
  min-width: 100px;
  flex-shrink: 0;
}

.field-value {
  font-weight: 600;
  color: var(--ink);
}

.db-row { flex-wrap: wrap; }

.db-value-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.db-id {
  font-family: monospace;
  font-size: 13px;
}

.copy-btn {
  font-size: 12px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.06);
  color: var(--ink);
  cursor: pointer;
  transition: background 0.15s;
  flex-shrink: 0;
}
.copy-btn.copied { background: rgba(45, 122, 74, 0.15); color: #1a5c35; }
.copy-btn:hover { background: rgba(15, 23, 42, 0.1); }

/* Account type badges */
.badge.guest {
  background: rgba(234, 179, 8, 0.15);
  color: #92400e;
  font-size: 12px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 20px;
}

.badge.regular {
  background: rgba(45, 122, 74, 0.12);
  color: #1a5c35;
  font-size: 12px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 20px;
}

/* Guest upgrade banner */
.guest-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  background: rgba(234, 179, 8, 0.08);
  border: 1px solid rgba(234, 179, 8, 0.3);
  border-radius: 14px;
  flex-wrap: wrap;
}

.guest-banner-text {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.guest-banner-icon {
  font-size: 16px;
  color: #b45309;
  flex-shrink: 0;
  margin-top: 2px;
}

.guest-banner-title {
  margin: 0 0 2px;
  font-size: 14px;
  font-weight: 700;
  color: #92400e;
}

.guest-banner-desc {
  margin: 0;
  font-size: 13px;
  color: #b45309;
  line-height: 1.5;
}

.guest-banner-btn {
  background: #b45309;
  color: white;
  padding: 8px 16px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
  flex-shrink: 0;
  transition: background 0.15s, transform 0.1s;
}

.guest-banner-btn:hover {
  background: #92400e;
  transform: translateY(-1px);
}

/* Wizard panel */
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

.panel-title {
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: .08em;
  color: var(--muted);
}

.steps {
  display: flex;
  gap: 8px;
}

.step {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  padding: 12px 16px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.04);
  color: var(--muted);
  font-size: 14px;
  font-weight: 500;
}

.step.active {
  background: rgba(45, 122, 74, 0.1);
  color: #1a5c35;
}

.step.done {
  background: rgba(16, 185, 129, 0.1);
  color: #047857;
}

.step-num {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: currentColor;
  color: white;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.step.active .step-num { background: #2d7a4a; }
.step.done .step-num { background: #10b981; }

.panel h2 { margin: 0 0 8px; font-size: 20px; }
.hint { color: var(--muted); font-size: 14px; margin: 0 0 16px; }

.form { display: grid; gap: 12px; }

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
}

.db-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.db-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.db-item.selected {
  border-color: #2d7a4a;
  background: rgba(45, 122, 74, 0.06);
}

.db-item input[type="radio"] { display: none; }
.db-title { font-weight: 600; font-size: 15px; flex: 1; }
.db-id-small { font-size: 12px; color: var(--muted); font-family: monospace; }

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.state { padding: 12px 16px; border-radius: 12px; font-size: 14px; }
.state.error { background: rgba(239, 68, 68, .12); color: #b91c1c; }
.state.empty { background: rgba(148, 163, 184, .15); color: #475569; }

.success-msg {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px;
  background: rgba(45, 122, 74, 0.1);
  border-radius: 14px;
  color: #1a5c35;
  font-weight: 600;
}

.success-icon { font-size: 20px; }

.btn-ghost {
  font-size: 14px;
  font-weight: 600;
  color: var(--muted);
  text-decoration: none;
  padding: 10px 16px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.05);
  transition: background 0.15s;
}

.btn-ghost:hover { background: rgba(15, 23, 42, 0.1); }

button.primary {
  background: var(--accent-2);
  color: white;
  padding: 10px 20px;
  border-radius: 12px;
  font-weight: 600;
  transition: transform .15s, box-shadow .2s;
}

button.primary:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(45, 122, 74, .25);
}

button.primary:disabled { opacity: .6; cursor: not-allowed; }

/* Preferences panel */
.pref-panel { padding: 20px 28px; }

.pref-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.pref-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.pref-label {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}

.pref-desc {
  font-size: 13px;
  color: var(--muted);
}

/* Toggle switch */
.toggle {
  position: relative;
  width: 44px;
  height: 26px;
  border-radius: 13px;
  background: rgba(15, 23, 42, 0.15);
  border: none;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.2s;
  padding: 0;
}

.toggle.on {
  background: #2d7a4a;
}

.toggle-thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: white;
  box-shadow: 0 1px 4px rgba(0,0,0,.2);
  transition: transform 0.2s;
  display: block;
}

.toggle.on .toggle-thumb {
  transform: translateX(18px);
}

button.ghost {
  background: rgba(15, 23, 42, .05);
  color: var(--ink);
  padding: 10px 16px;
  border-radius: 12px;
  font-weight: 600;
}
</style>
