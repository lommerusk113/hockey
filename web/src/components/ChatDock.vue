<script setup lang="ts">
import { useToast } from '@nuxt/ui/composables'
import { useDocumentVisibility, useIntervalFn } from '@vueuse/core'
import { computed, nextTick, ref, watch } from 'vue'
import { api, REACTIONS, type ChatMessage } from '../api'
import { chatOpen, me, userById } from '../store'
import UserAvatar from './UserAvatar.vue'

const SEEN_KEY = 'hockey.chat.seen'
const messages = ref<ChatMessage[]>([])
const unread = ref(0)
const draft = ref('')
const sending = ref(false)
const scroller = ref<HTMLElement>()
const toast = useToast()

const lastSeen = () => Number(safeStorage(() => localStorage.getItem(SEEN_KEY)) ?? 0)
const markSeen = () => {
  const last = messages.value.at(-1)?.id
  if (last) safeStorage(() => localStorage.setItem(SEEN_KEY, String(last)))
  unread.value = 0
}
function safeStorage<T>(action: () => T): T | undefined {
  try {
    return action()
  } catch {
    return undefined
  }
}

const scrollDown = () => nextTick(() => scroller.value?.scrollTo({ top: scroller.value.scrollHeight }))

// Open: reload the latest window (picks up new reactions too). Closed: only count newer messages for the badge.
// ponytail: polling (4s open, 20s closed); switch to SSE/WebSocket if the group grows large
async function poll() {
  if (chatOpen.value) {
    const before = messages.value.at(-1)?.id
    messages.value = await api.chat().catch(() => messages.value)
    if (messages.value.at(-1)?.id !== before) scrollDown()
    markSeen()
  } else {
    const fresh = await api.chat(lastSeen()).catch(() => [])
    unread.value = fresh.filter((m) => m.userId !== me.value?.id).length
  }
}

const visibility = useDocumentVisibility()
const { pause, resume } = useIntervalFn(poll, () => (chatOpen.value ? 4000 : 20000), { immediateCallback: true })
watch(visibility, (v) => (v === 'visible' ? resume() : pause()))
watch(chatOpen, (open) => open && poll().then(scrollDown))

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

async function react(message: ChatMessage, emoji: string) {
  await api.toggleChatReaction(message.id, emoji).catch(() => undefined)
  await poll()
}

const reactionChips = (m: ChatMessage) =>
  REACTIONS.map((emoji) => {
    const who = m.reactions.filter((r) => r.emoji === emoji)
    return { emoji, count: who.length, mine: who.some((r) => r.userId === me.value?.id), names: who.map((r) => userById.value(r.userId)?.name).join(', ') }
  }).filter((r) => r.count)

const time = (iso: string) => new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
const day = (iso: string) => new Date(iso).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })
const newDay = (i: number) => i === 0 || new Date(messages.value[i - 1].createdAt).toDateString() !== new Date(messages.value[i].createdAt).toDateString()
// Consecutive messages from the same person share one avatar and name
const firstOfRun = (i: number) => newDay(i) || messages.value[i - 1].userId !== messages.value[i].userId
const mine = (m: ChatMessage) => m.userId === me.value?.id
const participants = computed(() => [...new Set(messages.value.map((m) => m.userId))].slice(-4))
</script>

<template>
  <!-- Collapsed: a bubble in the corner, above the mobile tab bar -->
  <UButton
    v-if="!chatOpen"
    icon="i-lucide-message-circle"
    size="xl"
    class="fixed right-4 bottom-[calc(5.75rem+env(safe-area-inset-bottom))] z-30 size-14 justify-center rounded-full shadow-lg shadow-primary/30 sm:right-6 sm:bottom-6"
    :ui="{ leadingIcon: 'size-7' }"
    :aria-label="unread ? `Chat, ${unread} unread` : 'Chat'"
    @click="chatOpen = true"
  >
    <span
      v-if="unread"
      class="absolute -top-1 -right-1 grid min-w-5 place-items-center rounded-full bg-error px-1 text-xs font-bold text-white ring-2 ring-default"
    >
      {{ unread > 9 ? '9+' : unread }}
    </span>
  </UButton>

  <!-- Open: full screen on phones, a corner window on larger screens -->
  <section
    v-else
    role="dialog"
    aria-label="Chat"
    class="fixed inset-0 z-40 flex flex-col bg-default pt-[env(safe-area-inset-top)] sm:inset-auto sm:right-6 sm:bottom-6 sm:h-[34rem] sm:max-h-[calc(100dvh-6rem)] sm:w-96 sm:overflow-hidden sm:rounded-2xl sm:border sm:border-default sm:pt-0 sm:shadow-2xl"
    @keydown.esc="chatOpen = false"
  >
    <header class="flex items-center gap-2 border-b border-default px-3 py-2.5">
      <div class="flex -space-x-2">
        <UserAvatar v-for="id in participants" :key="id" :id="id" size="xs" class="ring-2 ring-default" />
      </div>
      <p class="flex-1 font-semibold text-highlighted">Chat</p>
      <UButton icon="i-lucide-chevron-down" color="neutral" variant="ghost" aria-label="Close chat" @click="chatOpen = false" />
    </header>

    <div ref="scroller" class="flex-1 overflow-y-auto px-3 py-2">
      <UEmpty v-if="!messages.length" icon="i-lucide-messages-square" title="No messages yet" description="Say hi, or challenge someone to a rematch." />
      <ol class="space-y-0.5">
        <li v-for="(m, i) in messages" :key="m.id">
          <p v-if="newDay(i)" class="my-3 text-center text-xs text-muted">{{ day(m.createdAt) }}</p>
          <div class="group flex items-end gap-2" :class="{ 'flex-row-reverse': mine(m), 'mt-3': firstOfRun(i) && !newDay(i) }">
            <div class="w-7 shrink-0">
              <UserAvatar v-if="firstOfRun(i)" :id="m.userId" size="sm" />
            </div>
            <div class="flex max-w-[75%] flex-col" :class="mine(m) ? 'items-end' : 'items-start'">
              <p v-if="firstOfRun(i) && !mine(m)" class="mb-0.5 ml-1 text-xs font-medium text-muted">{{ userById(m.userId)?.name ?? '?' }}</p>
              <div class="flex items-center gap-1" :class="{ 'flex-row-reverse': !mine(m) }">
                <UPopover :content="{ side: 'top' }">
                  <UButton
                    icon="i-lucide-smile-plus"
                    size="xs"
                    color="neutral"
                    variant="ghost"
                    class="opacity-60 group-hover:opacity-100 sm:opacity-0 sm:focus-visible:opacity-100"
                    aria-label="React"
                  />
                  <template #content>
                    <div class="flex gap-1 p-1">
                      <UButton v-for="e in REACTIONS" :key="e" color="neutral" variant="ghost" class="text-xl" :aria-label="`React ${e}`" @click="react(m, e)">
                        {{ e }}
                      </UButton>
                    </div>
                  </template>
                </UPopover>
                <div
                  class="rounded-2xl px-3 py-2 text-sm break-words whitespace-pre-line"
                  :class="mine(m) ? 'rounded-br-md bg-primary text-inverted' : 'rounded-bl-md bg-elevated text-highlighted'"
                >
                  {{ m.body }}
                  <span class="ml-1.5 align-bottom text-[10px] opacity-70">{{ time(m.createdAt) }}</span>
                </div>
              </div>
              <div v-if="m.reactions.length" class="-mt-1 flex gap-1" :class="mine(m) ? 'mr-7' : 'ml-1'">
                <UTooltip v-for="r in reactionChips(m)" :key="r.emoji" :text="r.names">
                  <button
                    type="button"
                    class="flex items-center gap-0.5 rounded-full border px-1.5 text-xs leading-5 tabular-nums"
                    :class="r.mine ? 'border-primary bg-primary/10' : 'border-default bg-default'"
                    :aria-pressed="r.mine"
                    :aria-label="`${r.emoji} ${r.count}`"
                    @click="react(m, r.emoji)"
                  >
                    {{ r.emoji }}<span v-if="r.count > 1">{{ r.count }}</span>
                  </button>
                </UTooltip>
              </div>
            </div>
          </div>
        </li>
      </ol>
    </div>

    <form class="flex gap-2 border-t border-default p-2 pb-[max(0.5rem,env(safe-area-inset-bottom))]" @submit.prevent="send">
      <UInput v-model="draft" placeholder="Message" class="flex-1" size="lg" maxlength="2000" aria-label="Message" autocomplete="off" />
      <UButton type="submit" icon="i-lucide-send" size="lg" aria-label="Send" :loading="sending" :disabled="!draft.trim()" />
    </form>
  </section>
</template>
