<template>
  <div class="settings-view">

    <header class="screen-header">
      <h2 class="screen-title">Vibe Engine</h2>
      <span class="screen-sub">Personalise your Nova Edge experience</span>
    </header>

    <div class="settings-scroll">

      <!-- ── LIVE PREVIEW ── -->
      <div class="preview-card" :style="previewCardStyle">
        <span class="preview-tag">LIVE PREVIEW</span>
        <div class="preview-inner">
          <div class="preview-avatar">
            <img src="/robot_avatar.png" alt="Preview" />
            <div class="preview-avatar-ring"></div>
          </div>
          <div class="preview-info">
            <span class="preview-name" :style="{ color: accent.current.hex }">Nova Edge EA v6</span>
            <span class="preview-sub">{{ accent.current.name }} · AI Online</span>
          </div>
          <div class="preview-pill" :style="{ borderColor: accent.current.hex + '60', color: accent.current.hex }">
            <span class="preview-dot" :style="{ background: accent.current.hex }"></span>
            LIVE
          </div>
        </div>
      </div>

      <!-- ── ACCENT COLOR ── -->
      <div class="section-card">
        <div class="section-head">
          <span class="section-label">ACCENT COLOR</span>
          <span class="section-val" :style="{ color: accent.current.hex }">{{ accent.current.name }}</span>
        </div>
        <div class="swatch-grid">
          <button
            v-for="sw in SWATCHES"
            :key="sw.name"
            class="swatch-btn"
            :class="{ active: accent.current.name === sw.name }"
            :style="{ background: sw.hex }"
            :title="sw.name"
            @click="setAccent(sw)"
          >
            <svg v-if="accent.current.name === sw.name" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#000" stroke-width="3.5" stroke-linecap="round"><polyline points="20 6 9 17 4 12"/></svg>
          </button>
        </div>
        <!-- Color name labels -->
        <div class="swatch-names">
          <span
            v-for="sw in SWATCHES"
            :key="sw.name + '_label'"
            class="swatch-name-label"
            :style="accent.current.name === sw.name ? { color: sw.hex } : {}"
          >{{ sw.name }}</span>
        </div>
      </div>

      <!-- ── BACKGROUND LOOP ── -->
      <div class="section-card">
        <div class="section-head">
          <span class="section-label">BACKGROUND LOOP</span>
          <span class="section-val">{{ accent.videoBg.name }}</span>
        </div>
        <div class="bg-picker-grid">
          <button
            v-for="(bg, i) in VIDEO_BG_OPTIONS"
            :key="bg.id"
            class="bg-tile"
            :class="{ active: accent.videoBg.id === bg.id }"
            @click="accent.videoBg = bg"
          >
            <div class="bg-swatch" :style="bgTileStyle(i)" />
            <span class="bg-name">{{ bg.name }}</span>
          </button>
        </div>
      </div>

      <!-- ── DISPLAY SETTINGS ── -->
      <div class="section-card">
        <div class="section-head"><span class="section-label">DISPLAY</span></div>
        <div class="toggle-list">
          <div v-for="tog in toggles" :key="tog.id" class="toggle-row">
            <div class="toggle-info">
              <span class="toggle-name">{{ tog.name }}</span>
              <span class="toggle-desc">{{ tog.desc }}</span>
            </div>
            <button
              class="toggle-pill"
              :class="{ on: tog.value }"
              @click="tog.value = !tog.value"
              :style="tog.value ? { background: accent.current.hex, boxShadow: `0 0 16px ${accent.current.hex}60` } : {}"
            >
              <span class="pill-knob" />
            </button>
          </div>
        </div>
      </div>

      <!-- ── API KEYS ── -->
      <div class="section-card">
        <div class="section-head"><span class="section-label">API CREDENTIALS</span></div>
        <div class="api-fields">
          <div class="form-field">
            <label class="form-label">METAAPI TOKEN</label>
            <input type="password" class="field-glass" v-model="apiKeys.metaapi" placeholder="sk-..." @blur="saveKey('METAAPI_TOKEN', apiKeys.metaapi)" />
          </div>
          <div class="form-field">
            <label class="form-label">METAAPI ACCOUNT ID</label>
            <input type="text" class="field-glass" v-model="apiKeys.accountId" placeholder="Account ID..." @blur="saveKey('METAAPI_ACCOUNT_ID', apiKeys.accountId)" />
          </div>
          <div class="form-field">
            <label class="form-label">ELEVENLABS API KEY</label>
            <input type="password" class="field-glass" v-model="apiKeys.elevenlabs" placeholder="....." @blur="saveKey('ELEVENLABS_API_KEY', apiKeys.elevenlabs)" />
          </div>
        </div>
      </div>

      <!-- App version footer -->
      <div class="app-footer">
        <span class="app-version">Nova Edge EA v6 · Build 42</span>
        <span class="app-copy">© 2025 monga_za</span>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { accent, SWATCHES, VIDEO_BG_OPTIONS } from '../stores/accent.js'

function setAccent(sw) { accent.current = sw }

const toggles = ref([
  { id: 'haptic',        name: 'Haptic Feedback',  desc: 'Vibrate on actions',      value: true  },
  { id: 'reduce',        name: 'Reduce Motion',     desc: 'Disable some animations', value: false },
  { id: 'darkbg',        name: 'Ultra Dark Mode',   desc: 'Maximum contrast',        value: true  },
  { id: 'notifications', name: 'Notifications',     desc: 'EA alerts and signals',   value: true  },
])

const apiKeys = ref({
  metaapi:    localStorage.getItem('METAAPI_TOKEN') || '',
  accountId:  localStorage.getItem('METAAPI_ACCOUNT_ID') || '',
  elevenlabs: localStorage.getItem('ELEVENLABS_API_KEY') || '',
})
function saveKey(key, val) { if (val) localStorage.setItem(key, val) }

const previewCardStyle = computed(() => ({
  borderColor: accent.current.hex + '35',
  boxShadow: `0 0 28px ${accent.current.hex}15`,
}))

function bgTileStyle(i) {
  const hues = [195, 265, 142, 45, 350, 120, 300, 30]
  const h = hues[i % hues.length]
  return { background: `radial-gradient(circle at 40% 40%, hsla(${h},85%,65%,0.6), hsla(${h+40},70%,40%,0.2), #010A15)` }
}
</script>

<style scoped>
.settings-view {
  width: 100%;
  min-height: 100dvh;
  display: flex; flex-direction: column;
  background: transparent;
}

.screen-header {
  padding: calc(env(safe-area-inset-top, 16px) + 16px) 20px 0;
}
.screen-title { font-size: 1.3rem; font-weight: 800; color: #E8F0FF; margin: 0; }
.screen-sub {
  font-family: 'Courier New', monospace; font-size: 0.65rem;
  color: rgba(var(--accent-rgb), 0.5); letter-spacing: 0.1em;
  display: block; margin-top: 2px;
}

.settings-scroll {
  flex: 1; overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
  padding: 16px 16px 130px;
  display: flex; flex-direction: column; gap: 14px;
}

/* ── Preview ── */
.preview-card {
  border-radius: 18px;
  background: rgba(255,255,255,0.03);
  border: 1px solid transparent;
  padding: 16px;
  transition: all 0.4s ease;
}
.preview-tag {
  font-family: 'Courier New', monospace;
  font-size: 0.56rem; letter-spacing: 0.2em;
  color: rgba(var(--accent-rgb), 0.45);
  display: block; margin-bottom: 12px;
}
.preview-inner {
  display: flex; align-items: center; gap: 14px;
}
.preview-avatar {
  position: relative; width: 50px; height: 50px;
  border-radius: 50%; overflow: visible; flex-shrink: 0;
}
.preview-avatar img {
  width: 100%; height: 100%;
  border-radius: 50%; object-fit: cover;
  border: 2px solid rgba(var(--accent-rgb), 0.5);
}
.preview-avatar-ring {
  position: absolute; inset: -5px;
  border-radius: 50%;
  border: 1.5px solid rgba(var(--accent-rgb), 0.25);
  pointer-events: none;
}
.preview-info { flex: 1; }
.preview-name { display: block; font-size: 0.92rem; font-weight: 800; transition: color 0.3s ease; }
.preview-sub { display: block; font-family: 'Courier New', monospace; font-size: 0.65rem; color: rgba(232,240,255,0.35); margin-top: 2px; }
.preview-pill {
  padding: 5px 10px;
  border-radius: 100px;
  border: 1px solid;
  font-family: 'Courier New', monospace;
  font-size: 0.6rem; font-weight: 800; letter-spacing: 0.1em;
  display: flex; align-items: center; gap: 5px;
  transition: all 0.3s ease;
}
.preview-dot { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; }

/* Sections */
.section-card {
  border-radius: 18px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.07);
  padding: 18px;
}
.section-head {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 16px;
}
.section-label {
  font-family: 'Courier New', monospace;
  font-size: 0.6rem; letter-spacing: 0.2em;
  color: rgba(var(--accent-rgb), 0.45);
}
.section-val {
  font-family: 'Courier New', monospace;
  font-size: 0.75rem; color: rgba(232,240,255,0.55);
  transition: color 0.3s ease;
}

/* Swatches */
.swatch-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 8px;
}
.swatch-btn {
  aspect-ratio: 1;
  border-radius: 50%;
  border: 2.5px solid transparent;
  cursor: pointer;
  position: relative; overflow: hidden;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.swatch-btn.active {
  border-color: rgba(255,255,255,0.85);
  transform: scale(1.18);
  box-shadow: 0 0 18px currentColor, 0 4px 12px rgba(0,0,0,0.4);
}

.swatch-names {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 8px;
  margin-top: 6px;
}
.swatch-name-label {
  font-family: 'Courier New', monospace;
  font-size: 0.42rem; text-align: center;
  color: rgba(255,255,255,0.3);
  transition: color 0.25s ease;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* BG Picker */
.bg-picker-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}
.bg-tile {
  display: flex; flex-direction: column; align-items: center; gap: 5px;
  border: 1.5px solid transparent; border-radius: 12px;
  padding: 5px; cursor: pointer; background: transparent;
  transition: all 0.25s ease;
}
.bg-tile.active {
  border-color: var(--accent);
  box-shadow: 0 0 12px rgba(var(--accent-rgb), 0.4);
}
.bg-swatch {
  width: 100%; aspect-ratio: 1; border-radius: 8px;
}
.bg-name {
  font-family: 'Courier New', monospace;
  font-size: 0.52rem; color: rgba(232,240,255,0.4); text-align: center; line-height: 1.2;
}
.bg-tile.active .bg-name { color: var(--accent); }

/* Toggles */
.toggle-list { display: flex; flex-direction: column; gap: 14px; }
.toggle-row { display: flex; align-items: center; justify-content: space-between; }
.toggle-info { display: flex; flex-direction: column; }
.toggle-name { font-size: 0.9rem; color: #E8F0FF; font-weight: 500; }
.toggle-desc { font-size: 0.68rem; color: rgba(232,240,255,0.33); margin-top: 1px; }
.toggle-pill {
  width: 46px; height: 26px; border-radius: 100px;
  background: rgba(255,255,255,0.1);
  border: none; cursor: pointer; position: relative;
  transition: all 0.3s ease; flex-shrink: 0;
}
.pill-knob {
  position: absolute; top: 3px; left: 3px;
  width: 20px; height: 20px; border-radius: 50%;
  background: rgba(255,255,255,0.7);
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.toggle-pill.on .pill-knob { transform: translateX(20px); background: rgba(0,0,0,0.85); }

/* API Fields */
.api-fields { display: flex; flex-direction: column; gap: 12px; }
.form-field { display: flex; flex-direction: column; gap: 6px; }
.form-label {
  font-family: 'Courier New', monospace;
  font-size: 0.58rem; letter-spacing: 0.16em;
  color: rgba(var(--accent-rgb), 0.5);
}
.field-glass {
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.09);
  border-radius: 11px; color: #E8F0FF;
  padding: 11px 14px;
  font-size: 0.85rem;
  font-family: 'Courier New', monospace;
  width: 100%; outline: none;
  transition: border-color 0.2s ease;
}
.field-glass::placeholder { color: rgba(255,255,255,0.2); }
.field-glass:focus { border-color: rgba(var(--accent-rgb), 0.5); }

/* Footer */
.app-footer {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 8px;
}
.app-version {
  font-family: 'Courier New', monospace;
  font-size: 0.62rem; color: rgba(255,255,255,0.2);
  letter-spacing: 0.1em;
}
.app-copy {
  font-family: 'Courier New', monospace;
  font-size: 0.55rem; color: rgba(255,255,255,0.15);
}
</style>
