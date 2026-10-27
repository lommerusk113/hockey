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

const int = (v: number) => String(v)
const avg = (v: number) => v.toFixed(2)
const signed = (v: number) => `${v > 0 ? '+' : ''}${v.toFixed(2)}`
const pct = (v: number) => `${Math.round(v * 100)}%`

/** A per-player stat that can be ranked. Per-game stats only rank players with enough games. */
export interface Metric {
  key: string
  title: string
  /** Fits a phone-width stat card */
  short?: string
  description: string
  icon: string
  value: (r: Row) => number
  format: (v: number) => string
  lowerIsBetter?: boolean
  perGame?: boolean
}

export const METRICS: Metric[] = [
  { key: 'winRate', title: 'Win rate', description: 'Share of games won', icon: 'i-lucide-trophy', value: winPct, format: pct, perGame: true },
  { key: 'goalsPerGame', title: 'Goals per game', short: 'Goals / game', description: 'Goals scored per game', icon: 'i-lucide-goal', value: (r) => perGame(r.goalsFor, r), format: avg, perGame: true },
  {
    key: 'concededPerGame',
    title: 'Best defence',
    description: 'Goals conceded per game, fewer is better',
    icon: 'i-lucide-shield',
    value: (r) => perGame(r.goalsAgainst, r),
    format: avg,
    lowerIsBetter: true,
    perGame: true,
  },
  {
    key: 'goalDiffPerGame',
    title: 'Goal difference per game',
    short: 'Goal diff / game',
    description: 'Goals scored minus conceded, per game',
    icon: 'i-lucide-scale',
    value: (r) => perGame(r.goalsFor - r.goalsAgainst, r),
    format: signed,
    perGame: true,
  },
  { key: 'cleanSheetRate', title: 'Clean sheet rate', description: 'Share of games without conceding', icon: 'i-lucide-lock', value: (r) => perGame(r.cleanSheets, r), format: pct, perGame: true },
  { key: 'wins', title: 'Most wins', description: 'Games won', icon: 'i-lucide-medal', value: (r) => r.wins, format: int },
  { key: 'goals', title: 'Most goals', description: 'Goals scored', icon: 'i-lucide-target', value: (r) => r.goalsFor, format: int },
  { key: 'conceded', title: 'Most goals against', description: 'Goals conceded', icon: 'i-lucide-shield-off', value: (r) => r.goalsAgainst, format: int },
  { key: 'streak', title: 'Longest win streak', description: 'Most wins in a row', icon: 'i-lucide-flame', value: (r) => r.longestWinStreak, format: int },
  { key: 'played', title: 'Most games played', description: 'Games played', icon: 'i-lucide-hash', value: (r) => r.played, format: int },
]

/** Games needed to rank in per-game stats: 3, or fewer if nobody has played that many yet. */
export function minGames(rows: Row[]): number {
  return Math.min(3, Math.max(0, ...rows.map((r) => r.played)))
}

export interface Ranked {
  row: Row
  value: number
  qualified: boolean
}

/** Every player ranked best first; for per-game stats, players below minGames come last. */
export function ranking(rows: Row[], metric: Metric): Ranked[] {
  const min = minGames(rows)
  const direction = metric.lowerIsBetter ? 1 : -1
  return rows
    .map((row) => ({ row, value: metric.value(row), qualified: !metric.perGame || row.played >= min }))
    .sort((a, b) => Number(b.qualified) - Number(a.qualified) || direction * (a.value - b.value) || b.row.played - a.row.played)
}

/** The qualified players sharing the best value. */
export function leaders(rows: Row[], metric: Metric): Ranked[] {
  const ranked = ranking(rows, metric).filter((r) => r.qualified)
  return ranked.filter((r) => r.value === ranked[0]?.value)
}

/** Match records: the biggest margin and the most goals in one game. */
export function matchRecords(matches: Match[]) {
  const margin = (m: Match) => Math.abs(m.homeGoals - m.awayGoals)
  const total = (m: Match) => m.homeGoals + m.awayGoals
  const top = (score: (m: Match) => number) => [...matches].sort((a, b) => score(b) - score(a))[0]
  return [
    { title: 'Biggest win', match: top(margin) },
    { title: 'Highest scoring match', match: top(total) },
  ].filter((r) => r.match)
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
