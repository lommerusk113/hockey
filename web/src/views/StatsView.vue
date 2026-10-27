<script setup lang="ts">
import { computed, ref } from 'vue'
import MetricSlideover from '../components/MetricSlideover.vue'
import PlayerAvatar from '../components/PlayerAvatar.vue'
import { RESULT_COLORS, playerColor } from '../colors'
import { inPeriod, leaders, matchRecords, METRICS, minGames, perGame, periods, standings, winPct, winsOverTime, type Metric } from '../stats'
import { matches, openMatchId, openPlayer, playerIds, playerName } from '../store'

const period = ref('all')
const periodLabel = (p: string) => (p === 'all' ? 'All time' : p.replace('-', ' '))
const periodItems = computed(() => periods(matches.value).map((p) => ({ value: p, label: periodLabel(p) })))
const filtered = computed(() => matches.value.filter((m) => inPeriod(m, period.value)))
const rows = computed(() => standings(filtered.value))

// The four headline cards are per-game stats; every stat opens its full leaderboard
const HIGHLIGHTS = ['winRate', 'goalsPerGame', 'concededPerGame', 'goalDiffPerGame']
const highlights = computed(() => METRICS.filter((m) => HIGHLIGHTS.includes(m.key)).map((metric) => ({ metric, leaders: leaders(rows.value, metric) })))
const records = computed(() => METRICS.map((metric) => ({ metric, leaders: leaders(rows.value, metric) })))
const matchRecordList = computed(() => matchRecords(filtered.value))
const recordsOpen = ref(false)
const names = (ids: number[]) => ids.map(playerName.value).join(', ')

const metricOpen = ref(false)
const selectedMetric = ref<Metric>()
const showMetric = (metric: Metric) => {
  selectedMetric.value = metric
  metricOpen.value = true
}

// Standings columns: shorthand header, full name in the tooltip
const COLUMNS = {
  GP: 'Games played',
  W: 'Wins',
  D: 'Draws',
  L: 'Losses',
  'W-D-L': 'Wins - draws - losses',
  GF: 'Goals for (scored)',
  GA: 'Goals against (conceded)',
  GD: 'Goal difference',
  'GF/G': 'Goals scored per game',
  'GA/G': 'Goals conceded per game',
  'Win%': 'Share of games won',
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
      <button
        v-for="h in highlights"
        :key="h.metric.key"
        type="button"
        class="rounded-2xl border border-default bg-default p-3 text-left shadow-sm transition hover:border-primary/50 active:scale-[.98] sm:p-4 dark:bg-elevated/40 dark:shadow-none"
        @click="showMetric(h.metric)"
      >
        <div class="mb-2 flex items-center gap-2">
          <span class="grid size-8 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary">
            <UIcon :name="h.metric.icon" class="size-4.5" />
          </span>
          <p class="min-w-0 flex-1 truncate text-xs font-medium text-muted">{{ h.metric.short ?? h.metric.title }}</p>
          <UIcon name="i-lucide-chevron-right" class="size-4 text-dimmed" />
        </div>
        <p class="display text-4xl leading-none text-highlighted">{{ h.leaders[0] ? h.metric.format(h.leaders[0].value) : '–' }}</p>
        <div class="mt-2 flex items-center gap-1.5">
          <PlayerAvatar v-for="l in h.leaders.slice(0, 3)" :key="l.row.playerId" :id="l.row.playerId" size="2xs" />
          <span class="truncate text-sm">{{ names(h.leaders.map((l) => l.row.playerId)) }}</span>
        </div>
      </button>
    </div>
    <p class="-mt-2 text-xs text-muted">Per-game stats count players with at least {{ minGames(rows) }} games. Tap a stat to see everyone.</p>
    <UButton block color="neutral" variant="outline" size="lg" icon="i-lucide-medal" trailing-icon="i-lucide-chevron-right" label="All records" class="bg-default" @click="recordsOpen = true" />

    <div class="grid items-start gap-4 lg:grid-cols-[1.3fr_1fr]">
      <UCard :ui="{ body: 'p-0 sm:p-0' }">
        <p class="eyebrow px-4 pt-4 pb-2">Standings</p>
        <table class="w-full text-sm tabular-nums">
          <thead class="bg-elevated/50 text-[11px] tracking-wider text-muted uppercase">
            <tr class="[&>th]:px-2 [&>th]:py-2 [&>th]:font-semibold">
              <th class="w-8 !pl-4 text-left">#</th>
              <th class="text-left">Player</th>
              <th
                v-for="(full, key) in COLUMNS"
                :key="key"
                class="text-right"
                :class="{
                  'hidden sm:table-cell': ['W', 'D', 'L', 'GA/G'].includes(key),
                  'sm:hidden': key === 'W-D-L',
                  'hidden xl:table-cell': ['GF', 'GA'].includes(key),
                  'hidden md:table-cell': key === 'GD',
                }"
              >
                <UTooltip :text="full" :content="{ side: 'top' }">
                  <abbr :title="full" class="cursor-help no-underline">{{ key }}</abbr>
                </UTooltip>
              </th>
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
              @click="openPlayer(r.playerId, period)"
              @keydown.enter="openPlayer(r.playerId, period)"
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
              <td class="hidden text-right md:table-cell" :class="r.goalsFor > r.goalsAgainst ? 'text-success' : r.goalsFor < r.goalsAgainst ? 'text-error' : 'text-muted'">
                {{ r.goalsFor - r.goalsAgainst > 0 ? '+' : '' }}{{ r.goalsFor - r.goalsAgainst }}
              </td>
              <td class="text-right">{{ perGame(r.goalsFor, r).toFixed(1) }}</td>
              <td class="hidden text-right sm:table-cell">{{ perGame(r.goalsAgainst, r).toFixed(1) }}</td>
              <td class="text-right font-semibold text-highlighted">{{ Math.round(winPct(r) * 100) }}%</td>
              <td class="!pr-3 text-dimmed"><UIcon name="i-lucide-chevron-right" class="size-4 align-middle" /></td>
            </tr>
          </tbody>
        </table>
        <!-- No hover on phones: spell out the short column names -->
        <p class="px-4 py-3 text-[11px] leading-relaxed text-muted sm:hidden">
          GP games played · W-D-L wins-draws-losses · GF/G goals per game · Win% share of games won
        </p>
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

    <USlideover v-model:open="recordsOpen" title="Records" :description="`${periodLabel(period)} · tap a record to see everyone`">
      <template #body>
        <ul class="divide-y divide-default">
          <li v-for="r in records" :key="r.metric.key">
            <button type="button" class="flex w-full items-center gap-3 py-3 text-left hover:bg-elevated/40" @click="showMetric(r.metric)">
              <UIcon :name="r.metric.icon" class="size-5 shrink-0 text-primary" />
              <div class="min-w-0 flex-1">
                <p class="text-sm text-muted">{{ r.metric.title }}</p>
                <p class="truncate font-medium">{{ names(r.leaders.map((l) => l.row.playerId)) || '–' }}</p>
              </div>
              <span class="display text-2xl text-highlighted">{{ r.leaders[0] ? r.metric.format(r.leaders[0].value) : '–' }}</span>
              <UIcon name="i-lucide-chevron-right" class="size-4 text-dimmed" />
            </button>
          </li>
          <li v-for="r in matchRecordList" :key="r.title">
            <button type="button" class="flex w-full items-center gap-3 py-3 text-left hover:bg-elevated/40" @click="openMatchId = r.match.id">
              <UIcon name="i-lucide-swords" class="size-5 shrink-0 text-primary" />
              <div class="min-w-0 flex-1">
                <p class="text-sm text-muted">{{ r.title }}</p>
                <p class="truncate font-medium">{{ playerName(r.match.homePlayerId) }} vs {{ playerName(r.match.awayPlayerId) }}</p>
              </div>
              <span class="display text-2xl text-highlighted">{{ r.match.homeGoals }}–{{ r.match.awayGoals }}</span>
              <UIcon name="i-lucide-chevron-right" class="size-4 text-dimmed" />
            </button>
          </li>
        </ul>
      </template>
    </USlideover>

    <MetricSlideover v-model:open="metricOpen" :metric="selectedMetric" :rows="rows" :period="period" :period-label="periodLabel(period)" />
  </template>
</template>
