<script setup lang="ts">
import type { Match } from '../api'
import { computed } from 'vue'
import { activity, playerName } from '../store'
import PlayerAvatar from './PlayerAvatar.vue'

const props = defineProps<{ match: Match }>()
defineEmits<{ select: [Match] }>()

// Compact activity line: the most used reaction and the comment count
const summary = computed(() => {
  const { comments, reactions } = activity.value(props.match.id)
  const counts = new Map<string, number>()
  reactions.forEach((r) => counts.set(r.emoji, (counts.get(r.emoji) ?? 0) + 1))
  const top = [...counts].sort((a, b) => b[1] - a[1])[0]
  return [top && `${top[0]}${reactions.length}`, comments.length && `💬${comments.length}`].filter(Boolean).join(' ')
})

const shortDate = (iso: string) => new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
</script>

<template>
  <button
    type="button"
    class="grid w-full grid-cols-[1fr_auto_1fr] items-center gap-2 rounded-lg px-2 py-2.5 text-sm transition hover:bg-elevated active:scale-[.99] active:bg-elevated/70 focus-visible:outline-2 focus-visible:outline-primary"
    @click="$emit('select', match)"
  >
    <span class="flex min-w-0 items-center justify-end gap-2 text-right">
      <span class="truncate" :class="match.homeGoals > match.awayGoals ? 'font-semibold text-highlighted' : 'text-muted'">
        {{ playerName(match.homePlayerId) }}
      </span>
      <PlayerAvatar :id="match.homePlayerId" size="xs" />
    </span>
    <span class="flex flex-col items-center">
      <span class="display rounded-md bg-elevated px-2 text-lg text-highlighted">{{ match.homeGoals }}–{{ match.awayGoals }}</span>
      <span class="text-xs text-muted">{{ shortDate(match.playedAt) }}</span>
      <span v-if="summary" class="text-[11px] leading-tight tabular-nums">{{ summary }}</span>
    </span>
    <span class="flex min-w-0 items-center gap-2">
      <PlayerAvatar :id="match.awayPlayerId" size="xs" />
      <span class="truncate" :class="match.awayGoals > match.homeGoals ? 'font-semibold text-highlighted' : 'text-muted'">
        {{ playerName(match.awayPlayerId) }}
      </span>
    </span>
  </button>
</template>
