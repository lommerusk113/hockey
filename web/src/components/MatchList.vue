<script setup lang="ts">
import type { Match } from '../api'
import { openMatchId } from '../store'
import MatchRow from './MatchRow.vue'

// With `selected` (select mode), tapping a row asks the parent to tick it instead of opening the match
defineProps<{ matches: Match[]; selected?: number[] }>()
defineEmits<{ toggle: [id: number] }>()
</script>

<template>
  <div class="-mx-2 divide-y divide-default lg:mx-auto lg:max-w-md">
    <div v-for="m in matches" :key="m.id" class="flex items-center gap-1">
      <UCheckbox
        v-if="selected"
        :model-value="selected.includes(m.id)"
        class="pl-2"
        :aria-label="`Select match ${m.id}`"
        @update:model-value="$emit('toggle', m.id)"
      />
      <MatchRow :match="m" class="flex-1" @select="selected ? $emit('toggle', m.id) : (openMatchId = m.id)" />
    </div>
  </div>
</template>
