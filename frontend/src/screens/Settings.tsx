import { useClerk, useUser } from '@clerk/react'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../api'
import { CHEVRON_RIGHT, Icon } from '../components/ui'
import { plural } from '../logic'
import { applyTheme, loadTheme, type Theme } from '../theme'

const Chevron = () => <Icon className="chev">{CHEVRON_RIGHT}</Icon>

export default function Settings() {
  const navigate = useNavigate()
  const { user } = useUser()
  const clerk = useClerk()
  const keys = useQuery({ queryKey: ['api-keys'], queryFn: api.apiKeys })
  const [theme, setTheme] = useState(loadTheme)

  const name = user?.fullName || user?.username || ''
  const email = user?.primaryEmailAddress?.emailAddress ?? ''
  const initials = (name || email).split(/\s+/).map(w => w[0]).join('').slice(0, 2).toUpperCase()

  function pick(t: Theme) {
    setTheme(t)
    applyTheme(t)
  }

  return (
    <main className="screen">
      <header className="topbar"><h1>Settings</h1></header>

      <h2 className="section-label">Account</h2>
      <ul className="list">
        <li className="row account">
          <span className="avatar" aria-hidden="true">{initials}</span>
          <span className="account-text">
            <strong>{name || email}</strong>
            {name && <span>{email}</span>}
          </span>
        </li>
        <li><button className="row" onClick={() => clerk.openUserProfile()}>Manage account <Chevron /></button></li>
        <li><button className="row danger-text" onClick={() => clerk.signOut()}>Sign out</button></li>
      </ul>

      <h2 className="section-label">Agent access</h2>
      <ul className="list">
        <li>
          <button className="row" onClick={() => navigate('/settings/agents')}>
            <span>API keys</span>
            <span className="row-end">
              {keys.data && <span className="row-count">{plural(keys.data.length, 'key')}</span>}
              <Chevron />
            </span>
          </button>
        </li>
      </ul>
      <p className="section-note">Lets AI agents like Claude Code read and add cards for you over MCP.</p>

      <h2 className="section-label">Appearance</h2>
      <div className="segmented" role="radiogroup" aria-label="Theme">
        {(['system', 'light', 'dark'] as const).map(t => (
          <button key={t} role="radio" aria-checked={theme === t} className={theme === t ? 'selected' : ''} onClick={() => pick(t)}>
            {t[0].toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>

      <h2 className="section-label">About</h2>
      <ul className="list">
        <li className="row about">
          <span className="brand-mark">brainrot.</span>
          <span className="row-count">v{__APP_VERSION__}</span>
        </li>
      </ul>
    </main>
  )
}
