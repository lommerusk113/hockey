<script setup lang="ts">
import { useToast } from '@nuxt/ui/composables'
import { computed, ref, watch } from 'vue'
import { api } from '../api'
import { addMatchOpen, editingMatch, matches, openMatchId, playerName, refresh } from '../store'
import MatchSocial from './MatchSocial.vue'
import PlayerAvatar from './PlayerAvatar.vue'
import Sheet from './Sheet.vue'

// One sheet for the whole app, opened by setting openMatchId (from lists or a push notification link)
const match = computed(() => matches.value.find((m) => m.id === openMatchId.value))
const open = computed({
  get: () => !!match.value,
  set: (value) => {
    if (!value) openMatchId.value = undefined
  },
})
const confirming = ref(false)
const deleting = ref(false)
const toast = useToast()

watch(open, () => (confirming.value = false))

function edit() {
  editingMatch.value = match.value
  open.value = false
  addMatchOpen.value = true
}

async function remove() {
  if (!confirming.value) {
    confirming.value = true
    return
  }
  deleting.value = true
  try {
    await api.deleteMatch(match.value!.id)
    await refresh()
    open.value = false
    toast.add({ title: 'Match deleted', icon: 'i-lucide-trash-2' })
  } catch (e) {
    toast.add({ title: 'Could not delete', description: (e as Error).message, color: 'error' })
  } finally {
    deleting.value = false
  }
}

const fullDate = (iso: string) =>
  new Date(iso).toLocaleString(undefined, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' })
</script>

<template>
  <Sheet v-if="match" v-model:open="open" title="Match" :description="fullDate(match.playedAt)">
    <div class="grid grid-cols-[1fr_auto_1fr] items-center gap-4 py-4 text-center">
      <div class="flex flex-col items-center gap-2">
        <PlayerAvatar :id="match.homePlayerId" size="xl" />
        <span class="font-medium">{{ playerName(match.homePlayerId) }}</span>
      </div>
      <div class="display text-6xl text-highlighted">{{ match.homeGoals }}–{{ match.awayGoals }}</div>
      <div class="flex flex-col items-center gap-2">
        <PlayerAvatar :id="match.awayPlayerId" size="xl" />
        <span class="font-medium">{{ playerName(match.awayPlayerId) }}</span>
      </div>
    </div>
    <MatchSocial :match-id="match.id" />
    <template #footer>
      <div class="flex w-full gap-2">
        <UButton block size="lg" icon="i-lucide-pencil" label="Edit" class="flex-1" @click="edit" />
        <UButton
          block
          size="lg"
          class="flex-1"
          :color="confirming ? 'error' : 'neutral'"
          :variant="confirming ? 'solid' : 'subtle'"
          icon="i-lucide-trash-2"
          :loading="deleting"
          :label="confirming ? 'Tap again to delete' : 'Delete'"
          @click="remove"
        />
      </div>
    </template>
  </Sheet>
</template>
