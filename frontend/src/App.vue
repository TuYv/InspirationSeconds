<template>
  <div class="app-shell">
    <!-- Login screen for unauthenticated users -->
    <div v-if="!loggedIn" class="page login-page">
      <header class="hero">
        <div class="hero-tag">Notion Config Console</div>
        <h1>你的 Notion 配置，一眼可见</h1>
        <p>完成微信授权后即可管理配置，同步你的想法到 Notion。</p>
      </header>

      <section class="panel">
        <div class="auth-card">
          <div>
            <h3>微信授权获取身份</h3>
            <p>请在微信内打开本页面，点击按钮完成授权后即可管理配置。</p>
          </div>
          <div class="auth-actions">
            <button class="primary" type="button" @click="startOAuth">微信授权登录</button>
          </div>
          <p v-if="!isWeChat" class="hint muted">
            当前环境非微信浏览器，可使用下方二维码扫码登录。
          </p>
        </div>

        <div v-if="!isWeChat" class="qr-card">
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

        <div v-if="loginError" class="state error">{{ loginError }}</div>
      </section>
    </div>

    <!-- Authenticated layout -->
    <template v-else>
      <nav class="top-nav">
        <span class="nav-brand">Notion Config</span>
        <div class="nav-links">
          <router-link to="/dashboard">概览</router-link>
          <router-link to="/settings">设置</router-link>
          <button class="ghost-sm" @click="doLogout">退出</button>
        </div>
      </nav>
      <router-view />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import QRCode from 'qrcode';
import { useAuth } from './composables/useAuth';

const router = useRouter();
const { isLoggedIn, setToken, logout } = useAuth();

const loggedIn = ref(isLoggedIn());
const isWeChat = /micromessenger/i.test(navigator.userAgent);
const qrDataUrl = ref('');
const qrState = ref('');
const qrStatus = ref<'PENDING' | 'SUCCESS' | 'EXPIRED' | ''>('');
const loginError = ref('');
let qrTimer: number | null = null;

const apiBase = import.meta.env.VITE_API_BASE ?? '';

function handleToken(token: string) {
  setToken(token);
  loggedIn.value = true;
  // Remove token from URL
  const url = new URL(window.location.href);
  url.searchParams.delete('token');
  window.history.replaceState({}, '', url.toString());
  router.push('/dashboard');
}

const startOAuth = () => {
  const returnUrl = `${window.location.origin}${window.location.pathname}`;
  window.location.href = `${apiBase}/wx/oauth/start?returnUrl=${encodeURIComponent(returnUrl)}`;
};

const startQrLogin = async () => {
  if (qrTimer) { window.clearInterval(qrTimer); qrTimer = null; }
  qrStatus.value = '';
  try {
    const resp = await fetch(`${apiBase}/wx/oauth/qr/start`);
    if (!resp.ok) throw new Error('二维码初始化失败');
    const data = await resp.json();
    qrState.value = data.state;
    qrDataUrl.value = await QRCode.toDataURL(data.qrUrl as string, { width: 220, margin: 1 });
    pollQrStatus();
  } catch {
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
      if (data.status === 'SUCCESS' && data.token) {
        if (qrTimer) { window.clearInterval(qrTimer); qrTimer = null; }
        handleToken(data.token as string);
      } else if (data.status === 'EXPIRED') {
        if (qrTimer) { window.clearInterval(qrTimer); qrTimer = null; }
      }
    } catch { /* ignore transient errors */ }
  }, 2000);
};

function doLogout() {
  logout();
  loggedIn.value = false;
  if (qrTimer) { window.clearInterval(qrTimer); qrTimer = null; }
  if (!isWeChat) startQrLogin();
}

onMounted(() => {
  const params = new URLSearchParams(window.location.search);
  const tokenParam = params.get('token');

  if (tokenParam) {
    handleToken(tokenParam);
    return;
  }

  if (isLoggedIn()) {
    loggedIn.value = true;
    return;
  }

  // Auto-start login flow
  if (isWeChat) {
    const attempted = sessionStorage.getItem('wx_oauth_attempted');
    if (!attempted) {
      sessionStorage.setItem('wx_oauth_attempted', '1');
      startOAuth();
    }
  } else {
    startQrLogin();
  }
});
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.top-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 10;
}

.nav-brand {
  font-weight: 700;
  font-size: 16px;
  color: var(--ink);
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 20px;
  font-size: 14px;
  font-weight: 500;
}

.nav-links a {
  color: var(--muted);
  text-decoration: none;
  transition: color 0.15s;
}

.nav-links a:hover,
.nav-links a.router-link-active {
  color: var(--ink);
}

button.ghost-sm {
  background: none;
  border: none;
  color: var(--muted);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  padding: 4px 8px;
}

button.ghost-sm:hover {
  color: var(--ink);
}

/* Login page reuses shared styles */
.login-page {
  display: flex;
  flex-direction: column;
  gap: 28px;
  padding: 48px 24px 64px;
  max-width: 720px;
  margin: 0 auto;
}

.hero {
  padding: 32px 32px 24px;
  border-radius: 24px;
  background: linear-gradient(130deg, rgba(255,255,255,.95), rgba(255,255,255,.7));
  box-shadow: var(--shadow);
  border: 1px solid rgba(255,255,255,.6);
  backdrop-filter: blur(8px);
}

.hero-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(217,119,6,.12);
  color: #b45309;
  font-weight: 600;
  font-size: 12px;
  letter-spacing: .12em;
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
  border: 1px dashed rgba(37,99,235,.35);
  background: rgba(37,99,235,.06);
}

.auth-card h3 { margin: 0 0 6px; }
.auth-card p { margin: 0; color: var(--muted); font-size: 14px; }

.auth-actions { display: flex; flex-wrap: wrap; gap: 12px; }
.hint.muted { color: var(--muted); }

.qr-card {
  display: flex;
  gap: 18px;
  align-items: center;
  flex-wrap: wrap;
  padding: 18px;
  border-radius: 16px;
  border: 1px solid rgba(15,23,42,.1);
  background: rgba(255,255,255,.8);
}

.qr-preview {
  width: 220px;
  height: 220px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid rgba(15,23,42,.08);
  display: grid;
  place-items: center;
}

.qr-preview img { width: 100%; height: 100%; object-fit: contain; }
.qr-loading { color: var(--muted); font-size: 13px; }

.qr-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 200px;
}

.qr-info h3 { margin: 0; }
.qr-info p { margin: 0; color: var(--muted); font-size: 14px; }
.qr-expired { color: #b91c1c; font-weight: 600; }

.state { padding: 16px; border-radius: 12px; font-size: 14px; }
.state.error { background: rgba(239,68,68,.12); color: #b91c1c; }

button.primary {
  background: var(--accent-2);
  color: white;
  padding: 10px 18px;
  border-radius: 12px;
  font-weight: 600;
  transition: transform .15s ease, box-shadow .2s ease;
}

button.primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(37,99,235,.2);
}

button.ghost {
  background: rgba(15,23,42,.05);
  color: var(--ink);
  padding: 10px 16px;
  border-radius: 12px;
  font-weight: 600;
}

@media (max-width: 720px) {
  .login-page { padding: 32px 16px 48px; }
  .hero h1 { font-size: 28px; }
  .panel { padding: 20px; }
}
</style>
