<script setup lang="ts">
import { computed, ref } from 'vue'
import PlayerAvatar from '../components/PlayerAvatar.vue'
import PlayerSlideover from '../components/PlayerSlideover.vue'
import { RESULT_COLORS, playerColor } from '../colors'
import { inPeriod, leaders, periods, standings, winPct, winsOverTime } from '../stats'
import { matches, playerIds, playerName } from '../store'

const period = ref('all')
const periodLabel = (p: string) => (p === 'all' ? 'All time' : p.replace('-', ' '))
const periodItems = computed(() => periods(matches.value).map((p) => ({ value: p, label: periodLabel(p) })))
const filtered = computed(() => matches.value.filter((m) => inPeriod(m, period.value)))
const rows = computed(() => standings(filtered.value))

const allLeaders = computed(() => leaders(filtered.value))
const HIGHLIGHTS: Record<string, string> = {
  'Most wins': 'i-lucide-trophy',
  'Most goals': 'i-lucide-goal',
  'Best defence (conceded/game)': 'i-lucide-shield',
  'Longest win streak': 'i-lucide-flame',
}
const highlights = computed(() => allLeaders.value.filter((l) => l.title in HIGHLIGHTS))
const recordsOpen = ref(false)
const shortTitle = (t: string) => t.replace(/ \(.*\)/, '').replace('Longest win streak', 'Win streak')
const holder = (l: { playerIds: number[]; match?: { homePlayerId: number; awayPlayerId: number } }) =>
  l.match ? `${playerName.value(l.match.homePlayerId)} vs ${playerName.value(l.match.awayPlayerId)}` : l.playerIds.map(playerName.value).join(', ')

const selectedPlayer = ref<number>()
const playerOpen = ref(false)
const openPlayer = (id: number) => {
  selectedPlayer.value = id
  playerOpen.value = true
}

const chartTab = ref('trend')
const chartTabs = [
  { label: 'Wins over time', slot: 'trend' as const, value: 'trend' },
  { label: 'Results', slot: 'results' as const, value: 'results' },
  { label: 'Goals', slot: 'goals' as const, value: 'goals' },
]
const trend = computed(() => {
  const { labels, wins } = winsOverTime(filtered.value, period.value)
  return {
    data: labels.map((label, i) => Object.fromEntries([['bucket', label.replace('-', ' ')], ...[...wins].map(([id, s]) => [`p${id}`, s[i]])])),
    categories: [...wins.keys()].map((id) => ({ key: `p${id}`, label: playerName.value(id), color: playerColor(id, playerIds.value) })),
  }
})
const resultsData = computed(() => rows.value.map((r) => ({ player: playerName.value(r.playerId), W: r.wins, D: r.draws, L: r.losses })))
const resultCategories = [
  { key: 'W', label: 'Wins', color: RESULT_COLORS.W },
  { key: 'D', label: 'Draws', color: RESULT_COLORS.D },
  { key: 'L', label: 'Losses', color: RESULT_COLORS.L },
]
const goalsData = computed(() => rows.value.map((r) => ({ player: playerName.value(r.playerId), GF: r.goalsFor, GA: r.goalsAgainst })))
const goalCategories = [
  { key: 'GF', label: 'Scored', color: 'var(--player-1)' },
  { key: 'GA', label: 'Conceded', color: 'var(--player-6)' },
]
</script>

<template>
  <div class="flex items-center justify-between gap-3">
    <h1 class="text-2xl font-extrabold tracking-tight text-highlighted">Stats</h1>
    <USelect v-model="period" :items="periodItems" icon="i-lucide-calendar-range" class="w-40" aria-label="Period" />
  </div>

  <UEmpty v-if="!filtered.length" icon="i-lucide-chart-column" title="No matches in this period" description="Pick another period or record a match." />
  <template v-else>
    <div class="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <UCard v-for="l in highlights" :key="l.title" :ui="{ body: 'p-3 sm:p-4' }">
        <div class="mb-2 flex items-center gap-2">
          <span class="grid size-8 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary">
            <UIcon :name="HIGHLIGHTS[l.title]" class="size-4.5" />
          </span>
          <p class="truncate text-xs font-medium text-muted">{{ shortTitle(l.title) }}</p>
        </div>
        <p class="display text-4xl leading-none text-highlighted">{{ l.value }}</p>
        <div class="mt-2 flex items-center gap-1.5">
          <PlayerAvatar v-for="id in l.playerIds.slice(0, 3)" :key="id" :id="id" size="2xs" />
          <span class="truncate text-sm">{{ holder(l) }}</span>
        </div>
      </UCard>
    </div>
    <UButton block color="neutral" variant="outline" size="lg" icon="i-lucide-medal" trailing-icon="i-lucide-chevron-right" label="All records" class="bg-default" @click="recordsOpen = true" />

    <div class="grid items-start gap-4 lg:grid-cols-[1.3fr_1fr]">
      <UCard :ui="{ body: 'p-0 sm:p-0' }">
        <p class="eyebrow px-4 pt-4 pb-2">Standings</p>
        <table class="w-full text-sm tabular-nums">
          <thead class="bg-elevated/50 text-[11px] tracking-wider text-muted uppercase">
            <tr class="[&>th]:px-2 [&>th]:py-2 [&>th]:font-semibold">
              <th class="w-8 !pl-4 text-left">#</th>
              <th class="text-left">Player</th>
              <th class="text-right"><abbr title="Games played" class="no-underline">GP</abbr></th>
              <th class="hidden text-right sm:table-cell"><abbr title="Wins" class="no-underline">W</abbr></th>
              <th class="hidden text-right sm:table-cell"><abbr title="Draws" class="no-underline">D</abbr></th>
              <th class="hidden text-right sm:table-cell"><abbr title="Losses" class="no-underline">L</abbr></th>
              <th class="text-right sm:hidden"><abbr title="Wins - draws - losses" class="no-underline">W-D-L</abbr></th>
              <th class="hidden text-right xl:table-cell"><abbr title="Goals for" class="no-underline">GF</abbr></th>
              <th class="hidden text-right xl:table-cell"><abbr title="Goals against" class="no-underline">GA</abbr></th>
              <th class="text-right"><abbr title="Goal difference" class="no-underline">GD</abbr></th>
              <th class="text-right">Win%</th>
              <th class="w-8 !pr-3"><span class="sr-only">Details</span></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-default">
            <tr
              v-for="(r, i) in rows"
              :key="r.playerId"
              tabindex="0"
              role="button"
              :aria-label="`Details for ${playerName(r.playerId)}`"
              class="cursor-pointer transition hover:bg-elevated/60 focus-visible:bg-elevated focus-visible:outline-none active:bg-elevated [&>td]:px-2 [&>td]:py-3"
              @click="openPlayer(r.playerId)"
              @keydown.enter="openPlayer(r.playerId)"
            >
              <td class="!pl-4 font-semibold text-muted">{{ i + 1 }}</td>
              <td>
                <span class="flex items-center gap-2 font-medium">
                  <PlayerAvatar :id="r.playerId" size="xs" />
                  <span class="truncate">{{ playerName(r.playerId) }}</span>
                </span>
              </td>
              <td class="text-right">{{ r.played }}</td>
              <td class="hidden text-right sm:table-cell">{{ r.wins }}</td>
              <td class="hidden text-right sm:table-cell">{{ r.draws }}</td>
              <td class="hidden text-right sm:table-cell">{{ r.losses }}</td>
              <td class="text-right whitespace-nowrap sm:hidden">{{ r.wins }}-{{ r.draws }}-{{ r.losses }}</td>
              <td class="hidden text-right xl:table-cell">{{ r.goalsFor }}</td>
              <td class="hidden text-right xl:table-cell">{{ r.goalsAgainst }}</td>
              <td class="text-right" :class="r.goalsFor > r.goalsAgainst ? 'text-success' : r.goalsFor < r.goalsAgainst ? 'text-error' : 'text-muted'">
                {{ r.goalsFor - r.goalsAgainst > 0 ? '+' : '' }}{{ r.goalsFor - r.goalsAgainst }}
              </td>
              <td class="text-right font-semibold text-highlighted">{{ Math.round(winPct(r) * 100) }}%</td>
              <td class="!pr-3 text-dimmed"><UIcon name="i-lucide-chevron-right" class="size-4 align-middle" /></td>
            </tr>
          </tbody>
        </table>
      </UCard>

      <UCard>
        <UTabs v-model="chartTab" :items="chartTabs" variant="link" class="w-full" :ui="{ list: 'mb-4' }">
          <template #trend>
            <UChart
              type="line"
              :data="trend.data"
              index="bucket"
              :categories="trend.categories"
              curve="linear"
              points
              legend
              :height="280"
              :aria-label="`Wins per ${period === 'all' ? 'quarter' : 'month'} for each player`"
            />
          </template>
          <template #results>
            <UChart type="bar" :data="resultsData" index="player" :categories="resultCategories" stacked legend :height="280" aria-label="Wins, draws and losses per player" />
          </template>
          <template #goals>
            <UChart type="bar" :data="goalsData" index="player" :categories="goalCategories" legend :height="280" aria-label="Goals scored and conceded per player" />
          </template>
        </UTabs>
      </UCard>
    </div>

    <USlideover v-model:open="recordsOpen" title="Records" :description="periodLabel(period)">
      <template #body>
        <ul class="divide-y divide-default">
          <li v-for="l in allLeaders" :key="l.title" class="flex items-center gap-3 py-3">
            <div class="min-w-0 flex-1">
              <p class="text-sm text-muted">{{ l.title }}</p>
              <p class="truncate font-medium">{{ holder(l) }}</p>
            </div>
            <span class="display text-3xl text-highlighted">{{ l.value }}</span>
          </li>
        </ul>
      </template>
    </USlideover>

    <PlayerSlideover
      v-if="selectedPlayer"
      v-model:open="playerOpen"
      :player-id="selectedPlayer"
      :matches="filtered"
      :period-label="periodLabel(period)"
    />
  </template>
</template>
