<template>
  <!--
    The active account is the hero card; paused ones collapse into rows below
    and a tap on a row switches to it. Tap the hero to see billing and delink.

    Ordinary document flow, not the stack's absolute positioning: every piece
    here is a different height and nothing overlaps, so there is nothing for a
    coordinate system to buy.
  -->
  <div class="lst">
    <div v-if="hero" class="lst-hero" @click="open = !open">
      <div class="lst-hero-card">
        <AccountCard
          :account="hero"
          active
          :included="hero.id === includedId"
          :running="running"
          :robot-name="robotName"
          :hint="open ? '' : 'Tap for billing and details'"
        />
      </div>
      <div v-if="open" class="lst-detail" @click.stop>
        <AccountDetail
          :account="hero"
          active
          :included="hero.id === includedId"
          :running="running"
          :busy="busy"
          :robot-name="robotName"
        />
      </div>
    </div>

    <template v-if="others.length">
      <div class="cc-label lst-label">Other accounts · paused</div>
      <div class="lst-rows">
        <template v-for="it in others" :key="it.id">
          <AccountRow v-if="it.kind === 'acc'" :account="it" :included="it.id === includedId" @click="$emit('make', it.id)" />
          <div v-else class="lst-slot"><SlotCard :item="it" @act="$emit('slot-act', it)" /></div>
        </template>
      </div>
    </template>

    <div class="lst-add" :style="{ height: (count === 0 ? 196 : 124) + 'px' }">
      <AddCard :count="count" @add="$emit('add')" />
    </div>

    <slot />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import AccountCard from './AccountCard.vue'
import AccountRow from './AccountRow.vue'
import AccountDetail from './AccountDetail.vue'
import SlotCard from './SlotCard.vue'
import AddCard from './AddCard.vue'

const props = defineProps({
  items: { type: Array, required: true },
  activeId: { type: String, default: null },
  includedId: { type: String, default: null },
  running: Boolean,
  busy: Boolean,
  robotName: { type: String, default: 'Your robot' },
})
defineEmits(['add', 'make', 'delink', 'slot-act'])

const open = ref(false)
const count = computed(() => props.items.filter((i) => i.kind === 'acc').length)
const hero = computed(() => props.items.find((i) => i.kind === 'acc' && i.id === props.activeId) || null)
const others = computed(() => props.items.filter((i) => i !== hero.value))

/* Switching makes a different card the hero; carrying the old one's open
   panel across would show billing for an account the user did not tap. */
watch(() => props.activeId, () => { open.value = false })
</script>

<style scoped>
.lst { display: flex; flex-direction: column; }
.lst-hero-card { height: 196px; cursor: pointer; }
.lst-detail { margin-top: 12px; animation: lst-in 350ms cubic-bezier(.2, .8, .2, 1) both; }
.lst-label { margin: 22px 0 10px 6px; }
.lst-rows { display: flex; flex-direction: column; gap: 8px; }
.lst-slot { height: 196px; }
.lst-add { margin-top: 14px; }
@keyframes lst-in { from { opacity: 0; transform: translateY(8px) } to { opacity: 1; transform: none } }
@media (prefers-reduced-motion: reduce) { .lst-detail { animation: none } }
</style>
