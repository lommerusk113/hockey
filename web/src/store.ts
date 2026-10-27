import { breakpointsTailwind, useBreakpoints } from '@vueuse/core'
import { computed, ref } from 'vue'
import { api, SignedOutError, type Match, type MatchComment, type MatchReaction, type Me, type Player, type User } from './api'

// ponytail: one shared reactive store; add Pinia if state grows beyond a few refs
export const me = ref<Me>()
export const signedOut = ref(false)
export const players = ref<Player[]>([])
export const matches = ref<Match[]>([])
export const users = ref<User[]>([])
export const comments = ref<MatchComment[]>([])
export const reactions = ref<MatchReaction[]>([])
export const loaded = ref(false)
export const loadError = ref('')
export const playerIds = computed(() => players.value.map((p) => p.id))
/** Players offered when recording matches */
export const activePlayers = computed(() => players.value.filter((p) => p.active))
export const playerShortName = computed(() => {
  const names = new Map(players.value.map((p) => [p.id, p.shortName]))
  return (id: number) => names.get(id) ?? '?'
})
export const playerName = computed(() => {
  const names = new Map(players.value.map((p) => [p.id, p.name]))
  return (id: number) => names.get(id) ?? '?'
})
export const userById = computed(() => {
  const byId = new Map(users.value.map((u) => [u.id, u]))
  return (id: number) => byId.get(id)
})

/** Comments and reactions per match id. */
export const activity = computed(() => {
  const byMatch = new Map<number, { comments: MatchComment[]; reactions: MatchReaction[] }>()
  const entry = (id: number) => byMatch.get(id) ?? byMatch.set(id, { comments: [], reactions: [] }).get(id)!
  comments.value.forEach((c) => entry(c.matchId).comments.push(c))
  reactions.value.forEach((r) => entry(r.matchId).reactions.push(r))
  return (matchId: number) => byMatch.get(matchId) ?? { comments: [], reactions: [] }
})

// UI state shared between the shell and views
export const addMatchOpen = ref(false)
/** Set before opening the add-match sheet to edit an existing match instead. */
export const editingMatch = ref<Match>()
export const playersOpen = ref(false)
export const profileOpen = ref(false)
export const chatOpen = ref(false)
/** The match shown in the global match sheet. */
export const openMatchId = ref<number>()
export const isDesktop = useBreakpoints(breakpointsTailwind).greaterOrEqual('sm')

export async function refresh() {
  try {
    ;[me.value, players.value, matches.value, users.value] = await Promise.all([api.me(), api.players(), api.matches(), api.users()])
    await refreshSocial()
    signedOut.value = false
    loadError.value = ''
  } catch (e) {
    if (e instanceof SignedOutError) {
      signedOut.value = true
    } else {
      loadError.value = (e as Error).message
    }
  } finally {
    loaded.value = true
  }
}

export async function refreshSocial() {
  ;({ comments: comments.value, reactions: reactions.value } = await api.social())
}
