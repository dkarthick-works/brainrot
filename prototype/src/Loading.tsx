import { useEffect, useState } from 'react'

// Master list: every load picks one at random (app start, Play, Topics, Cards, API keys)
export const LOADING_MESSAGES = [
  'Warming up the neurons...',
  // Brain
  'Brain cells assembling...',
  'Neuron activation in progress...',
  'The brain is braining...',
  'Loading intelligence DLC...',
  'Acquiring brain wrinkles...',
  'Upgrading from NPC mode...',
  'Big brain energy incoming...',
  'One brain cell at a time...',
  'Downloading common sense...',
  'Cooking up some knowledge...',
  // Internet
  'Let him cook...',
  'Chat, are we cooked?',
  'Hold up, the neurons are cooking...',
  "It's giving intelligence...",
  'Thinking thoughts... allegedly.',
  'Brainrot.exe is running...',
  'Summoning the last brain cell...',
  'The neurons are having a meeting...',
  'Currently fighting the forgetting allegations...',
  '99% loading, 1% brain cells...',
  // Developer
  'Garbage collecting bad thoughts...',
  'Compiling brain cells...',
  'Cache miss. Fetching knowledge...',
  'SELECT * FROM brain...',
  'Rebuilding neural indexes...',
  'Waiting for the brain API...',
  'Resolving memory dependencies...',
  'Initializing cognitive services...',
  // Calm
  'A thought is forming...',
  'Making room for knowledge...',
  'Connecting the dots...',
  'Give it a thought...',
  "Something's clicking...",
  'Getting smarter...',
  'Almost a genius...',
  'Putting thoughts together...',
]

// Prototype only: ?loading in the URL holds every screen in its loading state
export const LOADING = new URLSearchParams(location.search).has('loading')

const pick = () => LOADING_MESSAGES[Math.floor(Math.random() * LOADING_MESSAGES.length)]

// Fast loads never show anything: the loader appears only after 300ms
function useDelayed(ms = 300) {
  const [show, setShow] = useState(false)
  useEffect(() => {
    const t = setTimeout(() => setShow(true), ms)
    return () => clearTimeout(t)
  }, [ms])
  return show
}

// variant "full": Play / app start (faint card stack + message)
// variant "list": Topics, Cards, API keys (message + placeholder rows shaped like the real ones)
export default function Loading({ variant, rows = 5 }: { variant: 'full' | 'list'; rows?: number }) {
  const [message] = useState(pick)
  const show = useDelayed()
  if (!show) return null

  if (variant === 'full') {
    return (
      <div className="loading-full" role="status">
        <div className="ghost-stack" aria-hidden="true">
          <span /><span />
        </div>
        <p className="loading-msg">{message}</p>
      </div>
    )
  }

  return (
    <div className="loading-list" role="status">
      <p className="loading-msg small">{message}</p>
      <ul className="list" aria-hidden="true">
        {Array.from({ length: rows }, (_, i) => (
          <li key={i} className="row skeleton-row">
            {/* varied widths so it reads as content, not a grid */}
            <span className="bar" style={{ width: `${[52, 38, 64, 45, 58, 34][i % 6]}%` }} />
            <span className="bar short" />
          </li>
        ))}
      </ul>
    </div>
  )
}
