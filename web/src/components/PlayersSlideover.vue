<script setup lang="ts">
import { useToast } from '@nuxt/ui/composables'
import { computed, ref, watch } from 'vue'
import { api, type Player } from '../api'
import { matches, openPlayer, players, playersOpen, refresh } from '../store'
import PlayerAvatar from './PlayerAvatar.vue'

const name = ref('')
const shortName = ref('')
const saving = ref(false)
const editing = ref<Player>()
const editName = ref('')
const editShortName = ref('')
const confirmingDelete = ref<number>()
const toast = useToast()

const gamesPlayed = computed(() => {
  const counts = new Map<number, number>()
  for (const m of matches.value) {
    for (const id of [m.homePlayerId, m.awayPlayerId]) counts.set(id, (counts.get(id) ?? 0) + 1)
  }
  return counts
})
const taken = (field: 'name' | 'shortName', value: string, exceptId?: number) =>
  !!value.trim() && players.value.some((p) => p.id !== exceptId && p[field].toLowerCase() === value.trim().toLowerCase())
const addProblem = computed(() =>
  taken('name', name.value) ? 'That name is already taken' : taken('shortName', shortName.value) ? 'That shorthand is already used' : '',
)
const editProblem = computed(() =>
  taken('name', editName.value, editing.value?.id)
    ? 'That name is already taken'
    : taken('shortName', editShortName.value, editing.value?.id)
      ? 'That shorthand is already used'
      : '',
)

watch(playersOpen, () => {
  editing.value = undefined
  confirmingDelete.value = undefined
})

async function attempt(action: () => Promise<unknown>, failTitle: string) {
  saving.value = true
  try {
    await action()
    await refresh()
    return true
  } catch (e) {
    toast.add({ title: failTitle, description: (e as Error).message, color: 'error' })
    return false
  } finally {
    saving.value = false
  }
}

async function add() {
  if (!name.value.trim() || !shortName.value.trim() || addProblem.value) return
  if (await attempt(() => api.addPlayer(name.value.trim(), shortName.value.trim()), 'Could not add player')) {
    name.value = ''
    shortName.value = ''
  }
}

function startEdit(p: Player) {
  editing.value = p
  editName.value = p.name
  editShortName.value = p.shortName
  confirmingDelete.value = undefined
}

async function update() {
  const p = editing.value
  if (!p || !editName.value.trim() || !editShortName.value.trim() || editProblem.value) return
  if (await attempt(() => api.updatePlayer(p.id, editName.value.trim(), editShortName.value.trim()), 'Could not save')) editing.value = undefined
}

async function remove(p: Player) {
  if (confirmingDelete.value !== p.id) {
    confirmingDelete.value = p.id
    return
  }
  if (await attempt(() => api.deletePlayer(p.id), 'Could not remove')) {
    toast.add({ title: `${p.name} removed`, icon: 'i-lucide-user-minus' })
  }
  confirmingDelete.value = undefined
}

// Active players first, disabled ones below
const sorted = computed(() => [...players.value].sort((a, b) => Number(b.active) - Number(a.active)))

const setActive = (p: Player, active: boolean) =>
  attempt(() => api.setPlayerActive(p, active), active ? 'Could not enable' : 'Could not disable')

const menu = (p: Player) => [
  [{ label: 'Edit', icon: 'i-lucide-pencil', onSelect: () => startEdit(p) }],
  gamesPlayed.value.get(p.id)
    ? [{ label: 'Has matches, cannot remove', icon: 'i-lucide-lock', disabled: true }]
    : [{ label: 'Remove', icon: 'i-lucide-trash-2', color: 'error' as const, onSelect: () => remove(p) }],
]
</script>

<template>
  <USlideover v-model:open="playersOpen" title="Players" description="Everyone who plays at your table">
    <template #body>
      <form class="flex gap-2" @submit.prevent="add">
        <UInput v-model="name" placeholder="Name" class="min-w-0 flex-1" size="lg" autocomplete="off" aria-label="New player name" />
        <UInput v-model="shortName" placeholder="Short" class="w-20" size="lg" autocomplete="off" aria-label="Shorthand on the results board" />
        <UButton type="submit" icon="i-lucide-user-plus" size="lg" aria-label="Add player" :loading="saving" :disabled="!name.trim() || !shortName.trim() || !!addProblem" />
      </form>
      <p class="mt-1 mb-4 min-h-4 text-xs" :class="addProblem ? 'text-error' : 'text-muted'">
        {{ addProblem || 'Shorthand is how they are written on the board, e.g. Ni for Nico.' }}
      </p>

      <UEmpty v-if="!players.length" icon="i-lucide-users" title="No players yet" description="Add the people you play against." />
      <ul class="divide-y divide-default">
        <li v-for="p in sorted" :key="p.id" class="flex min-h-14 items-center gap-3 py-2">
          <PlayerAvatar :id="p.id" size="md" :class="{ 'opacity-40 grayscale': !p.active }" />
          <form v-if="editing?.id === p.id" class="flex-1 space-y-1" @submit.prevent="update">
            <div class="flex gap-2">
              <UInput v-model="editName" class="min-w-0 flex-1" autofocus aria-label="Player name" @keydown.esc="editing = undefined" />
              <UInput v-model="editShortName" class="w-16" aria-label="Shorthand" @keydown.esc="editing = undefined" />
              <UButton type="submit" icon="i-lucide-check" aria-label="Save" :loading="saving" :disabled="!!editProblem" />
              <UButton icon="i-lucide-x" color="neutral" variant="ghost" aria-label="Cancel" @click="editing = undefined" />
            </div>
            <p v-if="editProblem" class="text-xs text-error">{{ editProblem }}</p>
          </form>
          <template v-else>
            <button type="button" class="min-w-0 flex-1 text-left" :class="{ 'opacity-60': !p.active }" @click="openPlayer(p.id)">
              <p class="truncate font-medium hover:underline">{{ p.name }}</p>
              <p class="text-xs text-muted">
                “{{ p.shortName }}” · {{ gamesPlayed.get(p.id) ?? 0 }} matches<template v-if="!p.active"> · disabled</template>
              </p>
            </button>
            <UTooltip :text="p.active ? 'Playing — turn off to hide when recording matches' : 'Disabled — turn on to play again'">
              <USwitch
                :model-value="p.active"
                :aria-label="`${p.name} is ${p.active ? 'active' : 'disabled'}`"
                :disabled="saving"
                @update:model-value="(v: boolean) => setActive(p, v)"
              />
            </UTooltip>
            <UButton
              v-if="confirmingDelete === p.id"
              color="error"
              size="sm"
              label="Tap to remove"
              :loading="saving"
              @click="remove(p)"
            />
            <UDropdownMenu v-else :items="menu(p)" :content="{ align: 'end' }">
              <UButton icon="i-lucide-ellipsis-vertical" color="neutral" variant="ghost" :aria-label="`Options for ${p.name}`" />
            </UDropdownMenu>
          </template>
        </li>
      </ul>
    </template>
  </USlideover>
</template>
