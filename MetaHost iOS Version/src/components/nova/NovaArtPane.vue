<template>
  <div class="nv-art" :style="{ '--nv-reserve': reserve }">
    <!-- Ambient colour that lives UNDER the sheet. Without this the sheet
         blurs a flat near-white and reads as an opaque panel rather than
         as glass. It is the material's content, not decoration. -->
    <div class="nv-art-bed" aria-hidden="true" />

    <!-- `key` re-triggers the enter animation on every step change. -->
    <div :key="artKey" class="nv-art-inner">
      <slot />
    </div>
  </div>
</template>

<script setup>
/**
 * @description The illustration pane every onboarding step opens with.
 *
 * Unlike the source design — where the pane stops where the sheet starts —
 * this fills the full shell and the frosted sheet floats over it. That gives
 * the Apple glass material something real to blur, instead of the design's
 * flat 26px overlap.
 */
defineProps({
  /** Change this to replay the enter animation. */
  artKey: { type: [String, Number], default: 0 },
  /**
   * Share of the shell height the sheet covers, 0–100. The pane still
   * paints full-bleed behind the sheet — this only keeps the artwork
   * itself centred in the space that stays visible.
   *
   * Applied in viewport units, not percent: percentage padding resolves
   * against the containing block's *width*, which on a 375pt phone
   * reserved 233px instead of 503px and slid the art under the sheet.
   */
  reserve: { type: Number, default: 44 },
})
</script>

<style scoped>
.nv-art {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, var(--nv-art-top), var(--nv-art-bottom));
  overflow: hidden;
  padding-bottom: calc(var(--nv-reserve, 44) * 1vh);
}

/* dvh tracks the collapsing Safari toolbar; vh above is the fallback. */
@supports (height: 1dvh) {
  .nv-art { padding-bottom: calc(var(--nv-reserve, 44) * 1dvh); }
}

/* Accent pools straddling the sheet's top edge. The straddle is the point:
   the same blob reads sharp above the edge and blurred below it, and that
   discontinuity is what tells the eye the sheet is transparent. Tight radii
   rather than broad washes, because a blur over a smooth gradient is
   invisible — the material needs structure to work on. */
.nv-art-bed {
  position: absolute;
  left: -24%;
  right: -24%;
  bottom: -22%;
  height: 88%;
  pointer-events: none;
  background:
    radial-gradient(38% 30% at 22% 44%, rgba(162, 136, 227, 0.46), transparent 68%),
    radial-gradient(42% 32% at 82% 62%, rgba(92, 156, 230, 0.42), transparent 70%),
    radial-gradient(30% 24% at 54% 86%, rgba(162, 136, 227, 0.30), transparent 72%);
  animation: nhGlow 9000ms ease-in-out infinite;
}

.nv-art-inner {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: nhArt 620ms var(--nv-ease) both;
}

/* The illustrations are drawn at fixed pixel sizes from the design. On a
   short handset (SE, mini, or any device in landscape) the reserved band
   is smaller than the art, so scale the whole thing down rather than let
   the sheet crop it. Read by the nhArt keyframes. */
@media (max-height: 740px) {
  .nv-art { --nv-art-scale: 0.84; }
}
@media (max-height: 670px) {
  .nv-art { --nv-art-scale: 0.72; }
}
@media (max-height: 600px) {
  .nv-art { --nv-art-scale: 0.6; }
}
</style>
