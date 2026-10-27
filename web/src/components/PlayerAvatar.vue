<script setup lang="ts">
import { computed } from 'vue'
import { playerColor } from '../colors'
import { playerIds, playerName, playerShortName } from '../store'

const props = withDefaults(defineProps<{ id: number; size?: '3xs' | '2xs' | 'xs' | 'sm' | 'md' | 'lg' | 'xl' | '2xl' }>(), { size: 'sm' })
const name = computed(() => playerName.value(props.id))
</script>

<template>
  <UAvatar
    :text="playerShortName(id).toUpperCase()"
    :alt="name"
    :size="size"
    :ui="{ root: 'ring-2 ring-default', fallback: '!text-white font-bold tracking-tight [text-shadow:0_1px_2px_rgb(0_0_0/.45)]' }"
    :style="{ background: playerColor(id, playerIds) }"
  />
</template>
