<template>
  <img
    class="nv-mark"
    :src="logo"
    :alt="alt"
    :style="{ width: size + 'px', height: size + 'px' }"
  >
</template>

<script setup>
/**
 * @description The NovaHost robot, circle-masked out of the wide source art.
 *
 * The source is 992x618, so the head only lands centred with the design's
 * exact `cover` + `50% 61%` framing and the radial mask feathering from
 * 38% to 54%. Those three numbers are load-bearing — changing any one
 * crops the robot off-centre.
 */
import logo from '../../assets/onboarding/app_logo.png'

defineProps({
  size: { type: Number, default: 248 },
  alt:  { type: String, default: 'NovaHost' },
})
</script>

<style scoped>
.nv-mark {
  display: block;
  object-fit: cover;
  object-position: 50% 61%;
  -webkit-mask: radial-gradient(circle at 50% 50%, #000 38%, transparent 54%);
  mask: radial-gradient(circle at 50% 50%, #000 38%, transparent 54%);
}
</style>
