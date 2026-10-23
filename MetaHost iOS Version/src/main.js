import { createApp } from 'vue'
import { MotionPlugin } from '@vueuse/motion'
import App from './App.vue'
import { router } from './router/index.js'
import './assets/glass.css'
import './assets/nova.css'     // light Nova + Apple glass layer, scoped to .nova
import './assets/command.css'  // Home Command Center, scoped to .cmd
import './stores/accent.js'    // initialise CSS vars immediately
import './stores/theme.js'     // ...and the --cc-* set, before first paint

createApp(App)
  .use(router)
  .use(MotionPlugin)
  .mount('#app')
