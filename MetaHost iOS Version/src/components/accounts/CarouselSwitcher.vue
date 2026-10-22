<template>
  <!--
    Swipe sideways through the accounts; the centred card is the one being
    looked at, with its details and Make active underneath. The Add slot is the
    last card.

    Native scroll-snap rather than the design's hand-rolled pointer drag: iOS
    gives momentum, rubber-banding and accessibility for free, and a pointer
    handler fights the vertical page scroll that wraps this. The "which card is
    centred" question is answered from scroll position instead.
  -->
  <div class="car">
    <div ref="track" class="car-track" @scroll.passive="onScroll">
      <div
        v-for="(it, i) in list"
        :key="it.id"
        class="car-item"
        :class="{ 'is-on': i === idx }"
        :style="{ height: H + 'px' }"
        @click="tap(it, i)"
      >
        <AccountCard
          v-if="it.kind === 'acc'"
          :account="it"
          :active="it.id === activeId"
          :included="it.id === includedId"
          :running="running"
          :robot-name="robotName"
          :hint="it.id === activeId ? 'Home and robot controls apply here' : 'Make it active below'"
        />
        <SlotCard v-else-if="it.kind === 'slot'" :item="it" @act="$emit('slot-act', it)" />
        <AddCard v-else :count="count" @add="$emit('add')" />
      </div>
    </div>

    <div class="car-dots" aria-hidden="true">
      <span v-for="(it, i) in list" :key="it.id" class="car-dot" :class="{ 'is-on': i === idx }"></span>
    </div>

    <div v-if="current && current.kind === 'acc'" :key="current.id" class="car-detail">
      <AccountDetail
        :account="current"
        :active="current.id === activeId"
        :included="current.id === includedId"
        :running="running"
        :busy="busy"
        :robot-name="robotName"
        @make="$emit('make', current.id)"
        @delink="$emit('delink', current.id)"
      />
    </div>

    <slot />
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import AccountCard from './AccountCard.vue'
import SlotCard from './SlotCard.vue'
import AddCard from './AddCard.vue'
import AccountDetail from './AccountDetail.vue'

const props = defineProps({
  items: { type: Array, required: true },
  activeId: { type: String, default: null },
  includedId: { type: String, default: null },
  running: Boolean,
  busy: Boolean,
  robotName: { type: String, default: 'Your robot' },
})
defineEmits(['add', 'make', 'delink', 'slot-act'])

const H = 196
const track = ref(null)
const idx = ref(0)
const count = computed(() => props.items.filter((i) => i.kind === 'acc').length)
const list = computed(() => [...props.items, { id: 'add', kind: 'add' }])
const current = computed(() => list.value[idx.value] || null)

let raf = 0
function onScroll() {
  if (raf) return
  raf = requestAnimationFrame(() => {
    raf = 0
    const el = track.value
    if (!el) return
    const mid = el.scrollLeft + el.clientWidth / 2
    let best = 0, bestD = Infinity
    ;[...el.children].forEach((c, i) => {
      const d = Math.abs(c.offsetLeft + c.offsetWidth / 2 - mid)
      if (d < bestD) { bestD = d; best = i }
    })
    idx.value = best
  })
}

function tap(it, i) {
  if (i !== idx.value) {
    track.value?.children[i]?.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' })
  }
}

/* A delink removes a card from under the scroll position; keep the index in
   range and re-centre so the details never describe a card that is gone. */
watch(() => list.value.length, async () => {
  if (idx.value > list.value.length - 1) idx.value = list.value.length - 1
  await nextTick()
  track.value?.children[idx.value]?.scrollIntoView({ inline: 'center', block: 'nearest' })
})
</script>

<style scoped>
.car { display: flex; flex-direction: column; }
/* Bleeds to the screen edges so neighbouring cards peek in. */
.car-track {
  display: flex; gap: 12px;
  /* Percent padding resolves against the PARENT's width (which the -22px
     margin does not widen), so centre against the viewport instead. */
  --car-w: min(322px, calc(100vw - 68px));
  margin: 0 -22px; padding: 0 calc((100vw - var(--car-w)) / 2);
  overflow-x: auto; overflow-y: hidden;
  scroll-snap-type: x mandatory; -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
  padding-bottom: 6px;
}
.car-track::-webkit-scrollbar { display: none; }
.car-item {
  flex: 0 0 var(--car-w); scroll-snap-align: center;
  transform: scale(0.92); opacity: 0.55;
  transition: transform 350ms cubic-bezier(.2, .8, .2, 1), opacity 300ms ease;
  cursor: pointer;
}
.car-item.is-on { transform: none; opacity: 1; }

.car-dots { display: flex; justify-content: center; gap: 6px; margin: 14px 0 16px; }
.car-dot { height: 6px; width: 6px; border-radius: 3px; background: rgba(255, 255, 255, 0.28); transition: width 350ms ease, background 350ms ease; }
.car-dot.is-on { width: 18px; background: var(--cc-ink); }

.car-detail { animation: car-in 350ms cubic-bezier(.2, .8, .2, 1) both; }
@keyframes car-in { from { opacity: 0; transform: translateY(8px) } to { opacity: 1; transform: none } }
@media (prefers-reduced-motion: reduce) {
  .car-item, .car-dot { transition: none }
  .car-detail { animation: none }
}
</style>
