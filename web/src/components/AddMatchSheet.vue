<script setup lang="ts">
import { useToast } from '@nuxt/ui/composables'
import { computed, reactive, ref, watch } from 'vue'
import { api, downscale, type NewMatch } from '../api'
import { addMatchOpen, editingMatch, matches, playerName, players, playersOpen, refresh } from '../store'
import ScanReview, { type ReviewRow } from './ScanReview.vue'
import Sheet from './Sheet.vue'

const toLocalInput = (d: Date) => {
  const local = new Date(d)
  local.setMinutes(local.getMinutes() - local.getTimezoneOffset())
  return local.toISOString().slice(0, 16)
}

const form = reactive({ homePlayerId: undefined as number | undefined, awayPlayerId: undefined as number | undefined, homeGoals: 0, awayGoals: 0 })
const playedAt = ref<string>()
const scanning = ref(false)
const saving = ref(false)
const scanned = ref<{ left: number; right: number }>()
// Matches read from a photo of the results board, waiting for review
const rows = ref<ReviewRow[]>([])
const reviewing = computed(() => rows.value.length > 0)
const fileInput = ref<HTMLInputElement>()
const toast = useToast()

// Opening the sheet: load the match being edited, otherwise preselect the pair that played last.
watch(addMatchOpen, (open) => {
  if (!open) {
    editingMatch.value = undefined
    return
  }
  const edit = editingMatch.value
  if (edit) {
    Object.assign(form, { homePlayerId: edit.homePlayerId, awayPlayerId: edit.awayPlayerId, homeGoals: edit.homeGoals, awayGoals: edit.awayGoals })
    playedAt.value = toLocalInput(new Date(edit.playedAt))
    dateMode.value = 'other'
  } else {
    const last = matches.value[0]
    Object.assign(form, { homePlayerId: form.homePlayerId ?? last?.homePlayerId, awayPlayerId: form.awayPlayerId ?? last?.awayPlayerId, homeGoals: 0, awayGoals: 0 })
    setDateMode('now')
  }
  scanned.value = undefined
  rows.value = []
  triedSave.value = false
})

const samePlayer = computed(() => !!form.homePlayerId && form.homePlayerId === form.awayPlayerId)
const blocker = computed(() => {
  if (reviewing.value) {
    const included = rows.value.filter((r) => r.include)
    if (!included.length) return 'Tick at least one match'
    const line = rows.value.findIndex((r) => r.include && (!r.homePlayerId || !r.awayPlayerId || r.homePlayerId === r.awayPlayerId))
    return line >= 0 ? `Pick two different players on line ${line + 1}` : ''
  }
  if (!form.homePlayerId || !form.awayPlayerId) return 'Pick a player for each side'
  if (samePlayer.value) return 'Pick two different players'
  return ''
})
const scoreless = computed(() => form.homeGoals === 0 && form.awayGoals === 0)
const triedSave = ref(false)

async function onPhoto(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  scanning.value = true
  try {
    const found = await api.scan(await downscale(file))
    if (!found.length) {
      toast.add({ title: "Couldn't find any results", description: 'Try a closer, straighter photo or enter the score by hand.', color: 'warning', icon: 'i-lucide-scan-eye' })
      return
    }
    // A game scoreboard has no names: one unlabeled match goes straight into the normal form.
    if (found.length === 1 && !found[0].homeLabel && !found[0].awayLabel) {
      form.homeGoals = found[0].homeGoals
      form.awayGoals = found[0].awayGoals
      scanned.value = { left: found[0].homeGoals, right: found[0].awayGoals }
      return
    }
    rows.value = found.map((m, i) => ({ key: i, include: true, ...m }))
    for (const row of rows.value) row.include = !duplicates.value.has(row.key)
  } catch (e) {
    toast.add({ title: 'Scan failed', description: (e as Error).message, color: 'error' })
  } finally {
    scanning.value = false
  }
}

function swap() {
  ;[form.homePlayerId, form.awayPlayerId] = [form.awayPlayerId, form.homePlayerId]
}

type DateMode = 'now' | 'yesterday' | 'other'
const dateMode = ref<DateMode>('now')
const dateModes: { label: string; value: DateMode }[] = [
  { label: 'Now', value: 'now' },
  { label: 'Yesterday', value: 'yesterday' },
  { label: 'Other…', value: 'other' },
]
function setDateMode(mode: DateMode) {
  dateMode.value = mode
  if (mode === 'now') playedAt.value = undefined
  if (mode === 'yesterday') playedAt.value = toLocalInput(new Date(Date.now() - 86_400_000))
  if (mode === 'other') playedAt.value ??= toLocalInput(new Date())
}

const sameDay = (a: Date, b: Date) => a.toDateString() === b.toDateString()
// A results board grows through the evening, so a re-scan repeats earlier lines; flag those already saved that day.
const duplicates = computed(() => {
  const day = playedAt.value ? new Date(playedAt.value) : new Date()
  const saved = matches.value.filter((m) => sameDay(new Date(m.playedAt), day))
  const same = (r: ReviewRow) =>
    saved.some(
      (m) =>
        (m.homePlayerId === r.homePlayerId && m.awayPlayerId === r.awayPlayerId && m.homeGoals === r.homeGoals && m.awayGoals === r.awayGoals) ||
        (m.homePlayerId === r.awayPlayerId && m.awayPlayerId === r.homePlayerId && m.homeGoals === r.awayGoals && m.awayGoals === r.homeGoals),
    )
  return new Set(rows.value.filter(same).map((r) => r.key))
})
const includedCount = computed(() => rows.value.filter((r) => r.include).length)

async function saveReviewed() {
  const included = rows.value.filter((r) => r.include)
  // Lines are written top to bottom as games are played: space them a minute apart, ending at the chosen time.
  const end = playedAt.value ? new Date(playedAt.value).getTime() : Date.now()
  const batch: NewMatch[] = included.map((r, i) => ({
    homePlayerId: r.homePlayerId!,
    awayPlayerId: r.awayPlayerId!,
    homeGoals: r.homeGoals,
    awayGoals: r.awayGoals,
    playedAt: new Date(end - (included.length - 1 - i) * 60_000).toISOString(),
  }))
  await api.addMatches(batch)
  await refresh()
  toast.add({ title: `${batch.length} matches saved`, color: 'success', icon: 'i-lucide-check' })
  addMatchOpen.value = false
}

async function save() {
  if (reviewing.value) {
    if (blocker.value) return
    saving.value = true
    try {
      await saveReviewed()
    } catch (e) {
      toast.add({ title: 'Could not save', description: (e as Error).message, color: 'error' })
    } finally {
      saving.value = false
    }
    return
  }
  if (blocker.value) return
  // A 0–0 is usually an untouched form: ask once before saving it.
  if (scoreless.value && !triedSave.value) {
    triedSave.value = true
    return
  }
  saving.value = true
  const edit = editingMatch.value
  try {
    const payload = {
      homePlayerId: form.homePlayerId!,
      awayPlayerId: form.awayPlayerId!,
      homeGoals: form.homeGoals,
      awayGoals: form.awayGoals,
      playedAt: playedAt.value ? new Date(playedAt.value).toISOString() : (edit?.playedAt ?? new Date().toISOString()),
    }
    await (edit ? api.updateMatch(edit.id, payload) : api.addMatch(payload))
    await refresh()
    toast.add({
      title: edit ? 'Match updated' : 'Match saved',
      description: `${playerName.value(form.homePlayerId!)} ${form.homeGoals}–${form.awayGoals} ${playerName.value(form.awayPlayerId!)}`,
      color: 'success',
      icon: 'i-lucide-check',
    })
    addMatchOpen.value = false
  } catch (e) {
    toast.add({ title: 'Could not save', description: (e as Error).message, color: 'error' })
  } finally {
    saving.value = false
  }
}

function managePlayers() {
  addMatchOpen.value = false
  playersOpen.value = true
}
</script>

<template>
  <Sheet
    v-model:open="addMatchOpen"
    :title="editingMatch ? 'Edit match' : 'New match'"
    :description="editingMatch ? 'Fix the players, score or time' : reviewing ? `Found ${rows.length} matches — check them before saving` : 'Scan the results board or enter a score'"
  >
    <UEmpty
      v-if="players.length < 2"
      icon="i-lucide-users"
      title="Add players first"
      description="You need at least two players to record a match."
      :actions="[{ label: 'Add players', icon: 'i-lucide-user-plus', onClick: managePlayers }]"
    />
    <form v-else id="add-match" class="space-y-5" @submit.prevent="save">
      <template v-if="!editingMatch">
        <UButton
          block
          size="xl"
          variant="soft"
          icon="i-lucide-camera"
          :loading="scanning"
          :label="scanning ? 'Reading results…' : reviewing ? 'Scan again' : 'Scan results'"
          @click="fileInput?.click()"
        />
        <input ref="fileInput" type="file" accept="image/*" capture="environment" class="hidden" @change="onPhoto" />
      </template>

      <ScanReview v-if="reviewing" v-model:rows="rows" :duplicates="duplicates" />

      <div v-else class="space-y-3">
        <div class="grid grid-cols-[1fr_auto_1fr] items-end gap-2">
          <UFormField label="Left side" :ui="{ label: 'eyebrow' }">
            <USelect v-model="form.homePlayerId" :items="players" value-key="id" label-key="name" placeholder="Player" class="w-full" size="xl" />
          </UFormField>
          <UButton icon="i-lucide-arrow-left-right" color="neutral" variant="ghost" size="lg" aria-label="Swap players" @click="swap" />
          <UFormField label="Right side" :ui="{ label: 'eyebrow', root: 'text-right' }">
            <USelect v-model="form.awayPlayerId" :items="players" value-key="id" label-key="name" placeholder="Player" class="w-full" size="xl" />
          </UFormField>
        </div>
        <p class="-mt-1 text-xs text-muted">Sides as seen on the scoreboard.</p>

        <div class="grid grid-cols-2 gap-3">
          <UInputNumber
            v-for="side in (['homeGoals', 'awayGoals'] as const)"
            :key="side"
            v-model="form[side]"
            :min="0"
            :max="99"
            size="xl"
            inputmode="numeric"
            :aria-label="side === 'homeGoals' ? 'Left side goals' : 'Right side goals'"
            :increment="{ size: 'xl', variant: 'soft' }"
            :decrement="{ size: 'xl', variant: 'soft', color: 'neutral' }"
            :ui="{ base: 'display h-16 px-14 text-center text-4xl' }"
          />
        </div>

        <UAlert
          v-if="scanned"
          icon="i-lucide-sparkles"
          color="info"
          variant="subtle"
          :description="`Scanned ${scanned.left}–${scanned.right}: ${form.homePlayerId ? playerName(form.homePlayerId) : 'left'} ${form.homeGoals} – ${form.awayGoals} ${form.awayPlayerId ? playerName(form.awayPlayerId) : 'right'}. Swap players if the sides are wrong.`"
        />
        <p v-else-if="scoreless && !blocker && triedSave" class="flex items-center gap-1.5 text-sm text-muted">
          <UIcon name="i-lucide-info" class="size-4 shrink-0" />The score is 0–0. Tap Save again to confirm.
        </p>
      </div>

      <UFormField label="Played" :ui="{ label: 'eyebrow' }">
        <div class="flex flex-wrap gap-2">
          <UButton
            v-for="m in dateModes"
            :key="m.value"
            :label="m.label"
            size="sm"
            :color="dateMode === m.value ? 'primary' : 'neutral'"
            :variant="dateMode === m.value ? 'solid' : 'subtle'"
            @click="setDateMode(m.value)"
          />
        </div>
        <UInput v-if="dateMode === 'other'" v-model="playedAt" type="datetime-local" class="mt-2 w-full" />
      </UFormField>

      <UButton v-if="reviewing" variant="link" color="neutral" size="sm" icon="i-lucide-pencil" label="Enter one match by hand instead" class="px-0" @click="rows = []" />
      <UButton v-else variant="link" color="neutral" size="sm" icon="i-lucide-users" label="Manage players" class="px-0" @click="managePlayers" />
    </form>
    <template v-if="players.length >= 2" #footer>
      <div class="w-full space-y-1.5">
        <UButton type="submit" form="add-match" block size="xl" :label="editingMatch ? 'Save changes' : reviewing ? `Save ${includedCount} ${includedCount === 1 ? 'match' : 'matches'}` : 'Save match'" icon="i-lucide-check" :loading="saving" :disabled="!!blocker" />
        <p v-if="blocker" class="text-center text-xs text-muted">{{ blocker }}</p>
      </div>
    </template>
  </Sheet>
</template>
