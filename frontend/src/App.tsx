import { SignIn, useAuth, useClerk } from '@clerk/react'
import { useState } from 'react'
import { Route, Routes, useLocation, useNavigate } from 'react-router'
import { ApiError } from './api'
import ErrorState from './components/ErrorState'
import Loading from './components/Loading'
import { SaveErrorBanner } from './components/SaveError'
import { BACK, Icon } from './components/ui'
import AgentAccess from './screens/AgentAccess'
import Cards from './screens/Cards'
import Play from './screens/Play'
import Settings from './screens/Settings'
import Topics from './screens/Topics'

const MENU = [
  { label: 'Topics', path: '/topics', icon: <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /> },
  { label: 'Cards', path: '/cards', icon: <><rect x="3" y="7" width="14" height="14" rx="2" /><path d="M7 3h12a2 2 0 0 1 2 2v12" /></> },
  {
    label: 'Settings',
    path: '/settings',
    icon: <><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" /></>,
  },
]

export default function App() {
  const { isLoaded, isSignedIn } = useAuth()
  const { status } = useClerk()
  if (!isLoaded) {
    // Offline launch: the cached shell opens, but Clerk can't load without the network
    const failed = status === 'error' || !navigator.onLine
    return (
      <div className="app">
        <main className="stage">
          {failed ? <ErrorState variant="full" error={new ApiError(0, 'offline')} onRetry={() => location.reload()} /> : <Loading variant="full" />}
        </main>
      </div>
    )
  }
  // Signed out (or session expired): Clerk's sign-in, no error screen
  if (!isSignedIn) {
    return <div className="signin"><SignIn routing="hash" /></div>
  }
  return <Shell />
}

function Shell() {
  const [menuOpen, setMenuOpen] = useState(false)
  const { pathname } = useLocation()
  const navigate = useNavigate()

  const isPlay = pathname === '/'
  // Sub-screens use the arrow as a real back button to their parent; other screens' arrow opens the drawer
  const isSubScreen = pathname === '/settings/agents' && !menuOpen
  const current = MENU.find(m => pathname.startsWith(m.path))?.label

  const go = (path: string) => {
    navigate(path)
    setMenuOpen(false)
  }

  return (
    <div className="app">
      <button
        className={`menu-toggle ${menuOpen ? 'open' : ''}`}
        aria-label={isSubScreen ? 'Back to Settings' : menuOpen ? 'Close menu' : 'Open menu'}
        aria-expanded={isSubScreen ? undefined : menuOpen}
        onClick={() => (isSubScreen ? navigate('/settings') : setMenuOpen(o => !o))}
      >
        {!isPlay && !menuOpen ? <Icon>{BACK}</Icon> : <><span /><span /></>}
      </button>

      <Routes>
        <Route path="/" element={<Play menuOpen={menuOpen} />} />
        <Route path="/topics" element={<Topics />} />
        <Route path="/cards" element={<Cards />} />
        <Route path="/settings" element={<Settings />} />
        <Route path="/settings/agents" element={<AgentAccess />} />
        <Route path="*" element={<Play menuOpen={menuOpen} />} />
      </Routes>

      <SaveErrorBanner />

      <div className={`scrim ${menuOpen ? 'show' : ''}`} onClick={() => setMenuOpen(false)} />
      <nav className={`drawer ${menuOpen ? 'open' : ''}`} aria-hidden={!menuOpen} inert={!menuOpen}>
        {/* Wordmark doubles as "home": the drawer has no Play entry */}
        <button className="brand" onClick={() => go('/')}>brainrot.</button>
        {MENU.map(m => (
          <button
            key={m.label}
            className={current === m.label ? 'current' : ''}
            aria-current={current === m.label ? 'page' : undefined}
            onClick={() => go(m.path)}
          >
            <Icon>{m.icon}</Icon>
            {m.label}
          </button>
        ))}
      </nav>
    </div>
  )
}
