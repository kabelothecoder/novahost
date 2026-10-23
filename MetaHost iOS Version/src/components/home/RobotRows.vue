<template>
  <div class="rr">
    <!-- Three different situations, three different sentences. A list that
         says "no robots" because the network was down is a lie about what
         the user owns. -->
    <p v-if="robot.loading && !robot.eaList.length" class="rr-msg cc-caption">
      Reading your licences…
    </p>

    <button
      v-for="ea in robot.eaList"
      :key="ea.id"
      class="rr-row"
      :class="{ 'is-active': ea.id === robot.activeEaId, 'is-busy': robot.busyId === ea.id }"
      :disabled="robot.busyId !== null"
      @click="pick(ea)"
    >
      <span class="rr-art">
        <img :src="ea.avatar || FALLBACK_AVATAR" alt="" decoding="async" />
      </span>

      <span class="rr-copy">
        <span class="rr-name">{{ ea.name }}</span>
        <span class="rr-sub cc-mono">{{ subtitle(ea) }}</span>
      </span>

      <span v-if="robot.busyId === ea.id" class="rr-spin" aria-hidden="true"></span>
      <span v-else-if="session.isRunning && ea.id === robot.activeEaId" class="cc-dot cc-dot-live"></span>
      <svg v-else-if="ea.id === robot.activeEaId" width="16" height="16" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"
           class="rr-tick" aria-hidden="true">
        <polyline points="20 6 9 17 4 12" />
      </svg>
      <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="rr-chev" aria-hidden="true">
        <polyline points="9 18 15 12 9 6" />
      </svg>
    </button>

    <p v-if="robot.error" class="rr-msg is-error cc-caption">{{ robot.error }}</p>

    <!-- ── Add ──────────────────────────────────────────────────────
         A licence key, not a purchase. The backend already binds several
         keys to one device (`device_activations`), and `validate-license`
         is the single way a key becomes valid on a handset — so adding a
         robot is the same call the unlock gate makes, not a second one. -->
    <form v-if="adding" class="rr-add-form" @submit.prevent="submit">
      <input
        ref="keyField"
        v-model="keyInput"
        class="rr-input cc-recess"
        type="text"
        placeholder="NOVA-XXXX-XXXX"
        autocapitalize="characters"
        autocomplete="off"
        spellcheck="false"
        :disabled="robot.busyId === 'new'"
        @input="addError = ''"
      />
      <div class="rr-add-actions">
        <button type="button" class="rr-add-cancel" :disabled="robot.busyId === 'new'" @click="close">
          Cancel
        </button>
        <button type="submit" class="rr-add-go" :disabled="!keyInput.trim() || robot.busyId === 'new'">
          <span v-if="robot.busyId === 'new'" class="rr-spin is-ink" aria-hidden="true"></span>
          <span>{{ robot.busyId === 'new' ? 'Checking' : 'Add robot' }}</span>
        </button>
      </div>
      <p v-if="addError" class="rr-msg is-error cc-caption">{{ addError }}</p>
    </form>

    <button v-else class="rr-add" @click="open">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="2" stroke-linecap="round" aria-hidden="true">
        <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
      </svg>
      <span>Connect new robot</span>
    </button>
  </div>
</template>

<script setup>
/**
 * The robot list, as rows.
 *
 * Shared by the Home widget and the top bar's switcher sheet so the two
 * cannot disagree about what the fleet is or how a swap behaves — they
 * were about to be the same list twice, which is how one of them ends up
 * six weeks behind the other.
 *
 * Every action here goes through stores/robot.js. Nothing local decides
 * which robot is active: switching re-validates the chosen licence, which
 * is what carries name, art, accent and allowed symbols across, and
 * adding is the same `validate-license` call the unlock gate makes.
 */
import { ref, nextTick, onMounted } from 'vue'
import {
  robot, selectRobot, addRobot, refreshFleet, FALLBACK_AVATAR,
} from '../../stores/robot.js'
import { session } from '../../stores/session.js'
import { haptic } from '../../composables/useSpeech.js'

const emit = defineEmits(['toast', 'switched'])

const adding = ref(false)
const keyInput = ref('')
const addError = ref('')
const keyField = ref(null)

/**
 * The doc's row reads "Licence · 284 days". `expires_at` IS on the list
 * row now, so the countdown is real where a mentor set one — and absent,
 * rather than invented, where they did not. Run state wins over both: a
 * robot that is executing is the more urgent fact about it.
 */
function subtitle(ea) {
  if (robot.busyId === ea.id) return 'Switching…'
  if (session.isRunning && ea.id === robot.activeEaId) return 'Executing'
  if (ea.expiresAt) {
    const days = Math.ceil((new Date(ea.expiresAt) - Date.now()) / 86400000)
    if (Number.isFinite(days) && days > 0) return `Licence · ${days} days`
  }
  return ea.id === robot.activeEaId ? 'Selected · standby' : 'Standby'
}

async function pick(ea) {
  if (ea.id === robot.activeEaId || robot.busyId) return
  haptic(40)
  const res = await selectRobot(ea.id)
  if (res.ok) {
    emit('toast', `Active robot · ${ea.name}`)
    emit('switched')
  } else {
    emit('toast', res.message)
  }
}

async function open() {
  adding.value = true
  addError.value = ''
  await nextTick()
  keyField.value?.focus()
}

function close() {
  adding.value = false
  keyInput.value = ''
  addError.value = ''
}

async function submit() {
  const res = await addRobot(keyInput.value)
  if (!res.ok) {
    addError.value = res.message
    return
  }
  haptic(60)
  emit('toast', `Robot connected · ${robot.name}`)
  emit('switched')
  close()
}

/* Refreshed on mount rather than only at Home's, because the switcher
   sheet can be opened long after Home mounted and a licence bought in
   between should appear without a restart. `refreshFleet` keeps whatever
   is already on screen if the read fails, so a second call is cheap and
   never destructive. */
onMounted(refreshFleet)
</script>

<style scoped>
.rr { display: flex; flex-direction: column; gap: 6px }

.rr-msg { margin: 2px 2px 0 }
.rr-msg.is-error { color: var(--cc-sell-text) }

.rr-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid transparent;
  border-radius: var(--cc-r-tile);
  background: none;
  text-align: left;
  cursor: pointer;
  transition: background 200ms var(--cc-ease), border-color 200ms var(--cc-ease),
              opacity 200ms var(--cc-ease);
}
.rr-row.is-active {
  background: var(--cc-accent-dim);
  border-color: var(--cc-accent-line);
}
/* Only the row being switched dims. Disabling the whole list during a
   switch is right — two swaps in flight would race on one stored licence
   key — but greying every row makes it look broken rather than busy. */
.rr-row:disabled { cursor: default }
.rr-row.is-busy { opacity: 0.62 }

.rr-art {
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--cc-card-2);
}
.rr-art img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 50% 38%;
  display: block;
}

.rr-copy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.rr-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--cc-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rr-sub { font-size: 10px; letter-spacing: 0.10em; text-transform: uppercase }

.rr-chev { color: var(--cc-ink-5); flex: none }
.rr-tick { color: var(--cc-accent-text); flex: none }

.rr-spin {
  flex: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid rgba(var(--cc-accent-rgb), 0.28);
  border-top-color: var(--cc-accent);
  animation: rrSpin 760ms linear infinite;
}
.rr-spin.is-ink {
  border-color: rgba(0, 0, 0, 0.22);
  border-top-color: var(--cc-accent-ink);
}
@keyframes rrSpin { to { transform: rotate(360deg) } }

/* ── Add ─────────────────────────────────────────────────────────── */
.rr-add {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 4px;
  padding: 10px;
  border: 1px dashed var(--cc-border-strong);
  border-radius: var(--cc-r-tile);
  background: none;
  font-family: var(--cc-font);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--cc-ink-4);
  cursor: pointer;
  /* No colour/border transition: both read from scheme tokens, and a
     transition on a token-fed property keeps painting the old scheme
     after a swap. See the note in command.css. */
}
.rr-add:active { color: var(--cc-accent-text); border-color: var(--cc-accent-line) }

.rr-add-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 6px;
}
.rr-input {
  width: 100%;
  height: 46px;
  padding: 0 14px;
  font-family: var(--cc-mono);
  font-size: 16px;              /* 16px exactly — iOS zooms a focused field below it */
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--cc-ink);
  outline: none;
}
.rr-input::placeholder { color: var(--cc-ink-5); letter-spacing: 0.06em }
.rr-input:focus { border-color: var(--cc-accent-line) }

.rr-add-actions { display: flex; gap: 8px }
.rr-add-cancel,
.rr-add-go {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 40px;
  border-radius: var(--cc-r-chip);
  font-family: var(--cc-font);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}
.rr-add-cancel {
  border: 1px solid var(--cc-border);
  background: none;
  color: var(--cc-ink-3);
}
.rr-add-go {
  border: none;
  background: var(--cc-accent);
  color: var(--cc-accent-ink);
}
.rr-add-go:disabled { opacity: 0.45; cursor: not-allowed }
</style>
