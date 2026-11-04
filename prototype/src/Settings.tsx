import type { ApiKey } from './mock'
import { user } from './mock'

export type Theme = 'system' | 'light' | 'dark'

type Props = {
  theme: Theme
  setTheme: (t: Theme) => void
  apiKeys: ApiKey[]
  onAgentAccess: () => void
}

const CHEVRON = (
  <svg className="icon chev" viewBox="0 0 24 24" aria-hidden="true"><path d="m9 6 6 6-6 6" /></svg>
)

export default function Settings({ theme, setTheme, apiKeys, onAgentAccess }: Props) {
  const initials = user.name.split(' ').map(w => w[0]).join('').slice(0, 2)

  return (
    <main className="screen">
      <header className="topbar">
        <h1>Settings</h1>
      </header>

      <h2 className="section-label">Account</h2>
      <ul className="list">
        <li className="row account">
          <span className="avatar" aria-hidden="true">{initials}</span>
          <span className="account-text">
            <strong>{user.name}</strong>
            <span>{user.email}</span>
          </span>
        </li>
        {/* Both handled by Clerk in the real app (UserProfile, signOut) */}
        <li><button className="row">Manage account {CHEVRON}</button></li>
        <li><button className="row danger-text">Sign out</button></li>
      </ul>

      <h2 className="section-label">Agent access</h2>
      <ul className="list">
        <li>
          <button className="row" onClick={onAgentAccess}>
            <span>API keys</span>
            <span className="row-end">
              <span className="row-count">{apiKeys.length} {apiKeys.length === 1 ? 'key' : 'keys'}</span>
              {CHEVRON}
            </span>
          </button>
        </li>
      </ul>
      <p className="section-note">Lets AI agents like Claude Code read and add cards for you over MCP.</p>

      <h2 className="section-label">Appearance</h2>
      <div className="segmented" role="radiogroup" aria-label="Theme">
        {(['system', 'light', 'dark'] as const).map(t => (
          <button key={t} role="radio" aria-checked={theme === t} className={theme === t ? 'selected' : ''} onClick={() => setTheme(t)}>
            {t[0].toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>

      <h2 className="section-label">About</h2>
      <ul className="list">
        <li className="row about">
          <span className="brand-mark">brainrot.</span>
          <span className="row-count">Prototype v0.1</span>
        </li>
      </ul>
    </main>
  )
}
