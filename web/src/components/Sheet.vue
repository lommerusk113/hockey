<script setup lang="ts">
import { isDesktop } from '../store'

// Bottom sheet on phones, centered modal on larger screens.
defineProps<{ title: string; description?: string }>()
const open = defineModel<boolean>('open', { default: false })
</script>

<template>
  <UModal v-if="isDesktop" v-model:open="open" :title="title" :description="description">
    <template #body><slot /></template>
    <template v-if="$slots.footer" #footer><slot name="footer" /></template>
  </UModal>
  <UDrawer v-else v-model:open="open" :title="title" :description="description" :ui="{ body: 'pb-2' }">
    <template #body><slot /></template>
    <template v-if="$slots.footer" #footer><slot name="footer" /></template>
  </UDrawer>
</template>
