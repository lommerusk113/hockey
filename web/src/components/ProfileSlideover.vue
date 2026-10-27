<script setup lang="ts">
import { useToast } from '@nuxt/ui/composables'
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'
import { disablePush, enablePush, pushState, type PushState } from '../push'
import { me, players, playersOpen, profileOpen, refresh, users } from '../store'

const toast = useToast()
const push = ref<PushState>('off')
const busy = ref(false)

onMounted(async () => (push.value = await pushState()))

// Players nobody else has claimed
const playerItems = computed(() => [
  { label: 'Not playing', value: undefined },
  ...players.value
    .filter((p) => (p.active || p.id === me.value?.playerId) && !users.value.some((u) => u.playerId === p.id && u.id !== me.value?.id))
    .map((p) => ({ label: `${p.name} (${p.shortName})`, value: p.id })),
])

async function save(change: Partial<{ playerId: number | undefined; notifyMatches: boolean; notifyChat: boolean }>) {
  if (!me.value) return
  try {
    me.value = await api.updateMe({ playerId: me.value.playerId, notifyMatches: me.value.notifyMatches, notifyChat: me.value.notifyChat, ...change })
    await refresh()
  } catch (e) {
    toast.add({ title: 'Could not save', description: (e as Error).message, color: 'error' })
  }
}

const setPlayer = (playerId: number | undefined) => save({ playerId })
const setNotifyMatches = (notifyMatches: boolean) => save({ notifyMatches })
const setNotifyChat = (notifyChat: boolean) => save({ notifyChat })

async function togglePush() {
  busy.value = true
  try {
    await (push.value === 'on' ? disablePush() : enablePush())
    push.value = await pushState()
  } catch (e) {
    toast.add({ title: 'Notifications', description: (e as Error).message, color: 'error' })
    push.value = await pushState()
  } finally {
    busy.value = false
  }
}

function managePlayers() {
  profileOpen.value = false
  playersOpen.value = true
}
</script>

<template>
  <USlideover v-model:open="profileOpen" title="Profile">
    <template #body>
      <div v-if="me" class="space-y-6">
        <div class="flex items-center gap-3">
          <UAvatar :src="me.pictureUrl" :alt="me.name" size="xl" referrerpolicy="no-referrer" />
          <div class="min-w-0">
            <p class="truncate font-semibold text-highlighted">{{ me.name }}</p>
            <p class="truncate text-sm text-muted">{{ me.email }}</p>
          </div>
        </div>

        <UFormField label="On the board I am" description="Links your account to your player, so you get notified about your matches.">
          <USelect
            :model-value="me.playerId ?? undefined"
            :items="playerItems"
            placeholder="Pick your player"
            class="w-full"
            size="lg"
            @update:model-value="setPlayer"
          />
        </UFormField>

        <div class="space-y-3">
          <p class="eyebrow">Notifications</p>
          <div class="flex items-center justify-between gap-3 rounded-xl border border-default p-3">
            <div>
              <p class="font-medium">Push on this device</p>
              <p class="text-xs text-muted">
                {{
                  push === 'unsupported'
                    ? 'Not supported here. On iPhone, add the app to your home screen first.'
                    : push === 'denied'
                      ? 'Blocked in browser settings.'
                      : push === 'on'
                        ? 'On'
                        : 'Off'
                }}
              </p>
            </div>
            <UButton
              :label="push === 'on' ? 'Turn off' : 'Turn on'"
              :color="push === 'on' ? 'neutral' : 'primary'"
              :variant="push === 'on' ? 'subtle' : 'solid'"
              :loading="busy"
              :disabled="push === 'unsupported' || push === 'denied'"
              @click="togglePush"
            />
          </div>
          <USwitch
            :model-value="me.notifyMatches"
            label="Comments and reactions on my matches"
            @update:model-value="setNotifyMatches"
          />
          <USwitch :model-value="me.notifyChat" label="Chat messages" @update:model-value="setNotifyChat" />
        </div>

        <div class="space-y-2 border-t border-default pt-4">
          <UButton block color="neutral" variant="subtle" icon="i-lucide-users" label="Manage players" @click="managePlayers" />
          <UButton block color="neutral" variant="ghost" icon="i-lucide-log-out" label="Sign out" to="/logout" external />
        </div>
      </div>
    </template>
  </USlideover>
</template>
