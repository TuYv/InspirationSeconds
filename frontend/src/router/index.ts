import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router';
import { useAuth } from '../composables/useAuth';
import { apiFetch } from '../utils/api';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/setup',
    component: () => import('../views/SetupView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/dashboard',
    component: () => import('../views/DashboardView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/settings',
    component: () => import('../views/SettingsView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/notion',
    component: () => import('../views/NotionView.vue'),
    meta: { requiresAuth: true },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(async (to) => {
  const { isLoggedIn } = useAuth();

  // Unauthenticated: let App.vue's login view handle it
  if (!isLoggedIn()) {
    // Allow navigation; App.vue will show login UI when no JWT
    return true;
  }

  if (to.meta.requiresAuth) {
    // Check if user has an active config
    if (to.path !== '/setup') {
      try {
        const resp = await apiFetch('/api/user/me');
        if (resp.status === 404) {
          return '/setup';
        }
      } catch {
        return '/setup';
      }
    }
  }

  return true;
});

export default router;
