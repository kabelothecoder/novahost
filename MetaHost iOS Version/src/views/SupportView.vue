<template>
  <div class="support-view">
    <canvas ref="bgCanvas" class="support-bg" />

    <header class="support-header">
      <h2 class="screen-title">Support & Help</h2>
      <span class="screen-sub">Everything you need to know</span>
    </header>

    <!-- Search -->
    <div class="search-wrap" style="position:relative;z-index:2;padding:12px 20px 0;">
      <input
        v-model="query"
        type="search"
        class="input-glass search-input"
        placeholder="Search FAQ…"
      />
      <span class="search-icon">🔍</span>
    </div>

    <div class="support-scroll">

      <!-- Contact strip -->
      <div class="contact-strip glass liquid">
        <a class="contact-btn" href="mailto:support@novaedge.io">
          <span class="contact-ico">📧</span>
          <span>Email</span>
        </a>
        <div class="strip-div" />
        <a class="contact-btn" href="https://t.me/novaedge" target="_blank">
          <span class="contact-ico">✈️</span>
          <span>Telegram</span>
        </a>
        <div class="strip-div" />
        <a class="contact-btn" href="#" @click.prevent="">
          <span class="contact-ico">💬</span>
          <span>Live Chat</span>
        </a>
      </div>

      <!-- FAQ tiles — high-density vertical list -->
      <div class="faq-section" v-for="cat in filteredCategories" :key="cat.title">
        <h3 class="faq-cat-title">{{ cat.title }}</h3>
        <div class="faq-list">
          <div
            v-for="item in cat.items"
            :key="item.q"
            class="faq-tile glass liquid"
            :class="{ open: item.open }"
            @click="item.open = !item.open"
          >
            <div class="faq-header">
              <span class="faq-q">{{ item.q }}</span>
              <svg class="faq-chevron" :class="{ open: item.open }" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
            </div>
            <Transition name="accordion">
              <div v-if="item.open" class="faq-answer">{{ item.a }}</div>
            </Transition>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { generateBgLoop } from '../utils/bgLoop.js'

const bgCanvas = ref(null)
const query = ref('')
let animId = null

onMounted(() => {
  const { drawFrame } = generateBgLoop(bgCanvas.value)
  let last = 0
  const loop = (t) => {
    if (t - last > 33) { drawFrame(t); last = t }
    animId = requestAnimationFrame(loop)
  }
  animId = requestAnimationFrame(loop)
})
onUnmounted(() => cancelAnimationFrame(animId))

const categories = ref([
  {
    title: '🚀 Getting Started',
    items: [
      { q: 'What is Nova Edge?', a: 'Nova Edge is a premium AI-powered Expert Advisor hosting platform. Your EAs run 24/7 on our low-latency servers with SMS and Telegram alerts.', open: false },
      { q: 'How do I connect my MT5 account?', a: 'Go to the Terminal tab, enter your broker server, account number and investor/master password. Nova Edge uses read-only credentials by default for safety.', open: false },
      { q: 'Which brokers are supported?', a: 'Nova Edge works with any MetaTrader 4 or 5 broker. Popular choices include Exness, IC Markets, FP Markets, and XM.', open: false },
    ]
  },
  {
    title: '⚡ Expert Advisors',
    items: [
      { q: 'How do I upload a custom EA?', a: 'Navigate to Home → Expert Advisors → Upload. Your .ex5 or .ex4 file is encrypted and stored securely on our servers.', open: false },
      { q: 'Can I run multiple EAs simultaneously?', a: 'Yes! Nova Edge supports up to 10 concurrent EAs per account tier. Each EA gets its own isolated thread.', open: false },
      { q: 'What happens if the EA crashes?', a: 'Our watchdog system detects crashes instantly and restarts the EA. You receive a push notification for each event.', open: false },
      { q: 'How do I set lot sizes per pair?', a: 'Go to Pair Management → Allowed Pairs, expand any pair card and configure the Lot Size field.', open: false },
    ]
  },
  {
    title: '🔒 Security',
    items: [
      { q: 'Is my password stored?', a: 'Never. Credentials are AES-256 encrypted and stored in an HSM. We use investor-read-only passwords where possible.', open: false },
      { q: 'Can Nova Edge withdraw my funds?', a: 'No. We use investor passwords which are read-only — Nova Edge can view trades but cannot withdraw or make deposits.', open: false },
    ]
  },
  {
    title: '💳 Billing & Plans',
    items: [
      { q: 'What plans are available?', a: 'Starter ($29/mo), Pro ($79/mo), and Institutional ($299/mo). All plans include 24/7 uptime, real-time alerts, and basic EA support.', open: false },
      { q: 'How do I cancel my subscription?', a: 'Settings → Account → Subscription → Cancel Plan. No cancellation fees. Your EAs stop at the end of the billing period.', open: false },
    ]
  },
])

const filteredCategories = computed(() => {
  if (!query.value.trim()) return categories.value
  const q = query.value.toLowerCase()
  return categories.value
    .map(cat => ({
      ...cat,
      items: cat.items.filter(i => i.q.toLowerCase().includes(q) || i.a.toLowerCase().includes(q))
    }))
    .filter(cat => cat.items.length > 0)
})
</script>

<style scoped>
.support-view {
  position: relative;
  width: 100%;
  height: 100dvh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.support-bg { position: absolute; inset: 0; width: 100%; height: 100%; z-index: 0; }

.support-header {
  position: relative;
  z-index: 2;
  padding: calc(var(--safe-top) + 16px) 20px 0;
}
.screen-title { font-size: 1.4rem; font-weight: 700; color: #E8F0FF; letter-spacing: -0.02em; }
.screen-sub { font-family: var(--font-mono); font-size: 0.68rem; color: rgba(var(--accent-rgb), 0.5); letter-spacing: 0.1em; display: block; margin-top: 2px; }

.search-input { padding-right: 40px; }
.search-icon { position: absolute; right: 34px; top: 50%; transform: translateY(-50%); font-size: 0.9rem; opacity: 0.4; pointer-events: none; }

.support-scroll {
  position: relative;
  z-index: 1;
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
  padding: 14px 20px 130px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ── Contact Strip ── */
.contact-strip {
  border-radius: var(--radius-tight);
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 12px;
}
.contact-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  text-decoration: none;
  color: rgba(232,240,255,0.75);
  font-size: 0.78rem;
  font-weight: 500;
  transition: color 0.2s;
}
.contact-btn:hover { color: var(--accent); }
.contact-ico { font-size: 1.3rem; }
.strip-div { width: 1px; height: 32px; background: rgba(255,255,255,0.07); }

/* ── FAQ Sections ── */
.faq-section { display: flex; flex-direction: column; gap: 8px; }
.faq-cat-title {
  font-size: 0.82rem;
  font-weight: 600;
  color: rgba(var(--accent-rgb), 0.6);
  letter-spacing: 0.04em;
  padding: 0 4px;
}
.faq-list { display: flex; flex-direction: column; gap: 6px; }

.faq-tile {
  border-radius: var(--radius-tight);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.25s var(--smooth);
}
.faq-tile.open {
  background: rgba(var(--accent-rgb), 0.06);
  border-color: rgba(var(--accent-rgb), 0.25);
}

.faq-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 13px 14px;
  gap: 10px;
}
.faq-q {
  font-size: 0.88rem;
  color: #E8F0FF;
  font-weight: 500;
  line-height: 1.3;
  flex: 1;
  text-align: left;
}
.faq-chevron { flex-shrink: 0; transition: transform 0.3s var(--spring); color: rgba(var(--accent-rgb), 0.5); }
.faq-chevron.open { transform: rotate(180deg); color: var(--accent); }

.faq-answer {
  padding: 0 14px 14px;
  font-size: 0.82rem;
  color: rgba(232,240,255,0.6);
  line-height: 1.6;
}

/* Accordion */
.accordion-enter-active, .accordion-leave-active {
  transition: all 0.3s var(--smooth);
  overflow: hidden;
}
.accordion-enter-from, .accordion-leave-to { max-height: 0; opacity: 0; }
.accordion-enter-to, .accordion-leave-from { max-height: 200px; opacity: 1; }
</style>
