<script setup lang="ts">
import type { Match } from '../api'
import { openMatchId } from '../store'
import MatchRow from './MatchRow.vue'

defineProps<{ matches: Match[] }>()
</script>

<template>
  <div class="-mx-2 divide-y divide-default lg:mx-auto lg:max-w-md">
    <MatchRow v-for="m in matches" :key="m.id" :match="m" @select="openMatchId = m.id" />
  </div>
</template>
