<template>
  <WidgetCard label="Robot List">
    <!-- The rows, the switching and the key entry all live in RobotRows,
         shared with the top bar's switcher sheet. Two copies of a list
         that swaps the user's active licence is one copy too many. -->
    <RobotRows @toast="$emit('toast', $event)" />
  </WidgetCard>
</template>

<script setup>
import WidgetCard from '../WidgetCard.vue'
import RobotRows from '../RobotRows.vue'

defineEmits(['toast'])
</script>
