import { useEffect, useState, type Dispatch, type SetStateAction } from 'react'
import { FAIL_SAVES, SaveError } from './ErrorState'
import Play from './Play'
import Topics from './Topics'
import Cards from './Cards'
import Settings, { type Theme } from './Settings'
import AgentAccess from './AgentAccess'
import { allCards, apiKeys as mockKeys, topics as mockTopics } from './mock'

// Inline SVG paths (Lucide-style, 24x24 stroke icons) so no icon library is needed
const MENU = [
  { label: 'Topics', icon: <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /> },
  { label: 'Cards', icon: <><rect x="3" y="7" width="14" height="14" rx="2" /><path d="M7 3h12a2 2 0 0 1 2 2v12" /></> },
  {
    label: 'Settings',
    icon: <><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" /></>,
  },
]

type Screen = 'Play' | 'Topics' | 'Cards' | 'Settings' | 'AgentAccess'

export default function App() {
  const [screen, setScreen] = useState<Screen>('Play')
  const [menuOpen, setMenuOpen] = useState(false)
  // Lives here so topics added on the Topics screen survive navigating away.
  const [topics, setTopicsRaw] = useState(mockTopics)
  // ?fresh in the URL starts with no cards, to preview the brand-new-user state
  const [cards, setCardsRaw] = useState(() => (new URLSearchParams(location.search).has('fresh') ? [] : allCards))
  const [cardsTopic, setCardsTopic] = useState<string | null>(null) // Cards screen filter, null = All
  const [apiKeys, setApiKeysRaw] = useState(mockKeys)

  // Every add / edit / delete goes through here. With ?failsave it "fails": nothing changes
  // (the real app would undo its instant update) and the banner offers Try again.
  const [retrySave, setRetrySave] = useState<(() => void) | null>(null)
  function saving<T>(set: Dispatch<SetStateAction<T>>) {
    return (fn: (v: T) => T) => {
      if (!FAIL_SAVES) return set(fn)
      setRetrySave(() => () => {
        set(fn)
        setRetrySave(null)
      })
    }
  }
  const setTopics = saving(setTopicsRaw)
  const setCards = saving(setCardsRaw)
  const setApiKeys = saving(setApiKeysRaw)

  // The banner clears itself after 8s
  useEffect(() => {
    if (!retrySave) return
    const t = setTimeout(() => setRetrySave(null), 8000)
    return () => clearTimeout(t)
  }, [retrySave])
  const [theme, setTheme] = useState<Theme>('system')

  // "system" follows the phone; light/dark force it via data-theme on <html>
  useEffect(() => {
    if (theme === 'system') delete document.documentElement.dataset.theme
    else document.documentElement.dataset.theme = theme
  }, [theme])

  const go = (s: string) => {
    if (s === 'Play' || s === 'Topics' || s === 'Cards' || s === 'Settings') setScreen(s)
    if (s === 'Cards') setCardsTopic(null)
    setMenuOpen(false)
  }

  // Play shows the two-line pill; other screens show a back arrow that opens the drawer.
  // Sub-screens (Agent access) use the arrow as a real back button to their parent.
  const showArrow = screen !== 'Play' && !menuOpen
  const isSubScreen = screen === 'AgentAccess' && !menuOpen
  const drawerScreen = screen === 'AgentAccess' ? 'Settings' : screen

  return (
    <div className="play">
      <button
        className={`menu-toggle ${menuOpen ? 'open' : ''}`}
        aria-label={isSubScreen ? 'Back to Settings' : menuOpen ? 'Close menu' : 'Open menu'}
        aria-expanded={isSubScreen ? undefined : menuOpen}
        onClick={() => (isSubScreen ? setScreen('Settings') : setMenuOpen(o => !o))}
      >
        {showArrow ? (
          <svg className="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M19 12H5M12 19l-7-7 7-7" /></svg>
        ) : (
          <><span /><span /></>
        )}
      </button>

      {screen === 'Play' && <Play menuOpen={menuOpen} cards={cards} onGo={setScreen} />}
      {screen === 'Topics' && (
        <Topics
          topics={topics}
          setTopics={setTopics}
          onOpen={id => {
            setCardsTopic(id)
            setScreen('Cards')
          }}
        />
      )}
      {screen === 'Cards' && (
        <Cards cards={cards} setCards={setCards} topics={topics} topicId={cardsTopic} setTopicId={setCardsTopic} />
      )}
      {screen === 'Settings' && (
        <Settings theme={theme} setTheme={setTheme} apiKeys={apiKeys} onAgentAccess={() => setScreen('AgentAccess')} />
      )}
      {screen === 'AgentAccess' && <AgentAccess apiKeys={apiKeys} setApiKeys={setApiKeys} />}

      {retrySave && <SaveError onRetry={retrySave} onDismiss={() => setRetrySave(null)} />}

      <div className={`scrim ${menuOpen ? 'show' : ''}`} onClick={() => setMenuOpen(false)} />
      <nav className={`drawer ${menuOpen ? 'open' : ''}`} aria-hidden={!menuOpen}>
        {/* Wordmark doubles as "home": the drawer has no Play entry */}
        <button className="brand" tabIndex={menuOpen ? 0 : -1} onClick={() => go('Play')}>brainrot.</button>
        {MENU.map(m => (
          <button
            key={m.label}
            className={drawerScreen === m.label ? 'current' : ''}
            aria-current={drawerScreen === m.label ? 'page' : undefined}
            tabIndex={menuOpen ? 0 : -1}
            onClick={() => go(m.label)}
          >
            <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">{m.icon}</svg>
            {m.label}
          </button>
        ))}
      </nav>
    </div>
  )
}
