<template>
  <!-- Every symbol passed. Tap anywhere to dismiss; it also leaves on its own. -->
  <Teleport to="body">
    <div class="cel" role="status" @click="$emit('close')">
      <div class="cel-art" aria-hidden="true">
        <span class="cel-bloom"></span>
        <i v-for="n in 3" :key="'r' + n" class="cel-ring" :style="{ animationDelay: 0.3 + (n - 1) * 0.35 + 's' }"></i>
        <i v-for="s in sparks" :key="s.k" class="cel-spark" :class="{ w: s.w }" :style="s.style"></i>
        <span class="cel-ball">
          <svg width="70" height="70" viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke="#06210F" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </span>
      </div>
      <div class="cel-copy">
        <h2>{{ broker }} is ready</h2>
        <p>Your robot can trade {{ count }} symbol{{ count === 1 ? '' : 's' }}{{ tag ? ` on ${tag}` : '' }}.</p>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'

defineProps({ broker: String, count: Number, tag: String })
const emit = defineEmits(['close'])

const sparks = Array.from({ length: 16 }, (_, i) => {
  const ang = (i / 16) * Math.PI * 2, d = 120 + (i % 3) * 34
  return { k: i, w: i % 4 === 0, style: { '--dx': Math.cos(ang) * d + 'px', '--dy': Math.sin(ang) * d + 'px', animationDelay: 0.35 + (i % 4) * 0.05 + 's', width: (i % 2 ? 6 : 4) + 'px', height: (i % 2 ? 6 : 4) + 'px' } }
})

let t = null
onMounted(() => { t = setTimeout(() => emit('close'), 3200) })
onUnmounted(() => clearTimeout(t))
</script>

<style scoped>
.cel {
  position: fixed; inset: 0; z-index: 420; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 30px; cursor: pointer;
  background: radial-gradient(60% 40% at 50% 44%, rgba(48, 209, 88, 0.32), rgba(11, 11, 14, 0.94) 70%);
  -webkit-backdrop-filter: blur(10px); backdrop-filter: blur(10px); animation: cel-fade 350ms ease both; color: #F4F4F7;
}
.cel-art { position: relative; width: 300px; height: 300px; }
.cel-bloom { position: absolute; left: 50%; top: 50%; width: 260px; height: 260px; border-radius: 50%; background: radial-gradient(circle, rgba(48, 209, 88, 0.55), transparent 65%); animation: cel-bloom 2.4s ease-in-out infinite; }
.cel-ring { position: absolute; left: 50%; top: 50%; width: 140px; height: 140px; border-radius: 50%; border: 2px solid rgba(48, 209, 88, 0.7); animation: cel-ring 1.8s ease-out both; }
.cel-spark { position: absolute; left: 50%; top: 50%; border-radius: 3px; background: #5BE37D; box-shadow: 0 0 10px #30D158; animation: cel-spark 1.4s cubic-bezier(.2, .7, .2, 1) both; }
.cel-spark.w { background: #fff; }
.cel-ball {
  position: absolute; left: 80px; top: 80px; width: 140px; height: 140px; border-radius: 50%; display: grid; place-items: center;
  background: radial-gradient(circle at 35% 30%, #7CF29A, #30D158 55%, #1C9E3F);
  box-shadow: 0 0 60px rgba(48, 209, 88, 0.8), inset 0 2px 0 rgba(255, 255, 255, 0.4);
  animation: cel-pop 600ms cubic-bezier(.2, .9, .3, 1.3) 100ms both;
}
.cel-ball path { stroke-dasharray: 24; stroke-dashoffset: 24; animation: cel-draw 500ms ease-out 550ms forwards; }
.cel-copy { display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 0 32px; text-align: center; animation: cel-up 500ms ease 800ms both; }
.cel-copy h2 { margin: 0; font-size: 38px; line-height: 42px; font-weight: 650; letter-spacing: -0.03em; }
.cel-copy p { margin: 0; font-size: 16px; line-height: 23px; color: #C9C9D1; }

@keyframes cel-fade { from { opacity: 0 } to { opacity: 1 } }
@keyframes cel-bloom { 0%, 100% { opacity: .6; transform: translate(-50%, -50%) scale(1) } 50% { opacity: 1; transform: translate(-50%, -50%) scale(1.12) } }
@keyframes cel-ring { 0% { transform: translate(-50%, -50%) scale(.6); opacity: .9 } 100% { transform: translate(-50%, -50%) scale(3.2); opacity: 0 } }
@keyframes cel-spark { 0% { transform: translate(-50%, -50%); opacity: 1 } 100% { transform: translate(calc(-50% + var(--dx)), calc(-50% + var(--dy))) scale(.3); opacity: 0 } }
@keyframes cel-pop { 0% { transform: scale(.2); opacity: 0 } 60% { transform: scale(1.08); opacity: 1 } 100% { transform: scale(1) } }
@keyframes cel-draw { to { stroke-dashoffset: 0 } }
@keyframes cel-up { 0% { opacity: 0; transform: translateY(14px) } 100% { opacity: 1; transform: none } }
@media (prefers-reduced-motion: reduce) {
  .cel, .cel-bloom, .cel-ball, .cel-copy { animation: none }
  .cel-ring, .cel-spark { display: none }
  .cel-ball path { animation: none; stroke-dashoffset: 0 }
}
</style>
