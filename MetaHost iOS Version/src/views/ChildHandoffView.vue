<template>
  <div class="handoff-shell">
    <div class="handoff-content">
      <h1 class="title">Child Handoff</h1>
      <p class="subtitle">Device is ready for child usage.</p>
      
      <div class="card">
        <p class="info-text">Handoff complete. The terminal will now boot into active trading mode.</p>
      </div>

      <button class="action-btn" @click="completeHandoff">START TRADING</button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

function completeHandoff() {
  localStorage.setItem('CHILD_HANDOFF_COMPLETED', 'true')
  router.replace('/home')
}
</script>

<style scoped>
.handoff-shell {
  width: 100%;
  min-height: 100dvh;
  background-color: #121212;
  color: #FFF;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.handoff-content {
  width: 100%;
  max-width: 400px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.title {
  font-size: 24px;
  font-weight: 800;
  margin: 0;
}
.subtitle {
  color: rgba(255, 255, 255, 0.6);
  font-size: 14px;
}
.card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 20px;
  border-radius: 16px;
}
.info-text {
  font-size: 14px;
  color: #CCC;
}
.action-btn {
  background-color: var(--accent);
  color: #FFF;
  border: none;
  padding: 16px;
  border-radius: 12px;
  font-weight: 700;
  cursor: pointer;
}
.action-btn:active {
  transform: scale(0.98);
}
</style>
