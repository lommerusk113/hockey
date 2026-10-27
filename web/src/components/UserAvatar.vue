<script setup lang="ts">
import { computed } from 'vue'
import { userById } from '../store'

const props = withDefaults(defineProps<{ id: number; size?: '3xs' | '2xs' | 'xs' | 'sm' | 'md' | 'lg' }>(), { size: 'sm' })
const user = computed(() => userById.value(props.id))
</script>

<template>
  <UAvatar :src="user?.pictureUrl" :alt="user?.name ?? '?'" :size="size" referrerpolicy="no-referrer" />
</template>
