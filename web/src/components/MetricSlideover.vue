<script setup lang="ts">
import { computed } from 'vue'
import { playerColor } from '../colors'
import { minGames, ranking, type Metric, type Row } from '../stats'
import { openPlayer, playerIds, playerName } from '../store'
import PlayerAvatar from './PlayerAvatar.vue'

// Full ranking for one stat, so you can see how close everyone is
const props = defineProps<{ metric?: Metric; rows: Row[]; period: string; periodLabel: string }>()
const open = defineModel<boolean>('open', { default: false })

const ranked = computed(() => (props.metric ? ranking(props.rows, props.metric) : []))
const qualified = computed(() => ranked.value.filter((r) => r.qualified))
const unranked = computed(() => ranked.value.filter((r) => !r.qualified))

// Bars span worst..best of the ranked players, with a floor so the last place still shows
const bar = (value: number) => {
  const values = qualified.value.map((r) => r.value)
  const best = props.metric?.lowerIsBetter ? Math.min(...values) : Math.max(...values)
  const worst = props.metric?.lowerIsBetter ? Math.max(...values) : Math.min(...values)
  const share = best === worst ? 1 : (value - worst) / (best - worst)
  return `${8 + 92 * share}%`
}
const gap = (value: number) => {
  const leader = qualified.value[0]?.value ?? value
  const diff = Math.abs(leader - value)
  return diff ? `${props.metric!.format(diff).replace('+', '')} behind` : 'Leader'
}
</script>

<template>
  <USlideover v-model:open="open" :title="metric?.title ?? ''" :description="metric ? `${metric.description} · ${periodLabel}` : ''">
    <template #body>
      <ol v-if="metric" class="space-y-1">
        <li v-for="(r, i) in qualified" :key="r.row.playerId">
          <button
            type="button"
            class="-mx-2 flex w-[calc(100%+1rem)] items-center gap-3 rounded-lg px-2 py-2 text-left hover:bg-elevated/60"
            @click="openPlayer(r.row.playerId, period)"
          >
            <span class="w-5 text-center text-sm font-semibold text-muted">{{ i + 1 }}</span>
            <PlayerAvatar :id="r.row.playerId" size="sm" />
            <span class="min-w-0 flex-1">
              <span class="flex items-baseline justify-between gap-2">
                <span class="truncate font-medium">{{ playerName(r.row.playerId) }}</span>
                <span class="display text-xl text-highlighted">{{ metric.format(r.value) }}</span>
              </span>
              <span class="mt-1 flex items-center gap-2">
                <span class="h-1.5 flex-1 overflow-hidden rounded-full bg-elevated">
                  <span class="block h-full rounded-full" :style="{ width: bar(r.value), background: playerColor(r.row.playerId, playerIds) }" />
                </span>
                <span class="w-20 shrink-0 text-right text-xs text-muted">{{ gap(r.value) }}</span>
              </span>
            </span>
          </button>
        </li>
      </ol>

      <div v-if="unranked.length" class="mt-6">
        <p class="eyebrow mb-2">Not enough games (under {{ minGames(rows) }})</p>
        <ul class="space-y-1 opacity-70">
          <li v-for="r in unranked" :key="r.row.playerId">
            <button type="button" class="flex w-full items-center gap-3 rounded-lg py-1.5 text-left hover:bg-elevated/60" @click="openPlayer(r.row.playerId, period)">
              <PlayerAvatar :id="r.row.playerId" size="xs" />
              <span class="flex-1 truncate text-sm">{{ playerName(r.row.playerId) }}</span>
              <span class="text-sm tabular-nums text-muted">{{ metric?.format(r.value) }} · {{ r.row.played }} GP</span>
            </button>
          </li>
        </ul>
      </div>
    </template>
  </USlideover>
</template>
