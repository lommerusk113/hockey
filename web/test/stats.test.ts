import assert from 'node:assert/strict'
import { test } from 'node:test'
import { headToHead, inPeriod, leaders, matchRecords, METRICS, minGames, periods, ranking, recentForm, standings } from '../src/stats.ts'

const m = (id: number, playedAt: string, home: number, away: number, hg: number, ag: number) =>
  ({ id, playedAt, homePlayerId: home, awayPlayerId: away, homeGoals: hg, awayGoals: ag })

const matches = [
  m(1, '2026-01-10T12:00:00Z', 1, 2, 3, 0),
  m(2, '2026-02-10T12:00:00Z', 2, 1, 2, 2),
  m(3, '2026-05-10T12:00:00Z', 1, 3, 5, 1),
  m(4, '2025-11-10T12:00:00Z', 3, 2, 1, 4),
]

test('standings', () => {
  const [first, ...rest] = standings(matches)
  assert.deepEqual(first, { playerId: 1, played: 3, wins: 2, draws: 1, losses: 0, goalsFor: 10, goalsAgainst: 3, cleanSheets: 1, longestWinStreak: 1 })
  assert.equal(rest.find((r) => r.playerId === 3)!.losses, 2)
})

test('periods and filtering', () => {
  assert.deepEqual(periods(matches), ['all', '2026', '2025', '2026-Q2', '2026-Q1', '2025-Q4'])
  assert.equal(matches.filter((x) => inPeriod(x, '2026-Q1')).length, 2)
  assert.equal(matches.filter((x) => inPeriod(x, '2025')).length, 1)
})

const metric = (key: string) => METRICS.find((m) => m.key === key)!

test('leaders and rankings', () => {
  const rows = standings(matches)
  assert.equal(minGames(rows), 3)
  assert.deepEqual(leaders(rows, metric('goals')).map((r) => r.row.playerId), [1])
  // lower is better for defence: player 1 conceded 3 in 3 games
  assert.deepEqual(leaders(rows, metric('concededPerGame')).map((r) => r.row.playerId), [1])
  // player 3 played only 2 games: unranked for per-game stats, listed last
  const winRate = ranking(rows, metric('winRate'))
  assert.equal(winRate.at(-1)!.row.playerId, 3)
  assert.equal(winRate.at(-1)!.qualified, false)
  assert.equal(matchRecords(matches)[0].match.id, 3)
})

test('head to head', () => {
  const h = headToHead(matches, 2, 1)
  assert.deepEqual([h.winsA, h.winsB, h.draws, h.goalsA, h.goalsB], [0, 1, 1, 2, 5])
  assert.deepEqual(h.cumulativeB, [1, 1])
})

test('recent form is newest first', () => {
  assert.deepEqual(recentForm(matches, 1), ['W', 'D', 'W'])
  assert.deepEqual(recentForm(matches, 2, 2), ['D', 'L'])
})
