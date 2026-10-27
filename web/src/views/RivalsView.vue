<script setup lang="ts">
import { computed, ref, watchEffect } from 'vue'
import MatchList from '../components/MatchList.vue'
import PlayerAvatar from '../components/PlayerAvatar.vue'
import { playerColor, RESULT_COLORS } from '../colors'
import { headToHead } from '../stats'
import { matches, playerIds, playerName, players } from '../store'

const a = ref<number>()
const b = ref<number>()
watchEffect(() => {
  if (a.value === undefined && players.value.length > 1) {
    a.value = players.value[0].id
    b.value = players.value[1].id
  }
})

const h2h = computed(() => (a.value && b.value ? headToHead(matches.value, a.value, b.value) : undefined))
const share = (n: number) => `${(n / (h2h.value?.games.length || 1)) * 100}%`
const allOpen = ref(false)
const newestFirst = computed(() => [...(h2h.value?.games ?? [])].reverse())

const cumulative = computed(() =>
  (h2h.value?.games ?? []).map((m, i) => ({
    game: `#${i + 1} · ${new Date(m.playedAt).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })}`,
    a: h2h.value!.cumulativeA[i],
    b: h2h.value!.cumulativeB[i],
  })),
)
const categories = computed(() => [
  { key: 'a', label: playerName.value(a.value!), color: playerColor(a.value!, playerIds.value) },
  { key: 'b', label: playerName.value(b.value!), color: playerColor(b.value!, playerIds.value) },
])
</script>

<template>
  <h1 class="text-2xl font-extrabold tracking-tight text-highlighted">Rivals</h1>

  <UEmpty v-if="players.length < 2" icon="i-lucide-swords" title="Need two players" description="Add players to compare head to head." />
  <template v-else>
    <UCard :ui="{ body: 'sm:p-6' }">
      <div class="grid grid-cols-[1fr_auto_1fr] items-center gap-3">
        <div class="flex flex-col items-center gap-2">
          <PlayerAvatar v-if="a" :id="a" size="xl" />
          <USelect v-model="a" :items="players" value-key="id" label-key="name" class="w-full max-w-48" aria-label="First player" />
        </div>
        <span class="display text-2xl text-dimmed">VS</span>
        <div class="flex flex-col items-center gap-2">
          <PlayerAvatar v-if="b" :id="b" size="xl" />
          <USelect v-model="b" :items="players" value-key="id" label-key="name" class="w-full max-w-48" aria-label="Second player" />
        </div>
      </div>

      <template v-if="h2h && a !== b && h2h.games.length">
        <div class="mt-6 grid grid-cols-3 text-center">
          <div>
            <p class="display text-5xl text-highlighted">{{ h2h.winsA }}</p>
            <p class="flex items-center justify-center gap-1.5 text-xs text-muted"><PlayerAvatar :id="a!" size="3xs" />{{ playerName(a!) }} wins</p>
          </div>
          <div>
            <p class="display text-5xl text-muted">{{ h2h.draws }}</p>
            <p class="text-xs text-muted">Draws</p>
          </div>
          <div>
            <p class="display text-5xl text-highlighted">{{ h2h.winsB }}</p>
            <p class="flex items-center justify-center gap-1.5 text-xs text-muted"><PlayerAvatar :id="b!" size="3xs" />{{ playerName(b!) }} wins</p>
          </div>
        </div>
        <div class="mt-4 flex h-3 gap-0.5 overflow-hidden rounded-full bg-elevated" role="img" :aria-label="`${h2h.winsA} wins, ${h2h.draws} draws, ${h2h.winsB} wins`">
          <div :style="{ width: share(h2h.winsA), background: playerColor(a!, playerIds) }" />
          <div :style="{ width: share(h2h.draws), background: RESULT_COLORS.D }" />
          <div :style="{ width: share(h2h.winsB), background: playerColor(b!, playerIds) }" />
        </div>
        <p class="mt-3 text-center text-sm text-muted">
          {{ h2h.games.length }} games · goals {{ h2h.goalsA }}–{{ h2h.goalsB }} · avg
          {{ (h2h.goalsA / h2h.games.length).toFixed(1) }}–{{ (h2h.goalsB / h2h.games.length).toFixed(1) }}
        </p>
      </template>
    </UCard>

    <UAlert v-if="a === b" color="warning" variant="subtle" icon="i-lucide-info" title="Pick two different players" />
    <UEmpty v-else-if="h2h && !h2h.games.length" icon="i-lucide-swords" title="No meetings yet" :description="`${playerName(a!)} and ${playerName(b!)} haven't played each other.`" />
    <template v-else-if="h2h">
      <UCard>
        <p class="eyebrow mb-3">Wins over time</p>
        <UChart type="line" :data="cumulative" index="game" :categories="categories" curve="step" legend :x-axis="false" :height="220" aria-label="Cumulative wins in this rivalry" />
      </UCard>
      <UCard>
        <div class="mb-1 flex items-center justify-between">
          <p class="eyebrow">Last meetings</p>
          <UButton
            v-if="h2h.games.length > 5"
            size="xs"
            color="neutral"
            variant="link"
            trailing-icon="i-lucide-chevron-right"
            :label="`All ${h2h.games.length}`"
            @click="allOpen = true"
          />
        </div>
        <MatchList :matches="newestFirst.slice(0, 5)" />
      </UCard>
      <USlideover v-model:open="allOpen" :title="`${playerName(a!)} vs ${playerName(b!)}`" :description="`${h2h.games.length} meetings`">
        <template #body><MatchList :matches="newestFirst" /></template>
      </USlideover>
    </template>
  </template>
</template>
