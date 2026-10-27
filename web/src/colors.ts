// Player identity colors (CSS vars in style.css, light/dark stepped), assigned by player id order
// so a player keeps their color regardless of filtering.
export function playerColor(playerId: number, allIds: number[]): string {
  const index = [...allIds].sort((a, b) => a - b).indexOf(playerId)
  return index >= 0 && index < 8 ? `var(--player-${index + 1})` : 'var(--player-other)'
}

export const RESULT_COLORS = { W: 'var(--result-win)', D: 'var(--result-draw)', L: 'var(--result-loss)' }
