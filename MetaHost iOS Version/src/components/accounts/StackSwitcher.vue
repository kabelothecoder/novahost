<template>
  <!--
    The wallet stack. Cards overlap, each showing only its header row; the one
    you tap is pulled out, its details open beneath it, and the rest drop into
    a pile below. Everything is absolutely positioned inside one stage so the
    move between the two arrangements is a transition on top/transform, not a
    re-layout, and the stage height is computed to match.
  -->
  <div class="stk-root" @keydown.esc="sel = null">
  <div class="stk" :style="{ height: stageH + 'px' }">
    <div
      v-for="(it, i) in list"
      :key="it.id"
      class="stk-item"
      :class="{ 'is-pulled': it.id === sel }"
      :style="place(it, i)"
      @click="tap(it)"
    >
      <AccountCard
        v-if="it.kind === 'acc'"
        :account="it"
        :active="it.id === activeId"
        :included="it.id === includedId"
        :running="running"
        :robot-name="robotName"
        :hint="it.id === sel ? '' : it.id === activeId ? 'Home and robot controls apply here' : 'Tap for details'"
      />
      <SlotCard v-else-if="it.kind === 'slot'" :item="it" @act="$emit('slot-act', it)" />
      <AddCard v-else :count="count" @add="$emit('add')" />
    </div>

    <div v-if="selected" class="stk-detail" :style="{ top: DETAIL_TOP + 'px' }">
      <AccountDetail
        :account="selected"
        :active="selected.id === activeId"
        :included="selected.id === includedId"
        :running="running"
        :busy="busy"
        :robot-name="robotName"
        @make="$emit('make', selected.id)"
        @delink="$emit('delink', selected.id)"
      />
    </div>
  </div>
  <!-- Footer content (the billing summary) only belongs under the resting stack. -->
  <slot v-if="selIndex < 0" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
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

const STEP = 74          // visible header of an overlapped card
const H_CARD = 196
const H_ADD = 124
const DETAIL_TOP = H_CARD + 12
const DETAIL_H = 272    // tallest case: billing line wraps to two on a 390px phone
const PILE_TOP = DETAIL_TOP + DETAIL_H + 16
const PILE_STEP = 11
const PILE_H = 64

const sel = ref(null)
const count = computed(() => props.items.filter((i) => i.kind === 'acc').length)
const list = computed(() => [...props.items, { id: 'add', kind: 'add' }])
const selIndex = computed(() => list.value.findIndex((i) => i.id === sel.value))
const selected = computed(() => {
  const it = list.value[selIndex.value]
  return it && it.kind === 'acc' ? it : null
})

/* A delink or switch can remove the selected card from under us. */
watch(list, () => { if (sel.value && selIndex.value < 0) sel.value = null })

const heightOf = (it) => (it.kind === 'add' && count.value > 0 ? H_ADD : H_CARD)

/* How far the NEXT card sits below this one while resting. Accounts overlap
   down to their header row; a slot carries a message and a button the user has
   to reach, so it keeps its full height and nothing covers it. */
const stepOf = (it) => (it.kind === 'slot' ? H_CARD + 12 : STEP)

const tops = computed(() => {
  let y = 0
  return list.value.map((it) => { const t = y; y += stepOf(it); return t })
})

const stageH = computed(() => {
  const n = list.value.length
  if (selIndex.value < 0) return tops.value[n - 1] + heightOf(list.value[n - 1])
  return PILE_TOP + Math.max(0, n - 2) * PILE_STEP + PILE_H
})

function place(it, i) {
  const base = { height: heightOf(it) + 'px' }
  if (selIndex.value < 0) {
    return { ...base, top: tops.value[i] + 'px', zIndex: i + 1, transform: 'none' }
  }
  if (i === selIndex.value) return { ...base, top: '0px', zIndex: 30, transform: 'none' }
  /* In the pile every card is cut down to its header strip. Left at full
     height, the last one in the pile would sit fully exposed, and the Add card
     in particular reads as a second, competing call to action. */
  const j = i < selIndex.value ? i : i - 1
  return { height: PILE_H + 'px', top: PILE_TOP + j * PILE_STEP + 'px', zIndex: j + 1, transform: `scale(${(0.88 + j * 0.02).toFixed(3)})` }
}

function tap(it) {
  if (it.kind === 'slot') return                 // its own button acts
  if (sel.value) {
    sel.value = it.id === sel.value || it.kind !== 'acc' ? null : it.id
    return
  }
  if (it.kind === 'acc') sel.value = it.id
}
</script>

<style scoped>
.stk { position: relative; width: 100%; transition: height 500ms var(--cc-ease); }
.stk-item {
  position: absolute; left: 0; right: 0;
  transform-origin: 50% 0;
  transition: top 550ms cubic-bezier(.2, .8, .2, 1), transform 550ms cubic-bezier(.2, .8, .2, 1), height 450ms cubic-bezier(.2, .8, .2, 1);
  cursor: pointer;
}
.stk-detail { position: absolute; left: 0; right: 0; z-index: 31; animation: stk-in 450ms cubic-bezier(.2, .8, .2, 1) both; }
@keyframes stk-in { from { opacity: 0; transform: translateY(10px) } to { opacity: 1; transform: none } }
@media (prefers-reduced-motion: reduce) {
  .stk, .stk-item { transition: none }
  .stk-detail { animation: none }
}
</style>
