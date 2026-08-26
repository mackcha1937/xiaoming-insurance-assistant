import { createRouter, createWebHistory } from 'vue-router'

import XiaomingPage from '@/views/XiaomingPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/xiaoming' },
    { path: '/xiaoming', name: 'xiaoming', component: XiaomingPage },
    { path: '/:pathMatch(.*)*', redirect: '/xiaoming' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
