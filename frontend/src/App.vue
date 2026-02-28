<template>
  <div class="page">
    <header class="hero">
      <div class="hero-tag">Notion Config Console</div>
      <h1>你的 Notion 配置，一眼可见</h1>
      <p>完成微信授权后即可查看当前配置状态，也支持调试时直接传 openId。</p>
    </header>

    <section class="panel">
      <div class="panel-header">
        <div>
          <h2>配置查询</h2>
          <span class="hint">支持 URL 传参：<code>?openId=xxxx</code>（调试用）</span>
        </div>
        <div class="actions">
          <button class="ghost" type="button" @click="fillFromStorage">读取已保存 openId</button>
          <button class="primary" type="button" :disabled="loading" @click="fetchConfig">
            {{ loading ? '加载中...' : '查询配置' }}
          </button>
        </div>
      </div>

      <div v-if="!openId" class="auth-card">
        <div>
          <h3>微信授权获取 openId</h3>
          <p>请在微信内打开本页面，点击按钮完成授权后即可查看配置。</p>
        </div>
        <div class="auth-actions">
          <button class="primary" type="button" @click="startOAuth">微信授权登录</button>
          <button class="ghost" type="button" @click="toggleManual">
            {{ manualMode ? '收起手动输入' : '手动输入 openId' }}
          </button>
        </div>
        <p v-if="!isWeChat" class="hint muted">
          当前环境非微信浏览器，可使用下方二维码扫码登录。
        </p>
      </div>

      <div v-if="!isWeChat && !openId" class="qr-card">
        <div class="qr-preview">
          <img v-if="qrDataUrl" :src="qrDataUrl" alt="wechat qr" />
          <div v-else class="qr-loading">二维码生成中...</div>
        </div>
        <div class="qr-info">
          <h3>PC 端扫码登录</h3>
          <p>使用微信扫码后授权，页面将自动登录。</p>
          <p v-if="qrStatus === 'EXPIRED'" class="qr-expired">二维码已过期，请刷新。</p>
          <button class="ghost" type="button" @click="startQrLogin">刷新二维码</button>
        </div>
      </div>

      <div v-if="manualMode" class="form">
        <label class="field">
          <span>OpenID</span>
          <input v-model.trim="openId" placeholder="请输入微信 openId（调试用）" />
        </label>
      </div>

      <div v-if="error" class="state error">
        {{ error }}
      </div>

      <div v-else-if="!config" class="state empty">
        尚未加载配置。完成微信授权后将自动展示，也可手动点击“查询配置”。
      </div>

      <div v-else class="result">
        <div class="result-header">
          <div class="profile">
            <div class="avatar">
              <img v-if="config?.avatarUrl" :src="config.avatarUrl" alt="avatar" />
              <span v-else>{{ displayName.slice(0, 1) }}</span>
            </div>
            <div>
              <h3>{{ displayName }}</h3>
              <p>最后更新：{{ formattedUpdatedAt }}</p>
            </div>
          </div>
          <span :class="['badge', statusBadgeClass]">{{ statusLabel }}</span>
        </div>

        <div class="grid">
          <div v-for="item in displayFields" :key="item.label" class="field-card">
            <span class="label">{{ item.label }}</span>
            <span class="value">{{ item.value || '-' }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="note">
      <h4>接入建议</h4>
      <p>微信授权完成后，openId 会自动写入 localStorage（key: <code>wx_openid</code>），下次打开可直接读取。</p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import QRCode from 'qrcode';

type ConfigView = {
  openId: string;
  appType: string;
  status: string;
  databaseId: string;
  isGuest: boolean | null;
  migrationStatus: string | null;
  updatedAt: string | null;
  nickname?: string | null;
  avatarUrl?: string | null;
};

const openId = ref('');
const config = ref<ConfigView | null>(null);
const loading = ref(false);
const error = ref('');
const manualMode = ref(false);
const isWeChat = /micromessenger/i.test(navigator.userAgent);
const qrDataUrl = ref('');
const qrState = ref('');
const qrStatus = ref<'PENDING' | 'SUCCESS' | 'EXPIRED' | ''>('');
let qrTimer: number | null = null;

const apiBase = import.meta.env.VITE_API_BASE ?? '';

const displayFields = computed(() => {
  if (!config.value) return [];
  return [
    { label: 'OpenID', value: config.value.openId },
    { label: '应用类型', value: config.value.appType },
    { label: '配置状态', value: config.value.status },
    { label: 'Notion 数据库 ID', value: config.value.databaseId },
    { label: '访客账号', value: config.value.isGuest ? '是' : '否' },
    { label: '迁移状态', value: config.value.migrationStatus ?? '-' }
  ];
});

const displayName = computed(() => config.value?.nickname || '微信用户');

const statusLabel = computed(() => config.value?.status ?? 'UNKNOWN');

const statusBadgeClass = computed(() => {
  const status = (config.value?.status ?? '').toUpperCase();
  if (status === 'ACTIVE') return 'success';
  if (status === 'INACTIVE') return 'warning';
  return '';
});

const formattedUpdatedAt = computed(() => {
  if (!config.value?.updatedAt) return '-';
  const date = new Date(config.value.updatedAt);
  if (Number.isNaN(date.getTime())) return config.value.updatedAt;
  return date.toLocaleString();
});

const fetchConfig = async () => {
  if (!openId.value) {
    error.value = '请先获取 openId。';
    config.value = null;
    return;
  }
  error.value = '';
  loading.value = true;
  try {
    const resp = await fetch(`${apiBase}/api/configs/by-openid?openId=${encodeURIComponent(openId.value)}`);
    if (resp.status === 404) {
      config.value = null;
      error.value = '未找到该 openId 的配置。';
      return;
    }
    if (!resp.ok) {
      throw new Error(`请求失败 (${resp.status})`);
    }
    const data = await resp.json();
    config.value = data;
    localStorage.setItem('wx_openid', openId.value);
  } catch (err) {
    const msg = err instanceof Error ? err.message : '请求失败';
    error.value = msg;
    config.value = null;
  } finally {
    loading.value = false;
  }
};

const startOAuth = () => {
  const returnUrl = `${window.location.origin}${window.location.pathname}`;
  const url = `${apiBase}/wx/oauth/start?returnUrl=${encodeURIComponent(returnUrl)}`;
  window.location.href = url;
};

const startQrLogin = async () => {
  if (qrTimer) {
    window.clearInterval(qrTimer);
    qrTimer = null;
  }
  qrStatus.value = '';
  try {
    const resp = await fetch(`${apiBase}/wx/oauth/qr/start`);
    if (!resp.ok) throw new Error('二维码初始化失败');
    const data = await resp.json();
    qrState.value = data.state;
    const url = data.qrUrl as string;
    qrDataUrl.value = await QRCode.toDataURL(url, { width: 220, margin: 1 });
    pollQrStatus();
  } catch (err) {
    qrDataUrl.value = '';
    qrStatus.value = 'EXPIRED';
  }
};

const pollQrStatus = () => {
  if (!qrState.value) return;
  qrTimer = window.setInterval(async () => {
    try {
      const resp = await fetch(`${apiBase}/wx/oauth/qr/status?state=${encodeURIComponent(qrState.value)}`);
      if (!resp.ok) return;
      const data = await resp.json();
      qrStatus.value = data.status;
      if (data.status === 'SUCCESS' && data.openId) {
        if (qrTimer) {
          window.clearInterval(qrTimer);
          qrTimer = null;
        }
        openId.value = data.openId;
        localStorage.setItem('wx_openid', openId.value);
        fetchConfig();
      } else if (data.status === 'EXPIRED') {
        if (qrTimer) {
          window.clearInterval(qrTimer);
          qrTimer = null;
        }
      }
    } catch (err) {
      // 忽略临时网络错误
    }
  }, 2000);
};

const fillFromStorage = () => {
  const saved = localStorage.getItem('wx_openid');
  if (saved) {
    openId.value = saved;
  }
};

const toggleManual = () => {
  manualMode.value = !manualMode.value;
};

onMounted(() => {
  const params = new URLSearchParams(window.location.search);
  const fromQuery = params.get('openId');
  const fromStorage = localStorage.getItem('wx_openid');
  const oauthAttempted = sessionStorage.getItem('wx_oauth_attempted');
  if (fromQuery) {
    openId.value = fromQuery;
    fetchConfig();
  } else if (fromStorage) {
    openId.value = fromStorage;
  } else if (isWeChat && !oauthAttempted) {
    sessionStorage.setItem('wx_oauth_attempted', '1');
    startOAuth();
  } else if (!isWeChat) {
    startQrLogin();
  }
});
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 28px;
  padding: 48px 24px 64px;
  max-width: 1100px;
  margin: 0 auto;
}

.hero {
  padding: 32px 32px 24px;
  border-radius: 24px;
  background: linear-gradient(130deg, rgba(255, 255, 255, 0.95), rgba(255, 255, 255, 0.7));
  box-shadow: var(--shadow);
  border: 1px solid rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(8px);
}

.hero-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(217, 119, 6, 0.12);
  color: #b45309;
  font-weight: 600;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.hero h1 {
  font-family: 'ZCOOL XiaoWei', serif;
  font-size: 36px;
  margin: 14px 0 8px;
}

.hero p {
  margin: 0;
  color: var(--muted);
  font-size: 16px;
  max-width: 560px;
}

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

.auth-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
  border-radius: 16px;
  border: 1px dashed rgba(37, 99, 235, 0.35);
  background: rgba(37, 99, 235, 0.06);
}

.auth-card h3 {
  margin: 0 0 6px;
}

.auth-card p {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
}

.auth-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.hint.muted {
  color: var(--muted);
}

.qr-card {
  display: flex;
  gap: 18px;
  align-items: center;
  flex-wrap: wrap;
  padding: 18px;
  border-radius: 16px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  background: rgba(255, 255, 255, 0.8);
}

.qr-preview {
  width: 220px;
  height: 220px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  display: grid;
  place-items: center;
}

.qr-preview img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.qr-loading {
  color: var(--muted);
  font-size: 13px;
}

.qr-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 200px;
}

.qr-info h3 {
  margin: 0;
}

.qr-info p {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
}

.qr-expired {
  color: #b91c1c;
  font-weight: 600;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.panel-header h2 {
  margin: 0 0 6px;
  font-size: 22px;
}

.hint {
  color: var(--muted);
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

button.primary {
  background: var(--accent-2);
  color: white;
  padding: 10px 18px;
  border-radius: 12px;
  font-weight: 600;
  transition: transform 0.15s ease, box-shadow 0.2s ease;
}

button.primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

button.primary:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.2);
}

button.ghost {
  background: rgba(15, 23, 42, 0.05);
  color: var(--ink);
  padding: 10px 16px;
  border-radius: 12px;
  font-weight: 600;
}

.form {
  display: grid;
  gap: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
}

.state {
  padding: 16px;
  border-radius: 12px;
  font-size: 14px;
}

.state.error {
  background: rgba(239, 68, 68, 0.12);
  color: #b91c1c;
}

.state.empty {
  background: rgba(148, 163, 184, 0.15);
  color: #475569;
}

.result {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.profile {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
  display: grid;
  place-items: center;
  font-weight: 700;
  overflow: hidden;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.result-header h3 {
  margin: 0 0 4px;
}

.result-header p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}

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

.field-card .label {
  display: block;
  font-size: 12px;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.field-card .value {
  display: block;
  font-size: 15px;
  font-weight: 600;
  margin-top: 6px;
  word-break: break-all;
}

.note {
  border-left: 4px solid var(--accent);
  padding: 16px 20px;
  background: rgba(217, 119, 6, 0.08);
  border-radius: 12px;
}

.note h4 {
  margin: 0 0 6px;
  font-size: 16px;
}

.note p {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
}

@media (max-width: 720px) {
  .page {
    padding: 32px 16px 48px;
  }

  .hero h1 {
    font-size: 28px;
  }

  .panel {
    padding: 20px;
  }
}
</style>
