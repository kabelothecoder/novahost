<template>
  <div 
    class="floating-pulse-hud"
    :class="{ expanded: isExpanded, shaking: isShaking }"
  >
    <div 
      class="hud-inner glass"
      :class="['status-' + metaStatus, { expanded: isExpanded }]"
      @click="expandHud"
    >
      <!-- Pulse Ring (Idle) -->
      <div v-if="!isExpanded" class="pulse-glow-ring" />

      <!-- IDLE STATE -->
      <div v-if="!isExpanded" class="hud-idle">
        <img src="/robot_avatar.png" class="idle-avatar" alt="Bot Icon" />
      </div>

      <!-- EXPANDED STATE -->
      <div v-else class="hud-active" @click.stop>
        
        <!-- Top: Status Pill -->
        <div class="hud-top">
          <div class="status-pill glass">
            <span class="status-dot" :class="metaStatus"></span>
            <span class="status-txt">{{ metaStatus === 'online' ? 'ONLINE' : 'OFFLINE' }}</span>
          </div>
        </div>

        <!-- Middle: Full width Image -->
        <div class="hud-middle">
          <img src="/robot_avatar.png" class="active-avatar" alt="Hero" />
        </div>

        <!-- Bottom: Info & Actions -->
        <div class="hud-bottom">
          <h3 class="bot-name">GhostRider X</h3>
          <span class="bot-sub">Powered by NovaHost</span>
          
          <div class="hud-actions">
            <!-- Simulated Stop button changes status -->
            <button class="action-btn stop-btn" @click="toggleStatus">STOP</button>
            <button class="action-btn view-btn glass" @click="simulateTrade">SIM TRADE</button>
          </div>
        </div>

        <!-- Close target area to collapse -->
        <button class="close-hitbox" @click="isExpanded = false">
           <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.5)" stroke-width="2"><path d="M18 6L6 18M6 6l12 12"/></svg>
        </button>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const isExpanded = ref(false)
const isShaking = ref(false)
const metaStatus = ref('online') // 'online' | 'offline'

function expandHud() {
  if (!isExpanded.value) {
    isExpanded.value = true
  }
}

// Demo features for logically syncing events:
function toggleStatus() {
  metaStatus.value = metaStatus.value === 'online' ? 'offline' : 'online'
  isExpanded.value = false // collapse to show the grey pulse
}

function simulateTrade() {
  isExpanded.value = false
  // Wait to collapse
  setTimeout(() => {
    // Trigger shake
    isShaking.value = true
    setTimeout(() => {
      // Auto expand to show details
      isShaking.value = false
      isExpanded.value = true
    }, 600)
  }, 400)
}
</script>

<style scoped>
/* ── HUD WRAPPER (Fixed Positioning) ── */
.floating-pulse-hud {
  position: fixed;
  top: 15%;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
}

/* ── HUD INNER MORPHING CONTAINER ── */
.hud-inner {
  position: relative;
  width: 60px;
  height: 60px;
  border-radius: 30px; /* circle */
  display: flex;
  flex-direction: column;
  overflow: hidden;
  cursor: pointer;
  -webkit-backdrop-filter: blur(40px) saturate(200%);
  backdrop-filter: blur(40px) saturate(200%);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255,255,255,0.1);
  box-shadow: 0 10px 30px rgba(0,0,0,0.5);
  
  /* SPRING PHYSICS TRANSITION */
  transition: all 0.6s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.hud-inner.expanded {
  width: 320px;
  height: 400px;
  border-radius: 24px;
  cursor: default;
  background: rgba(1, 10, 21, 0.6); /* Slightly more smoked glass when open */
}

/* ── SHAKE ANIMATION ON DATA REF ── */
.shaking .hud-inner {
  animation: hud-shake 0.4s cubic-bezier(0.36, 0.07, 0.19, 0.97) infinite;
}
@keyframes hud-shake {
  10%, 90% { transform: translate3d(-2px, 0, 0); }
  20%, 80% { transform: translate3d(4px, 0, 0); }
  30%, 50%, 70% { transform: translate3d(-6px, 0, 0); }
  40%, 60% { transform: translate3d(6px, 0, 0); }
}

/* ── PULSE GLOW (IDLE ONLY) ── */
.pulse-glow-ring {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  border-radius: 50%;
  pointer-events: none;
  animation: pulse-glow-hud 2s infinite;
}
@keyframes pulse-glow-hud {
  0% { box-shadow: 0 0 0 0px var(--hud-clr, rgba(220,20,60, 0.6)); }
  100% { box-shadow: 0 0 0 15px transparent; }
}

.hud-inner.status-online  { --hud-clr: var(--accent, #FF003C); }
.hud-inner.status-offline { --hud-clr: rgba(120, 120, 120, 0.6); }

/* ── CONTENT LAYOUTS ── */

/* IDLE: Bubble format */
.hud-idle {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.idle-avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* ACTIVE: Expanded Card Format */
.hud-active {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
  /* fade in content */
  animation: fade-in 0.3s ease 0.2s both;
}

@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* Top Pill */
.hud-top {
  position: absolute;
  top: 14px;
  left: 14px;
  z-index: 10;
}
.status-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 20px;
  border: 1px solid rgba(255,255,255,0.1) !important;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  box-shadow: 0 0 8px currentColor;
}
.status-dot.online { background: #00FF87; color: #00FF87; }
.status-dot.offline { background: #888; color: #888; }
.status-txt {
  font-family: var(--font-mono);
  font-size: 0.6rem;
  letter-spacing: 0.1em;
  font-weight: 700;
  color: #fff;
}

.close-hitbox {
  position: absolute;
  top: 14px;
  right: 14px;
  z-index: 10;
  background: rgba(0,0,0,0.4);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 50%;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  -webkit-backdrop-filter: blur(20px);
  backdrop-filter: blur(20px);
}

/* Middle: Full width image */
.hud-middle {
  width: 100%;
  height: 200px;
  position: relative;
  flex-shrink: 0;
}
.hud-middle::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(to bottom, rgba(1,10,21,0.2) 0%, rgba(1,10,21,0.9) 100%);
}
.active-avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* Bottom: Info & Controls */
.hud-bottom {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 20px 20px;
}
.bot-name {
  font-size: 1.4rem;
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.02em;
}
.bot-sub {
  font-family: var(--font-mono);
  font-size: 0.55rem;
  color: rgba(255,255,255,0.4);
  letter-spacing: 0.2em;
  text-transform: uppercase;
  margin-top: 4px;
  margin-bottom: auto;
}

.hud-actions {
  display: flex;
  width: 100%;
  gap: 12px;
  margin-top: 12px;
}

.action-btn {
  flex: 1;
  height: 44px;
  border-radius: 12px;
  font-family: var(--font-mono);
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border: 1px solid rgba(255,255,255,0.1);
  transition: all 0.2s;
}
.action-btn:active { transform: scale(0.95); }

/* Red Tint Glass */
.stop-btn {
  background: rgba(255, 61, 90, 0.15);
  border-color: rgba(255, 61, 90, 0.3);
  color: #FF3D5A;
}

/* Clear Glass */
.view-btn {
  background: rgba(255, 255, 255, 0.05);
  color: #fff;
}
</style>
