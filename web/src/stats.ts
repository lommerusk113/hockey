import type { Match } from './api'

// ponytail: stats are computed client-side from the full match list; move to SQL if it grows past ~50k matches

export type Period = string // 'all' | '2026' | '2026-Q3'

const year = (m: Match) => new Date(m.playedAt).getFullYear()
const quarter = (m: Match) => `${year(m)}-Q${Math.floor(new Date(m.playedAt).getMonth() / 3) + 1}`
const month = (m: Match) => `${year(m)}-${String(new Date(m.playedAt).getMonth() + 1).padStart(2, '0')}`

export function periods(matches: Match[]): Period[] {
  const years = new Set(matches.map((m) => String(year(m))))
  const quarters = new Set(matches.map(quarter))
  const desc = (s: Set<string>) => [...s].sort().reverse()
  return ['all', ...desc(years), ...desc(quarters)]
}

export function inPeriod(m: Match, period: Period): boolean {
  if (period === 'all') {
    return true
  }
  return period.includes('Q') ? quarter(m) === period : String(year(m)) === period
}

export interface Row {
  playerId: number
  played: number
  wins: number
  draws: number
  losses: number
  goalsFor: number
  goalsAgainst: number
  cleanSheets: number
  longestWinStreak: number
}

/** Per-player perspective of a match: [playerId, goals scored, goals conceded]. */
function sides(m: Match): [number, number, number][] {
  return [
    [m.homePlayerId, m.homeGoals, m.awayGoals],
    [m.awayPlayerId, m.awayGoals, m.homeGoals],
  ]
}

const chronological = (matches: Match[]) =>
  [...matches].sort((a, b) => a.playedAt.localeCompare(b.playedAt) || a.id - b.id)

export function standings(matches: Match[]): Row[] {
  const rows = new Map<number, Row & { streak: number }>()
  for (const m of chronological(matches)) {
    for (const [id, gf, ga] of sides(m)) {
      const r = rows.get(id) ?? {
        playerId: id, played: 0, wins: 0, draws: 0, losses: 0,
        goalsFor: 0, goalsAgainst: 0, cleanSheets: 0, longestWinStreak: 0, streak: 0,
      }
      r.played++
      r.goalsFor += gf
      r.goalsAgainst += ga
      if (ga === 0) r.cleanSheets++
      if (gf > ga) {
        r.wins++
        r.streak++
        r.longestWinStreak = Math.max(r.longestWinStreak, r.streak)
      } else {
        if (gf === ga) r.draws++
        else r.losses++
        r.streak = 0
      }
      rows.set(id, r)
    }
  }
  return [...rows.values()]
    .map(({ streak: _, ...row }) => row)
    .sort((a, b) => winPct(b) - winPct(a) || (b.goalsFor - b.goalsAgainst) - (a.goalsFor - a.goalsAgainst))
}

export const winPct = (r: Row) => (r.played ? r.wins / r.played : 0)
export const perGame = (n: number, r: Row) => (r.played ? n / r.played : 0)

export interface Leader {
  title: string
  playerIds: number[]
  value: string
  match?: Match
}

function best(rows: Row[], title: string, score: (r: Row) => number, format: (v: number) => string, lowest = false): Leader | null {
  if (!rows.length) {
    return null
  }
  const values = rows.map(score)
  const top = lowest ? Math.min(...values) : Math.max(...values)
  return { title, playerIds: rows.filter((_, i) => values[i] === top).map((r) => r.playerId), value: format(top) }
}

export function leaders(matches: Match[]): Leader[] {
  const rows = standings(matches)
  const int = (v: number) => String(v)
  const avg = (v: number) => v.toFixed(2)
  const pct = (v: number) => `${Math.round(v * 100)}%`
  const biggestWin = [...matches].sort((a, b) => Math.abs(b.homeGoals - b.awayGoals) - Math.abs(a.homeGoals - a.awayGoals))[0]
  const highestScoring = [...matches].sort((a, b) => b.homeGoals + b.awayGoals - (a.homeGoals + a.awayGoals))[0]
  return [
    best(rows, 'Most wins', (r) => r.wins, int),
    best(rows, 'Best win %', winPct, pct),
    best(rows, 'Most goals', (r) => r.goalsFor, int),
    best(rows, 'Most goals against', (r) => r.goalsAgainst, int),
    best(rows, 'Best attack (goals/game)', (r) => perGame(r.goalsFor, r), avg),
    best(rows, 'Best defence (conceded/game)', (r) => perGame(r.goalsAgainst, r), avg, true),
    best(rows, 'Most clean sheets', (r) => r.cleanSheets, int),
    best(rows, 'Longest win streak', (r) => r.longestWinStreak, int),
    biggestWin && { title: 'Biggest win', playerIds: [], value: `${biggestWin.homeGoals}–${biggestWin.awayGoals}`, match: biggestWin },
    highestScoring && { title: 'Highest scoring match', playerIds: [], value: `${highestScoring.homeGoals}–${highestScoring.awayGoals}`, match: highestScoring },
  ].filter((l): l is Leader => !!l)
}

/** Wins per player per bucket: quarters for all-time, months within a year or quarter. */
export function winsOverTime(matches: Match[], period: Period) {
  const bucket = period === 'all' ? quarter : month
  const labels = [...new Set(matches.map(bucket))].sort()
  const wins = new Map<number, number[]>()
  for (const m of matches) {
    for (const [id, gf, ga] of sides(m)) {
      const series = wins.get(id) ?? labels.map(() => 0)
      if (gf > ga) series[labels.indexOf(bucket(m))]++
      wins.set(id, series)
    }
  }
  return { labels, wins }
}

export type Result = 'W' | 'D' | 'L'

/** Results for one player, newest first. */
export function recentForm(matches: Match[], playerId: number, count = 5): Result[] {
  return chronological(matches)
    .reverse()
    .flatMap((m) => sides(m).filter(([id]) => id === playerId))
    .slice(0, count)
    .map(([, gf, ga]) => (gf > ga ? 'W' : gf === ga ? 'D' : 'L'))
}

export function headToHead(matches: Match[], a: number, b: number) {
  const games = chronological(
    matches.filter((m) => [m.homePlayerId, m.awayPlayerId].includes(a) && [m.homePlayerId, m.awayPlayerId].includes(b)),
  )
  const result = { games, winsA: 0, winsB: 0, draws: 0, goalsA: 0, goalsB: 0, cumulativeA: [] as number[], cumulativeB: [] as number[] }
  for (const m of games) {
    const ga = m.homePlayerId === a ? m.homeGoals : m.awayGoals
    const gb = m.homePlayerId === a ? m.awayGoals : m.homeGoals
    result.goalsA += ga
    result.goalsB += gb
    if (ga > gb) result.winsA++
    else if (gb > ga) result.winsB++
    else result.draws++
    result.cumulativeA.push(result.winsA)
    result.cumulativeB.push(result.winsB)
  }
  return result
}
