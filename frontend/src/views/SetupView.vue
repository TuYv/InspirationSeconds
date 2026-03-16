<template>
  <div class="page">
    <header class="page-header">
      <h1>配置 Notion</h1>
      <p>完成以下步骤，将你的微信消息同步到 Notion。</p>
    </header>

    <!-- Step indicator -->
    <div class="steps">
      <div v-for="(label, i) in stepLabels" :key="i"
           :class="['step', { active: step === i + 1, done: step > i + 1 }]">
        <span class="step-num">{{ step > i + 1 ? '✓' : i + 1 }}</span>
        <span class="step-label">{{ label }}</span>
      </div>
    </div>

    <section class="panel">
      <!-- Step 1: Token input -->
      <div v-if="step === 1">
        <h2>输入 Notion Integration Token</h2>
        <p class="hint">在 <a href="https://www.notion.so/my-integrations" target="_blank">Notion 集成页面</a> 创建集成后，复制 "Internal Integration Secret"。</p>
        <div class="form">
          <label class="field">
            <span>Integration Token</span>
            <input v-model.trim="notionToken" type="password"
                   placeholder="secret_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" />
          </label>
        </div>
        <div v-if="tokenError" class="state error">{{ tokenError }}</div>
        <div class="actions">
          <button class="primary" :disabled="!notionToken || validating" @click="validateToken">
            {{ validating ? '验证中...' : '验证并继续' }}
          </button>
        </div>
      </div>

      <!-- Step 2: Database selection -->
      <div v-if="step === 2">
        <h2>选择 Notion 数据库</h2>
        <p class="hint">以下是该 Integration 可访问的数据库，请选择用于同步消息的数据库。</p>
        <div v-if="databases.length === 0" class="state empty">
          未找到可访问的数据库。请确保你已在 Notion 数据库中添加此 Integration。
        </div>
        <div v-else class="db-list">
          <label v-for="db in databases" :key="db.id"
                 :class="['db-item', { selected: selectedDb === db.id }]">
            <input type="radio" :value="db.id" v-model="selectedDb" />
            <span class="db-title">{{ db.title }}</span>
            <span class="db-id">{{ db.id }}</span>
          </label>
        </div>
        <div v-if="saveError" class="state error">{{ saveError }}</div>
        <div class="actions">
          <button class="ghost" @click="step = 1">上一步</button>
          <button class="primary" :disabled="!selectedDb || saving" @click="saveConfig">
            {{ saving ? '保存中...' : '保存配置' }}
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { apiFetch } from '../utils/api';

const router = useRouter();

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
    router.push('/dashboard');
  } catch {
    saveError.value = '网络错误，请稍后重试。';
  } finally {
    saving.value = false;
  }
};
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

.steps {
  display: flex;
  gap: 0;
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

.step + .step { margin-left: 8px; }

.step.active {
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
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

.step.active .step-num { background: #2563eb; }
.step.done .step-num { background: #10b981; }

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

.panel h2 { margin: 0 0 8px; font-size: 20px; }
.hint { color: var(--muted); font-size: 14px; margin: 0 0 16px; }
.hint a { color: var(--accent-2); }

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
  border-color: #2563eb;
  background: rgba(37, 99, 235, 0.06);
}

.db-item input[type="radio"] { display: none; }

.db-title { font-weight: 600; font-size: 15px; flex: 1; }
.db-id { font-size: 12px; color: var(--muted); font-family: monospace; }

.actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.state { padding: 12px 16px; border-radius: 12px; font-size: 14px; }
.state.error { background: rgba(239, 68, 68, .12); color: #b91c1c; }
.state.empty { background: rgba(148, 163, 184, .15); color: #475569; }

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
  box-shadow: 0 12px 28px rgba(37, 99, 235, .2);
}

button.primary:disabled { opacity: .6; cursor: not-allowed; }

button.ghost {
  background: rgba(15, 23, 42, .05);
  color: var(--ink);
  padding: 10px 16px;
  border-radius: 12px;
  font-weight: 600;
}
</style>
