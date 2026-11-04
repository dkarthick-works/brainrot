export type Theme = 'system' | 'light' | 'dark'

export function loadTheme(): Theme {
  try {
    const t = localStorage.getItem('theme')
    return t === 'light' || t === 'dark' ? t : 'system'
  } catch {
    return 'system' // storage blocked (private mode etc.)
  }
}

/** "system" follows the phone; light/dark force it via data-theme on <html> (tokens.css). */
export function applyTheme(t: Theme) {
  if (t === 'system') delete document.documentElement.dataset.theme
  else document.documentElement.dataset.theme = t
  try {
    localStorage.setItem('theme', t)
  } catch {
    /* not persisted; still applied for this visit */
  }
}
