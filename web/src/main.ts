import ui from '@nuxt/ui/vue-plugin'
import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import './style.css'
import HomeView from './views/HomeView.vue'
import RivalsView from './views/RivalsView.vue'
import StatsView from './views/StatsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: HomeView },
    { path: '/stats', component: StatsView },
    { path: '/rivals', component: RivalsView },
    // Old links and push notifications: the chat lives in the floating dock now
    { path: '/chat', redirect: { path: '/', query: { chat: '1' } } },
    { path: '/h2h', redirect: '/rivals' },
  ],
})

createApp(App).use(router).use(ui).mount('#app')
