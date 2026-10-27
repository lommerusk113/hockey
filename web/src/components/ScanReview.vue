<script setup lang="ts">
import { useToast } from '@nuxt/ui/composables'
import { computed, reactive, ref } from 'vue'
import { api } from '../api'
import { activePlayers, refresh } from '../store'

export interface ReviewRow {
  key: number
  include: boolean
  homeLabel: string
  awayLabel: string
  homePlayerId?: number
  awayPlayerId?: number
  homeGoals: number
  awayGoals: number
}

const rows = defineModel<ReviewRow[]>('rows', { required: true })
defineProps<{ duplicates: Set<number> }>()

const toast = useToast()
const normalize = (label: string) => label.trim().toLowerCase()

// A label means the same person on every line, so picking a player for "Ni" once fills in every "Ni".
function assign(label: string, playerId: number | undefined) {
  for (const row of rows.value) {
    if (normalize(row.homeLabel) === normalize(label)) row.homePlayerId = playerId
    if (normalize(row.awayLabel) === normalize(label)) row.awayPlayerId = playerId
  }
}

function addRow() {
  rows.value.push({ key: Math.max(-1, ...rows.value.map((r) => r.key)) + 1, include: true, homeLabel: '', awayLabel: '', homeGoals: 0, awayGoals: 0 })
}

function pick(row: ReviewRow, side: 'home' | 'away', playerId: number | undefined) {
  const label = side === 'home' ? row.homeLabel : row.awayLabel
  if (label.trim()) {
    assign(label, playerId)
  } else if (side === 'home') {
    row.homePlayerId = playerId
  } else {
    row.awayPlayerId = playerId
  }
}

const unknownLabels = computed(() => {
  const labels = new Map<string, string>()
  for (const row of rows.value.filter((r) => r.include)) {
    if (!row.homePlayerId && row.homeLabel.trim()) labels.set(normalize(row.homeLabel), row.homeLabel.trim())
    if (!row.awayPlayerId && row.awayLabel.trim()) labels.set(normalize(row.awayLabel), row.awayLabel.trim())
  }
  return [...labels.values()]
})
const newNames = reactive<Record<string, string>>({})
const adding = ref<string>()

async function addPlayer(label: string) {
  const name = (newNames[label] ?? '').trim()
  if (!name) return
  adding.value = label
  try {
    const player = await api.addPlayer(name, label)
    await refresh()
    assign(label, player.id)
  } catch (e) {
    toast.add({ title: 'Could not add player', description: (e as Error).message, color: 'error' })
  } finally {
    adding.value = undefined
  }
}
</script>

<template>
  <div class="space-y-3">
    <div v-if="unknownLabels.length" class="space-y-2 rounded-xl bg-warning/10 p-3">
      <p class="flex items-center gap-1.5 text-sm font-medium text-highlighted">
        <UIcon name="i-lucide-user-search" class="size-4 shrink-0" />Who is {{ unknownLabels.map((l) => `“${l}”`).join(', ') }}?
      </p>
      <p class="text-xs text-muted">Pick an existing player in the rows below, or add a new one here with this shorthand.</p>
      <form v-for="label in unknownLabels" :key="label" class="flex gap-2" @submit.prevent="addPlayer(label)">
        <UInput v-model="newNames[label]" :placeholder="`Full name for “${label}”`" class="flex-1" autocomplete="off" />
        <UButton type="submit" icon="i-lucide-user-plus" label="Add" color="neutral" variant="outline" :loading="adding === label" :disabled="!newNames[label]?.trim()" />
      </form>
    </div>

    <div
      v-for="(row, i) in rows"
      :key="row.key"
      class="space-y-2 rounded-xl border border-default p-3 transition"
      :class="{ 'opacity-50': !row.include }"
    >
      <div class="flex items-center gap-2 text-xs text-muted">
        <UCheckbox v-model="row.include" :aria-label="`Include line ${i + 1}`" />
        <span>Line {{ i + 1 }}</span>
        <span v-if="row.homeLabel || row.awayLabel" class="truncate">· “{{ row.homeLabel }} – {{ row.awayLabel }}”</span>
        <UBadge v-if="duplicates.has(row.key)" class="ml-auto shrink-0" color="warning" variant="subtle" size="sm" label="Already saved?" />
      </div>
      <div class="grid grid-cols-[minmax(0,1fr)_auto_auto_auto_minmax(0,1fr)] items-center gap-1.5">
        <USelect
          :model-value="row.homePlayerId"
          :items="activePlayers"
          value-key="id"
          label-key="name"
          :placeholder="row.homeLabel ? `“${row.homeLabel}”?` : 'Player'"
          :color="row.include && !row.homePlayerId ? 'warning' : 'neutral'"
          :highlight="row.include && !row.homePlayerId"
          :aria-label="`Line ${i + 1} first player`"
          @update:model-value="pick(row, 'home', $event)"
        />
        <UInput v-model.number="row.homeGoals" type="number" min="0" inputmode="numeric" class="w-11" :ui="{ base: 'display px-0 text-center text-xl' }" :aria-label="`Line ${i + 1} first player goals`" />
        <span class="text-dimmed">–</span>
        <UInput v-model.number="row.awayGoals" type="number" min="0" inputmode="numeric" class="w-11" :ui="{ base: 'display px-0 text-center text-xl' }" :aria-label="`Line ${i + 1} second player goals`" />
        <USelect
          :model-value="row.awayPlayerId"
          :items="activePlayers"
          value-key="id"
          label-key="name"
          :placeholder="row.awayLabel ? `“${row.awayLabel}”?` : 'Player'"
          :color="row.include && !row.awayPlayerId ? 'warning' : 'neutral'"
          :highlight="row.include && !row.awayPlayerId"
          :aria-label="`Line ${i + 1} second player`"
          @update:model-value="pick(row, 'away', $event)"
        />
      </div>
    </div>
    <UButton block color="neutral" variant="outline" icon="i-lucide-plus" label="Add missed line" @click="addRow" />
  </div>
</template>
