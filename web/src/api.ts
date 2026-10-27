export interface Player {
  id: number
  name: string
  /** How the player is written on the results board, e.g. "Ni" */
  shortName: string
  /** Inactive players are hidden when recording matches but stay in the stats */
  active: boolean
}

export interface Match {
  id: number
  playedAt: string
  homePlayerId: number
  awayPlayerId: number
  homeGoals: number
  awayGoals: number
}

/** A match read from a photo; player ids are missing when the label matched no known player. */
/** The signed-in user */
export interface Me {
  id: number
  email: string
  name: string
  pictureUrl?: string
  playerId?: number
  notifyMatches: boolean
  notifyChat: boolean
}

export interface User {
  id: number
  name: string
  pictureUrl?: string
  playerId?: number
}

export interface MatchComment {
  id: number
  matchId: number
  userId: number
  body: string
  createdAt: string
}

export interface MatchReaction {
  id: number
  matchId: number
  userId: number
  emoji: string
}

export interface ChatMessage {
  id: number
  userId: number
  body: string
  createdAt: string
  reactions: { id: number; messageId: number; userId: number; emoji: string }[]
}

export const REACTIONS = ['👍', '👎', '🔥', '😂', '😱', '👏', '💀'] as const

/** Thrown for 401 so the app can show the sign-in screen. */
export class SignedOutError extends Error {}

export interface ScannedMatch {
  homeLabel: string
  awayLabel: string
  homePlayerId?: number
  awayPlayerId?: number
  homeGoals: number
  awayGoals: number
}

export type NewMatch = Omit<Match, 'id' | 'playedAt'> & { playedAt?: string }

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, init)
  if (res.status === 401) {
    throw new SignedOutError('Signed out')
  }
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new Error(body?._embedded?.errors?.[0]?.message ?? body?.message ?? `Request failed (${res.status})`)
  }
  return res.status === 204 ? (undefined as T) : res.json()
}

const json = (body: unknown, method = 'POST'): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

export const api = {
  players: () => request<Player[]>('/api/players'),
  addPlayer: (name: string, shortName: string) => request<Player>('/api/players', json({ name, shortName })),
  updatePlayer: (id: number, name: string, shortName: string) => request<Player>(`/api/players/${id}`, json({ name, shortName }, 'PUT')),
  setPlayerActive: (p: Player, active: boolean) => request<Player>(`/api/players/${p.id}`, json({ name: p.name, shortName: p.shortName, active }, 'PUT')),
  deletePlayer: (id: number) => request<void>(`/api/players/${id}`, { method: 'DELETE' }),
  matches: () => request<Match[]>('/api/matches'),
  addMatch: (m: NewMatch) => request<Match>('/api/matches', json(m)),
  addMatches: (batch: NewMatch[]) => request<Match[]>('/api/matches/batch', json(batch)),
  updateMatch: (id: number, m: Omit<Match, 'id'>) => request<Match>(`/api/matches/${id}`, json(m, 'PUT')),
  deleteMatches: (ids: number[]) => request<void>(`/api/matches?ids=${ids.join(',')}`, { method: 'DELETE' }),
  deleteMatch: (id: number) => request<void>(`/api/matches/${id}`, { method: 'DELETE' }),
  me: () => request<Me>('/api/me'),
  updateMe: (settings: Pick<Me, 'playerId' | 'notifyMatches' | 'notifyChat'>) => request<Me>('/api/me', json(settings, 'PUT')),
  users: () => request<User[]>('/api/users'),
  social: () => request<{ comments: MatchComment[]; reactions: MatchReaction[] }>('/api/social'),
  addComment: (matchId: number, body: string) => request<MatchComment>(`/api/matches/${matchId}/comments`, json({ body })),
  deleteComment: (id: number) => request<void>(`/api/comments/${id}`, { method: 'DELETE' }),
  toggleReaction: (matchId: number, emoji: string) => request<boolean>(`/api/matches/${matchId}/reactions`, json({ emoji })),
  chat: (after?: number) => request<ChatMessage[]>(after ? `/api/chat?after=${after}` : '/api/chat'),
  toggleChatReaction: (messageId: number, emoji: string) => request<boolean>(`/api/chat/${messageId}/reactions`, json({ emoji })),
  sendChat: (body: string) => request<ChatMessage>('/api/chat', json({ body })),
  pushKey: () => request<{ publicKey: string }>('/api/push/key'),
  subscribePush: (subscription: PushSubscriptionJSON) => request<void>('/api/push/subscriptions', json(subscription)),
  unsubscribePush: (endpoint: string) =>
    request<void>(`/api/push/subscriptions?endpoint=${encodeURIComponent(endpoint)}`, { method: 'DELETE' }),
  importMatches: (text: string) =>
    request<{ matches: ScannedMatch[]; skipped: string[] }>('/api/import', { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: text }),
  scan: (image: Blob) => {
    const form = new FormData()
    form.append('image', image, 'scoreboard.jpg')
    return request<ScannedMatch[]>('/api/scan', { method: 'POST', body: form })
  },
}

// Phone photos are several MB; the model only needs enough pixels to read digits.
export async function downscale(file: File, maxSide = 1280): Promise<Blob> {
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('Could not encode image'))), 'image/jpeg', 0.85),
  )
}
