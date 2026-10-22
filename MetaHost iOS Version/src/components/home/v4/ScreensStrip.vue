<template>
  <!-- Design 2z / 3a: swipe up (or tap the grid button) for the other screens. -->
  <div class="ss-scrim" @click="$emit('close')"></div>
  <div class="ss" :style="{ top: top + 'px' }">
    <div class="ss-head">
      <div class="ss-label">SCREENS</div>
      <div class="ss-hint">Swipe for more</div>
    </div>
    <div class="ss-row">
      <div v-for="c in CARDS" :key="c.id" role="button" class="ss-card" :aria-label="'Open ' + c.name" @click="$emit('open', c)">
        <div class="ss-prev">
          <template v-if="c.id === 'look'">
            <div class="ss-grid4">
              <div class="ss-tile ss-tile-on"></div><div class="ss-tile"></div><div class="ss-tile"></div><div class="ss-tile"></div>
            </div>
            <div class="ss-foot"><div class="ss-line" style="width:50%;background:rgba(255,255,255,.3)"></div><div class="ss-toggle"></div></div>
          </template>
          <template v-else-if="c.id === 'scanner'">
            <div class="ss-scan">
              <div v-for="(b, i) in BARS" :key="i" class="ss-bar" :style="{ height: b[0] + '%', background: b[1] }"></div>
            </div>
            <div class="ss-btn"></div>
          </template>
          <template v-else-if="c.id === 'account'">
            <div class="ss-line" style="width:40%;background:rgba(255,255,255,.3)"></div>
            <div class="ss-line" style="width:85%;height:12px;background:rgba(255,255,255,.8)"></div>
            <div class="ss-line" style="width:55%;background:rgba(255,255,255,.25)"></div>
            <div class="ss-meter"><div style="width:78%;height:100%;background:#4CD787"></div></div>
            <div class="ss-box"><div class="ss-line" style="width:70%;background:rgba(255,255,255,.45)"></div><div class="ss-line" style="width:50%;background:rgba(255,255,255,.25)"></div></div>
          </template>
          <template v-else-if="c.id === 'activity'">
            <div v-for="(d, i) in ['#4CD787', '#F2B53A', '#4CD787', '#4CD787']" :key="i" class="ss-act">
              <div class="ss-dot" :style="{ background: d }"></div>
              <div class="ss-act-lines"><div class="ss-line ss-thin" style="width:80%;background:rgba(255,255,255,.55)"></div><div class="ss-line ss-thin" style="width:55%;background:rgba(255,255,255,.22)"></div></div>
            </div>
            <div class="ss-outline"></div>
          </template>
          <template v-else-if="c.id === 'broker'">
            <div class="ss-act"><div class="ss-logo"></div><div class="ss-act-lines"><div class="ss-line" style="width:85%;background:rgba(255,255,255,.6)"></div><div class="ss-line ss-thin" style="width:55%;background:rgba(255,255,255,.25)"></div></div></div>
            <div v-for="i in 3" :key="i" class="ss-act" style="padding:4px 0"><div class="ss-dot"></div><div class="ss-line ss-thin" style="width:60%;background:rgba(255,255,255,.35)"></div></div>
            <div class="ss-btn" style="background:rgba(255,255,255,.85)"></div>
          </template>
          <template v-else-if="c.id === 'help'">
            <div class="ss-bub" style="align-self:flex-start;width:72%"></div>
            <div class="ss-bub ss-bub-me" style="align-self:flex-end;width:62%"></div>
            <div class="ss-bub" style="align-self:flex-start;width:80%;height:28px"></div>
            <div class="ss-input"></div>
          </template>
          <template v-else-if="c.id === 'settings'">
            <div v-for="i in 5" :key="i" class="ss-act" style="padding:3px 0"><div class="ss-line ss-thin" style="flex:1;background:rgba(255,255,255,.35)"></div><div class="ss-mini-toggle" :class="{ on: i % 2 }"></div></div>
          </template>
        </div>
        <div class="ss-name"><span class="ms">{{ c.icon }}</span><div>{{ c.name }}</div></div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({ top: { type: Number, default: 404 } })
defineEmits(['close', 'open'])

/* `to` is a route; `action` is handled by the layout (Home look opens over Home). */
const CARDS = [
  { id: 'look',     name: 'Home look',     icon: 'tune',                   action: 'look' },
  { id: 'scanner',  name: 'Chart Scanner', icon: 'crop_free',              to: '/scanner' },
  { id: 'account',  name: 'Account',       icon: 'account_balance_wallet', to: '/trades' },
  { id: 'activity', name: 'Activity',      icon: 'history',                to: '/trades' },
  { id: 'broker',   name: 'Broker',        icon: 'link',                   to: '/terminal' },
  { id: 'help',     name: 'Help',          icon: 'support_agent',          to: '/support' },
  { id: 'settings', name: 'Settings',      icon: 'settings',               to: '/settings' },
]
const BARS = [[30, '#4CD787'], [52, '#4CD787'], [40, '#FF5D5D'], [68, '#4CD787'], [56, '#FF5D5D'], [84, '#4CD787']]
</script>

<style scoped>
.ms { font-family: 'Material Symbols Outlined'; font-weight: 400; font-style: normal; line-height: 1; letter-spacing: normal; font-feature-settings: 'liga'; font-size: 15px; color: #A3A3AE; }
.ss-scrim { position: absolute; inset: 0; z-index: 13; background: linear-gradient(180deg, rgba(4,4,6,.25), rgba(4,4,6,.78) 60%); }
.ss { position: absolute; left: 0; right: 0; z-index: 14; animation: ssUp .34s cubic-bezier(.2,.8,.2,1); }
@keyframes ssUp { from { transform: translateY(48px); opacity: 0 } to { transform: none; opacity: 1 } }
@media (prefers-reduced-motion: reduce) { .ss { animation: none } }
.ss-head { padding: 0 22px; display: flex; align-items: center; justify-content: space-between; }
.ss-label { font-size: 11px; font-weight: 600; letter-spacing: .14em; color: #C9C9D1; }
.ss-hint { font-size: 12px; color: #8E8E99; }
.ss-row { margin-top: 10px; display: flex; gap: 10px; overflow-x: auto; padding: 4px 20px 8px; scrollbar-width: none; touch-action: pan-x; }
.ss-row::-webkit-scrollbar { display: none; }
.ss-card { flex: none; width: 112px; display: flex; flex-direction: column; gap: 8px; cursor: pointer; transition: transform .15s ease; }
.ss-card:active { transform: scale(.97); }
.ss-prev { position: relative; height: 168px; border-radius: 16px; overflow: hidden; background: #101015; border: 1px solid rgba(255,255,255,.1); box-shadow: 0 14px 30px -12px rgba(0,0,0,.8); padding: 12px 10px; display: flex; flex-direction: column; gap: 7px; }
.ss-name { display: flex; align-items: center; gap: 5px; font-size: 12px; font-weight: 500; color: #E6E6EA; white-space: nowrap; }
.ss-grid4 { display: grid; grid-template-columns: 1fr 1fr; gap: 5px; }
.ss-tile { height: 52px; border-radius: 6px; background: #1C1C24; }
.ss-tile-on { border: 1.5px solid var(--a); background: color-mix(in srgb, var(--a) 18%, #15151B); }
.ss-foot { margin-top: auto; display: flex; align-items: center; justify-content: space-between; }
.ss-toggle { width: 20px; height: 11px; border-radius: 6px; background: var(--a); }
.ss-line { height: 5px; border-radius: 3px; }
.ss-thin { height: 4px; border-radius: 2px; }
.ss-scan { flex: 1; border: 1.5px dashed color-mix(in srgb, var(--a) 70%, transparent); border-radius: 8px; display: flex; align-items: flex-end; justify-content: center; gap: 5px; padding: 10px 8px; }
.ss-bar { width: 6px; border-radius: 1px; }
.ss-btn { margin-top: auto; height: 14px; border-radius: 7px; background: var(--a); }
.ss-meter { margin-top: 8px; height: 6px; border-radius: 3px; background: rgba(255,255,255,.1); overflow: hidden; }
.ss-box { margin-top: auto; display: flex; flex-direction: column; gap: 6px; padding: 8px; border-radius: 8px; background: #17171D; }
.ss-act { display: flex; align-items: center; gap: 6px; }
.ss-act-lines { flex: 1; display: flex; flex-direction: column; gap: 3px; }
.ss-dot { width: 8px; height: 8px; border-radius: 50%; flex: none; background: #4CD787; }
.ss-outline { margin-top: auto; height: 14px; border-radius: 7px; border: 1px solid color-mix(in srgb, var(--a) 60%, transparent); }
.ss-logo { width: 26px; height: 26px; border-radius: 7px; background: #1E1E26; border: 1px solid rgba(255,255,255,.12); }
.ss-bub { height: 20px; border-radius: 10px 10px 10px 3px; background: #1E1E26; }
.ss-bub-me { border-radius: 10px 10px 3px 10px; background: color-mix(in srgb, var(--a) 70%, #15151B); }
.ss-input { margin-top: auto; height: 16px; border-radius: 8px; background: #17171D; border: 1px solid rgba(255,255,255,.1); }
.ss-mini-toggle { width: 18px; height: 10px; border-radius: 5px; background: rgba(255,255,255,.18); flex: none; }
.ss-mini-toggle.on { background: var(--a); }
</style>
