<template>
  <Transition name="slide-up">
    <div v-if="visible" class="install-card glass liquid" role="dialog" aria-label="Install NovaHost">
      <button class="close-x" @click="dismiss" aria-label="Close">✕</button>

      <div class="install-icon">
        <img src="/app_logo.png" alt="NovaHost" />
      </div>

      <h3 class="install-title">Install NovaHost iOS</h3>
      <p class="install-sub">Use this as a native app on your iPhone:</p>

      <div class="steps">
        <div class="step">
          <span class="step-num">1</span>
          <span>Tap the <strong>Share</strong> icon <span class="share-icon">⬆</span> in Safari's toolbar</span>
        </div>
        <div class="step">
          <span class="step-num">2</span>
          <span>Scroll and select <strong>"Add to Home Screen"</strong></span>
        </div>
        <div class="step">
          <span class="step-num">3</span>
          <span>Tap <strong>Add</strong> — NovaHost launches standalone!</span>
        </div>
      </div>

      <button class="btn-neon got-it" @click="dismiss">Got it ✓</button>
    </div>
  </Transition>
</template>

<script setup>
import { ref } from 'vue'

const visible = ref(true)
const emit = defineEmits(['dismiss'])

function dismiss() {
  visible.value = false
  emit('dismiss')
}
</script>

<style scoped>
.install-card {
  position: fixed;
  bottom: 110px;
  left: 50%;
  transform: translateX(-50%);
  width: 90%;
  max-width: 380px;
  padding: 24px 20px;
  border-radius: 24px;
  background: rgba(1, 10, 21, 0.4);
  -webkit-backdrop-filter: blur(40px);
          backdrop-filter: blur(40px);
  border: 1px solid rgba(var(--accent-rgb), 0.3);
  z-index: 9999;
  text-align: center;
}

.close-x {
  position: absolute;
  top: 14px;
  right: 16px;
  background: none;
  border: none;
  color: rgba(255,255,255,0.4);
  font-size: 1rem;
  cursor: pointer;
  transition: color 0.2s;
}
.close-x:hover { color: #fff; }

.install-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  overflow: hidden;
  margin: 0 auto 14px;
  border: 1px solid rgba(var(--accent-rgb), 0.3);
  box-shadow: 0 0 20px rgba(var(--accent-rgb), 0.3);
}
.install-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.install-title {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--accent);
  margin-bottom: 6px;
  animation: ember-pulse 2s ease-in-out infinite;
}

.install-sub {
  font-size: 0.8rem;
  color: rgba(232,240,255,0.6);
  margin-bottom: 18px;
}

.steps {
  display: flex;
  flex-direction: column;
  gap: 12px;
  text-align: left;
  margin-bottom: 20px;
}

.step {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 0.85rem;
  color: rgba(232,240,255,0.85);
  line-height: 1.4;
}

.step strong { color: #fff; }

.step-num {
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--accent);
  color: var(--obsidian);
  font-weight: 800;
  font-size: 0.8rem;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 12px rgba(var(--accent-rgb), 0.6);
}

.share-icon {
  display: inline-block;
  background: rgba(var(--accent-rgb), 0.15);
  border: 1px solid rgba(var(--accent-rgb), 0.4);
  border-radius: 6px;
  padding: 1px 5px;
  margin: 0 2px;
  font-size: 0.75rem;
}

.got-it {
  width: 100%;
  padding: 13px;
  border-radius: 14px;
  font-size: 0.9rem;
}

/* Transition */
.slide-up-enter-active, .slide-up-leave-active {
  transition: all 0.4s var(--spring);
}
.slide-up-enter-from, .slide-up-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(30px) scale(0.94);
}
</style>
