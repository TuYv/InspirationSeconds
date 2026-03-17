import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('../views/EditorView.vue'),
    },
    {
      path: '/themes',
      component: () => import('../views/GalleryView.vue'),
    },
    {
      path: '/themes/edit',
      component: () => import('../views/ThemeEditorView.vue'),
    },
  ],
})

export default router
