import assert from 'node:assert/strict'
import { test } from 'node:test'
import { headToHead, inPeriod, leaders, periods, recentForm, standings } from '../src/stats.ts'

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

test('leaders', () => {
  const byTitle = Object.fromEntries(leaders(matches).map((l) => [l.title, l]))
  assert.deepEqual(byTitle['Most goals'].playerIds, [1])
  assert.equal(byTitle['Biggest win'].value, '5–1')
  assert.deepEqual(byTitle['Best defence (conceded/game)'].playerIds, [1])
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
