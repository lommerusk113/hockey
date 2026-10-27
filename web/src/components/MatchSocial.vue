<script setup lang="ts">
import { useToast } from '@nuxt/ui/composables'
import { computed, ref } from 'vue'
import { api, REACTIONS } from '../api'
import { activity, me, refreshSocial, userById } from '../store'
import UserAvatar from './UserAvatar.vue'

const props = defineProps<{ matchId: number }>()
const toast = useToast()
const draft = ref('')
const sending = ref(false)

const social = computed(() => activity.value(props.matchId))
const reactionChips = computed(() =>
  REACTIONS.map((emoji) => {
    const who = social.value.reactions.filter((r) => r.emoji === emoji)
    return {
      emoji,
      count: who.length,
      mine: who.some((r) => r.userId === me.value?.id),
      names: who.map((r) => userById.value(r.userId)?.name ?? '?').join(', '),
    }
  }),
)

async function attempt(action: () => Promise<unknown>) {
  try {
    await action()
    await refreshSocial()
  } catch (e) {
    toast.add({ title: 'Something went wrong', description: (e as Error).message, color: 'error' })
  }
}

const react = (emoji: string) => attempt(() => api.toggleReaction(props.matchId, emoji))
const removeComment = (id: number) => attempt(() => api.deleteComment(id))

async function send() {
  if (!draft.value.trim()) return
  sending.value = true
  await attempt(async () => {
    await api.addComment(props.matchId, draft.value.trim())
    draft.value = ''
  })
  sending.value = false
}

const time = (iso: string) => new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap justify-center gap-2" role="group" aria-label="Reactions">
      <UTooltip v-for="r in reactionChips" :key="r.emoji" :text="r.names || 'React'" :disabled="!r.count">
        <UButton
          :color="r.mine ? 'primary' : 'neutral'"
          :variant="r.mine ? 'soft' : 'outline'"
          size="lg"
          class="rounded-full"
          :aria-pressed="r.mine"
          :aria-label="`${r.emoji} ${r.count}`"
          @click="react(r.emoji)"
        >
          <span class="text-lg leading-none">{{ r.emoji }}</span>
          <span v-if="r.count" class="tabular-nums">{{ r.count }}</span>
        </UButton>
      </UTooltip>
    </div>

    <div>
      <p class="eyebrow mb-2">Comments</p>
      <p v-if="!social.comments.length" class="text-sm text-muted">No comments yet. Start the trash talk.</p>
      <ul class="space-y-3">
        <li v-for="c in social.comments" :key="c.id" class="flex gap-2.5">
          <UserAvatar :id="c.userId" size="sm" />
          <div class="min-w-0 flex-1">
            <p class="text-xs text-muted">
              <span class="font-semibold text-highlighted">{{ userById(c.userId)?.name ?? '?' }}</span> · {{ time(c.createdAt) }}
            </p>
            <p class="text-sm break-words whitespace-pre-line">{{ c.body }}</p>
          </div>
          <UButton
            v-if="c.userId === me?.id"
            icon="i-lucide-trash-2"
            size="xs"
            color="neutral"
            variant="ghost"
            aria-label="Delete comment"
            @click="removeComment(c.id)"
          />
        </li>
      </ul>
      <form class="mt-3 flex gap-2" @submit.prevent="send">
        <UInput v-model="draft" placeholder="Write a comment…" class="flex-1" maxlength="1000" aria-label="Comment" autocomplete="off" />
        <UButton type="submit" icon="i-lucide-send" aria-label="Send comment" :loading="sending" :disabled="!draft.trim()" />
      </form>
    </div>
  </div>
</template>
