import { createApp } from 'vue'
import { MotionPlugin } from '@vueuse/motion'
import App from './App.vue'
import { router } from './router/index.js'
import './assets/glass.css'
import './stores/accent.js'  // initialise CSS vars immediately

createApp(App)
  .use(router)
  .use(MotionPlugin)
  .mount('#app')
