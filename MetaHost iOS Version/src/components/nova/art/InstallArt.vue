<template>
  <div class="ia">
    <!-- Safari's share glyph, the thing they have to find -->
    <div class="ia-share nv-glass-thin">
      <svg width="17" height="21" viewBox="0 0 17 21" fill="none" aria-hidden="true">
        <path
          d="M8.5 1.5 V13"
          stroke="var(--nv-accent)" stroke-width="2" stroke-linecap="round"
        />
        <path
          d="M4.7 5 L8.5 1.3 L12.3 5"
          stroke="var(--nv-accent)" stroke-width="2"
          stroke-linecap="round" stroke-linejoin="round"
        />
        <path
          d="M3 9 H1.6 V19.4 H15.4 V9 H14"
          stroke="var(--nv-accent)" stroke-width="2"
          stroke-linecap="round" stroke-linejoin="round"
        />
      </svg>
      <span class="ia-share-label">Share</span>
    </div>

    <svg class="ia-arrow" width="26" height="40" viewBox="0 0 26 40" fill="none" aria-hidden="true">
      <path
        d="M13 2 V28" stroke="var(--nv-flow-line)" stroke-width="1.5"
        stroke-dasharray="5 5" class="ia-flow"
      />
      <path
        d="M7 24 L13 31 L19 24" stroke="var(--nv-flow-line)" stroke-width="1.5"
        stroke-linecap="round" stroke-linejoin="round"
      />
    </svg>

    <!-- The payoff: the icon landing on the home screen -->
    <div class="ia-phone">
      <!-- Tile sits in the centre column so the caption below lines up
           under it. -->
      <div class="ia-grid">
        <span v-for="n in 4" :key="'a' + n" class="ia-slot" />

        <span class="ia-tile">
          <img :src="icon" alt="NovaHost app icon">
        </span>

        <span v-for="n in 4" :key="'b' + n" class="ia-slot" />
      </div>
      <div class="ia-caption">NovaHost</div>
      <div class="ia-indicator"><span /></div>
    </div>
  </div>
</template>

<script setup>
/**
 * @description Step 08 art — the NovaHost tile arriving on the home screen.
 * The instructional detail lives in the numbered steps on the sheet; this
 * carries the payoff.
 */
import icon from '../../../assets/onboarding/app_icon.png'
</script>

<style scoped>
.ia {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.ia-share {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 16px;
  border-radius: 999px;
  animation: nhUp 480ms var(--nv-ease) 80ms both;
}

.ia-share-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--nv-ink-2);
}

.ia-arrow { display: block; }
.ia-flow { animation: nhFlow 1400ms linear infinite; }

.ia-phone {
  position: relative;
  width: 132px;
  padding: 13px 10px 11px;
  border-radius: 24px;
  border: 3px solid var(--nv-ink);
  background: linear-gradient(170deg, #DCE6F5, #EDF1F8 60%, #F6F4F2);
  box-shadow: 0 20px 44px rgba(20, 22, 26, 0.24);
  animation: nhUp 560ms var(--nv-ease) 260ms both;
}

.ia-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px 10px;
}

.ia-slot {
  aspect-ratio: 1;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.42);
}

.ia-tile {
  aspect-ratio: 1;
  border-radius: 9px;
  overflow: hidden;
  box-shadow: 0 4px 10px rgba(20, 22, 26, 0.22);
  animation: nhPop 520ms var(--nv-spring) 900ms both;
}

.ia-tile img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ia-caption {
  margin-top: 6px;
  /* Sits under the second column, where the tile is. */
  margin-left: 33.3%;
  width: 33.3%;
  text-align: center;
  font-size: 8px;
  font-weight: 500;
  color: var(--nv-ink-2);
  animation: nhUp 400ms var(--nv-ease) 1100ms both;
}

.ia-indicator {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}

.ia-indicator span {
  width: 44px;
  height: 3px;
  border-radius: 999px;
  background: var(--nv-ink-3);
  opacity: 0.5;
}
</style>
