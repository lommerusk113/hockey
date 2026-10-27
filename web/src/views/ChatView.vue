<script setup lang="ts">
import { useToast } from '@nuxt/ui/composables'
import { useDocumentVisibility, useIntervalFn } from '@vueuse/core'
import { nextTick, onMounted, ref, watch } from 'vue'
import { api, type ChatMessage } from '../api'
import UserAvatar from '../components/UserAvatar.vue'
import { me, userById } from '../store'

const messages = ref<ChatMessage[]>([])
const draft = ref('')
const sending = ref(false)
const bottom = ref<HTMLElement>()
const toast = useToast()

const scrollDown = () => nextTick(() => bottom.value?.scrollIntoView({ block: 'end' }))

async function poll() {
  const last = messages.value.at(-1)?.id
  const fresh = await api.chat(last).catch(() => [])
  if (fresh.length) {
    messages.value.push(...fresh.filter((m) => !messages.value.some((x) => x.id === m.id)))
    scrollDown()
  }
}

onMounted(poll)
// ponytail: polling every 4s while visible; switch to SSE/WebSocket if the group grows large
const visibility = useDocumentVisibility()
const { pause, resume } = useIntervalFn(poll, 4000)
watch(visibility, (v) => (v === 'visible' ? (poll(), resume()) : pause()))

async function send() {
  if (!draft.value.trim()) return
  sending.value = true
  try {
    await api.sendChat(draft.value.trim())
    draft.value = ''
    await poll()
  } catch (e) {
    toast.add({ title: 'Message not sent', description: (e as Error).message, color: 'error' })
  } finally {
    sending.value = false
  }
}

const time = (iso: string) => new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
const day = (iso: string) => new Date(iso).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })
const newDay = (i: number) => i === 0 || new Date(messages.value[i - 1].createdAt).toDateString() !== new Date(messages.value[i].createdAt).toDateString()
// Consecutive messages from the same person share one avatar and name
const firstOfRun = (i: number) => newDay(i) || messages.value[i - 1].userId !== messages.value[i].userId
</script>

<template>
  <h1 class="text-2xl font-extrabold tracking-tight text-highlighted">Chat</h1>

  <UEmpty v-if="!messages.length" icon="i-lucide-messages-square" title="No messages yet" description="Say hi, or challenge someone to a rematch." />

  <ol class="space-y-1 pb-20 sm:pb-24">
    <li v-for="(m, i) in messages" :key="m.id">
      <p v-if="newDay(i)" class="my-4 text-center text-xs text-muted">{{ day(m.createdAt) }}</p>
      <div class="flex items-end gap-2" :class="{ 'flex-row-reverse': m.userId === me?.id, 'mt-3': firstOfRun(i) && !newDay(i) }">
        <div class="w-8 shrink-0">
          <UserAvatar v-if="m.userId !== me?.id && firstOfRun(i)" :id="m.userId" size="md" />
        </div>
        <div class="max-w-[75%]" :class="m.userId === me?.id ? 'items-end text-right' : ''">
          <p v-if="m.userId !== me?.id && firstOfRun(i)" class="mb-0.5 ml-1 text-xs font-medium text-muted">{{ userById(m.userId)?.name ?? '?' }}</p>
          <div
            class="inline-block rounded-2xl px-3 py-2 text-left text-sm break-words whitespace-pre-line"
            :class="m.userId === me?.id ? 'rounded-br-md bg-primary text-inverted' : 'rounded-bl-md bg-elevated text-highlighted'"
          >
            {{ m.body }}
            <span class="ml-1.5 align-bottom text-[10px] opacity-70">{{ time(m.createdAt) }}</span>
          </div>
        </div>
      </div>
    </li>
  </ol>
  <div ref="bottom" />

  <form
    class="fixed inset-x-0 bottom-[calc(4.25rem+env(safe-area-inset-bottom))] z-10 border-t border-default bg-default/95 px-4 py-2 backdrop-blur sm:bottom-0 sm:py-3"
    @submit.prevent="send"
  >
    <div class="mx-auto flex max-w-6xl gap-2">
      <UInput v-model="draft" placeholder="Message" class="flex-1" size="lg" maxlength="2000" aria-label="Message" autocomplete="off" />
      <UButton type="submit" icon="i-lucide-send" size="lg" aria-label="Send" :loading="sending" :disabled="!draft.trim()" />
    </div>
  </form>
</template>
