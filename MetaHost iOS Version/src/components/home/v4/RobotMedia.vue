<template>
  <!--
    The robot's art: the mentor's loop video when there is one, over the still
    image. The image is always underneath, so a robot with no video, a video
    still loading, or a video that fails all look the same as before.

    The video only plays while the robot is running and motion is on; stopped,
    Lite or Reduce Motion, it is paused and faded out to the still image. It
    stays mounted while paused so Start brings it back instantly.
  -->
  <img class="rh-media" :src="art" alt="" draggable="false" :style="mediaStyle">
  <video
    v-if="video && allowed"
    ref="el"
    class="rh-media rmv"
    :class="{ on: shown }"
    :src="video"
    :style="mediaStyle"
    muted
    loop
    playsinline
    webkit-playsinline
    preload="auto"
    disablepictureinpicture
    @playing="ready = true"
    @error="failed = true"
  ></video>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'

const props = defineProps({
  art: { type: String, default: '' },
  video: { type: String, default: '' },
  /* Robot running and motion allowed. */
  play: Boolean,
  /* Lite or Reduce Motion: never mount the video at all. */
  allowed: { type: Boolean, default: true },
  mediaStyle: { type: Object, default: () => ({}) },
})

const el = ref(null)
const ready = ref(false)
const failed = ref(false)
const shown = computed(() => props.play && ready.value && !failed.value)

/* iOS needs `muted` set as a property, not just the attribute, before
   play() or autoplay is refused. A refused play() is not an error worth
   showing: the still image is already there. */
function sync() {
  const v = el.value
  if (!v) return
  v.muted = true
  if (props.play) v.play().catch(() => {})
  else v.pause()
}

watch(() => props.play, sync)
watch(() => props.video, () => { ready.value = false; failed.value = false })
watch(el, sync)
onMounted(sync)
</script>

<style scoped>
/* Same box as RobotHome's .rh-media; that rule is scoped there and does not
   reach this component's elements. */
.rh-media { position: absolute; left: 0; top: 0; width: 100%; height: 100%; object-fit: cover; transform-origin: 50% 40%; pointer-events: none; }
.rmv { opacity: 0; transition: opacity .5s ease; }
.rmv.on { opacity: 1; }
</style>
