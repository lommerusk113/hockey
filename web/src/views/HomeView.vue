<script setup lang="ts">
import { useToast } from '@nuxt/ui/composables'
import { computed, ref, watch } from 'vue'
import { api } from '../api'
import MatchList from '../components/MatchList.vue'
import PlayerAvatar from '../components/PlayerAvatar.vue'
import { standings, winPct } from '../stats'
import { addMatchOpen, loadError, loaded, matches, playerName, players, playersOpen, refresh } from '../store'

const latest = computed(() => matches.value[0])
const top = computed(() => standings(matches.value).slice(0, 3))
const allOpen = ref(false)

// Select mode in "All matches": tick rows, then delete them in one go (tap twice to confirm)
const selected = ref<number[]>()
const selecting = computed(() => selected.value !== undefined)
const confirmingDelete = ref(false)
const deleting = ref(false)
const toast = useToast()
const toggleSelected = (id: number) => {
  const list = selected.value!
  const at = list.indexOf(id)
  if (at >= 0) list.splice(at, 1)
  else list.push(id)
}
const stopSelecting = () => {
  selected.value = undefined
  confirmingDelete.value = false
}
watch(allOpen, stopSelecting)
watch(() => selected.value?.length, () => (confirmingDelete.value = false))

async function deleteSelected() {
  if (!confirmingDelete.value) {
    confirmingDelete.value = true
    return
  }
  deleting.value = true
  try {
    const count = selected.value!.length
    await api.deleteMatches(selected.value!)
    await refresh()
    toast.add({ title: `${count} ${count === 1 ? 'match' : 'matches'} deleted`, icon: 'i-lucide-trash-2' })
    stopSelecting()
  } catch (e) {
    toast.add({ title: 'Could not delete', description: (e as Error).message, color: 'error' })
  } finally {
    deleting.value = false
  }
}
const RANK_CLASSES = [
  'bg-amber-400/20 text-amber-700 dark:text-amber-300',
  'bg-slate-400/20 text-slate-600 dark:text-slate-300',
  'bg-orange-700/20 text-orange-800 dark:text-orange-300',
]

const relative = (iso: string) => {
  const days = Math.round((new Date(iso).getTime() - Date.now()) / 86_400_000)
  return new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' }).format(days, 'day')
}
</script>

<template>
  <UAlert
    v-if="loadError"
    color="error"
    variant="subtle"
    icon="i-lucide-wifi-off"
    title="Could not load data"
    :description="loadError"
    :actions="[{ label: 'Retry', color: 'error', variant: 'outline', onClick: refresh }]"
  />

  <div v-if="!loaded" class="space-y-4">
    <USkeleton class="h-40 w-full rounded-xl" />
    <USkeleton class="h-56 w-full rounded-xl" />
  </div>

  <UEmpty
    v-else-if="!loadError && players.length < 2"
    variant="naked"
    size="lg"
    icon="i-lucide-users"
    title="Welcome to the rink"
    description="Add the players at your table to start tracking games."
    :actions="[{ label: 'Add players', icon: 'i-lucide-user-plus', size: 'lg', onClick: () => (playersOpen = true) }]"
    class="py-16"
  />

  <UEmpty
    v-else-if="!loadError && !matches.length"
    variant="naked"
    size="lg"
    icon="i-lucide-trophy"
    title="No matches yet"
    description="Play a game, snap the scoreboard and it's on the board."
    :actions="[{ label: 'Record first match', icon: 'i-lucide-plus', size: 'lg', onClick: () => (addMatchOpen = true) }]"
    class="py-16"
  />

  <template v-else>
    <div class="grid items-start gap-4 lg:grid-cols-[3fr_2fr]">
      <div class="space-y-4">
        <UCard v-if="latest" :ui="{ root: 'relative overflow-hidden bg-linear-to-br from-primary/15 via-transparent to-transparent', body: 'sm:p-6' }">
          <div aria-hidden="true" class="pointer-events-none absolute inset-y-0 left-1/2 w-0.5 bg-error/15" />
          <div aria-hidden="true" class="pointer-events-none absolute top-1/2 left-1/2 size-28 -translate-1/2 rounded-full border-2 border-primary/10" />
          <div class="relative mb-4 flex items-center justify-between">
            <p class="eyebrow">Latest match</p>
            <UBadge color="neutral" variant="subtle" :label="relative(latest.playedAt)" />
          </div>
          <div class="relative grid grid-cols-[1fr_auto_1fr] items-center gap-3 text-center">
            <div class="flex min-w-0 flex-col items-center gap-2">
              <PlayerAvatar :id="latest.homePlayerId" size="xl" />
              <span class="max-w-full truncate" :class="latest.homeGoals >= latest.awayGoals ? 'font-semibold text-highlighted' : 'text-muted'">
                {{ playerName(latest.homePlayerId) }}
              </span>
            </div>
            <span class="display text-7xl text-highlighted">{{ latest.homeGoals }}–{{ latest.awayGoals }}</span>
            <div class="flex min-w-0 flex-col items-center gap-2">
              <PlayerAvatar :id="latest.awayPlayerId" size="xl" />
              <span class="max-w-full truncate" :class="latest.awayGoals >= latest.homeGoals ? 'font-semibold text-highlighted' : 'text-muted'">
                {{ playerName(latest.awayPlayerId) }}
              </span>
            </div>
          </div>
        </UCard>

        <UCard>
          <div class="mb-1 flex items-center justify-between">
            <p class="eyebrow">Recent matches</p>
            <UButton
              v-if="matches.length > 5"
              size="sm"
              color="neutral"
              variant="link"
              trailing-icon="i-lucide-chevron-right"
              :label="`See all ${matches.length}`"
              @click="allOpen = true"
            />
          </div>
          <MatchList :matches="matches.slice(0, 5)" />
        </UCard>
      </div>

      <UCard>
        <div class="mb-3 flex items-center justify-between">
          <p class="eyebrow">Leaderboard</p>
          <UButton to="/stats" size="sm" color="neutral" variant="link" trailing-icon="i-lucide-chevron-right" label="All stats" />
        </div>
        <ol class="space-y-1">
          <li
            v-for="(r, i) in top"
            :key="r.playerId"
            class="-mx-2 flex items-center gap-3 rounded-lg px-2 py-2"
            :class="{ 'bg-primary/5': i === 0 }"
          >
            <span class="grid size-6 place-items-center rounded-full text-xs font-bold" :class="RANK_CLASSES[i]">{{ i + 1 }}</span>
            <PlayerAvatar :id="r.playerId" size="md" />
            <span class="flex-1 truncate font-medium">{{ playerName(r.playerId) }}</span>
            <span class="text-right">
              <span class="display block text-xl leading-none text-highlighted">{{ Math.round(winPct(r) * 100) }}%</span>
              <span class="text-xs text-muted">{{ r.wins }} wins</span>
            </span>
          </li>
        </ol>
      </UCard>
    </div>

    <USlideover v-model:open="allOpen" title="All matches" :description="`${matches.length} played`">
      <template #actions>
        <UButton
          :label="selecting ? 'Done' : 'Select'"
          :icon="selecting ? undefined : 'i-lucide-list-checks'"
          color="neutral"
          variant="ghost"
          size="sm"
          @click="selecting ? stopSelecting() : (selected = [])"
        />
      </template>
      <template #body>
        <MatchList :matches="matches" :selected="selected" @toggle="toggleSelected" />
      </template>
      <template v-if="selecting" #footer>
        <div class="flex w-full items-center gap-2">
          <UButton
            color="neutral"
            variant="ghost"
            :label="selected!.length === matches.length ? 'Clear' : 'Select all'"
            @click="selected = selected!.length === matches.length ? [] : matches.map((m) => m.id)"
          />
          <UButton
            class="ml-auto"
            color="error"
            :variant="confirmingDelete ? 'solid' : 'soft'"
            icon="i-lucide-trash-2"
            :loading="deleting"
            :disabled="!selected!.length"
            :label="confirmingDelete ? `Tap again to delete ${selected!.length}` : `Delete ${selected!.length}`"
            @click="deleteSelected"
          />
        </div>
      </template>
    </USlideover>
  </template>
</template>
