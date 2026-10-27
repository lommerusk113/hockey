<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RESULT_COLORS } from '../colors'
import { headToHead, inPeriod, perGame, recentForm, standings } from '../stats'
import { matches as allMatches, playerName, players, playerView } from '../store'
import MatchList from './MatchList.vue'
import PlayerAvatar from './PlayerAvatar.vue'

// Global profile: opened from standings, leaderboards or the Players panel via openPlayer()
const open = computed({
  get: () => !!playerView.value,
  set: (value) => {
    if (!value) playerView.value = undefined
  },
})
const playerId = computed(() => playerView.value?.id ?? 0)
const period = computed(() => playerView.value?.period ?? 'all')
const periodLabel = computed(() => (period.value === 'all' ? 'All time' : period.value.replace('-', ' ')))
const matches = computed(() => allMatches.value.filter((m) => inPeriod(m, period.value)))
const theirMatches = computed(() => matches.value.filter((m) => m.homePlayerId === playerId.value || m.awayPlayerId === playerId.value))
const showAll = ref(false)
watch(playerView, () => (showAll.value = false))

const row = computed(() => standings(matches.value).find((r) => r.playerId === playerId.value))
const form = computed(() => recentForm(matches.value, playerId.value))
const opponents = computed(() =>
  players.value
    .filter((p) => p.id !== playerId.value)
    .map((p) => ({ id: p.id, ...headToHead(matches.value, playerId.value, p.id) }))
    .filter((h) => h.games.length)
    .sort((a, b) => b.games.length - a.games.length),
)
const tiles = computed(() => {
  const r = row.value
  if (!r) return []
  return [
    { label: 'Played', value: r.played },
    { label: 'Win %', value: `${Math.round((r.wins / r.played) * 100)}%` },
    { label: 'W–D–L', value: `${r.wins}–${r.draws}–${r.losses}` },
    { label: 'Goals', value: `${r.goalsFor}–${r.goalsAgainst}` },
    { label: 'Scored / game', value: perGame(r.goalsFor, r).toFixed(1) },
    { label: 'Conceded / game', value: perGame(r.goalsAgainst, r).toFixed(1) },
    { label: 'Clean sheets', value: r.cleanSheets },
    { label: 'Best streak', value: r.longestWinStreak },
  ]
})
</script>

<template>
  <USlideover v-model:open="open" :title="playerName(playerId)" :description="periodLabel">
    <template #body>
      <div class="space-y-6">
        <div class="flex items-center gap-4">
          <PlayerAvatar :id="playerId" size="xl" />
          <div>
            <p class="eyebrow mb-1">Recent form</p>
            <div class="flex gap-1">
              <span
                v-for="(r, i) in form"
                :key="i"
                class="grid size-7 place-items-center rounded-md text-xs font-bold text-white"
                :style="{ background: RESULT_COLORS[r] }"
              >{{ r }}</span>
            </div>
          </div>
        </div>

        <div class="grid grid-cols-2 gap-2">
          <div v-for="t in tiles" :key="t.label" class="rounded-lg bg-elevated/60 p-3">
            <p class="text-xs text-muted">{{ t.label }}</p>
            <p class="display text-2xl text-highlighted">{{ t.value }}</p>
          </div>
        </div>

        <div v-if="opponents.length">
          <p class="eyebrow mb-2">Against</p>
          <ul class="divide-y divide-default">
            <li v-for="o in opponents" :key="o.id" class="flex items-center gap-3 py-2 text-sm">
              <PlayerAvatar :id="o.id" size="xs" />
              <span class="flex-1 font-medium">{{ playerName(o.id) }}</span>
              <span class="tabular-nums text-muted">{{ o.winsA }}W {{ o.draws }}D {{ o.winsB }}L</span>
            </li>
          </ul>
        </div>

        <div v-if="theirMatches.length">
          <div class="mb-1 flex items-center justify-between">
            <p class="eyebrow">Matches ({{ theirMatches.length }})</p>
            <UButton
              v-if="theirMatches.length > 8"
              size="xs"
              color="neutral"
              variant="link"
              :label="showAll ? 'Show fewer' : 'Show all'"
              @click="showAll = !showAll"
            />
          </div>
          <MatchList :matches="showAll ? theirMatches : theirMatches.slice(0, 8)" />
        </div>
        <UEmpty v-else icon="i-lucide-calendar-x" title="No matches in this period" />
      </div>
    </template>
  </USlideover>
</template>
