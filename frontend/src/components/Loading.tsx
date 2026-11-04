import { useEffect, useState } from 'react'

// Master list: every load picks one at random
const MESSAGES = [
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

const pick = () => MESSAGES[Math.floor(Math.random() * MESSAGES.length)]

/** full: app start / Play (faint card stack + message). list: Topics, Cards, API keys (message + placeholder rows). */
export default function Loading({ variant, rows = 5 }: { variant: 'full' | 'list'; rows?: number }) {
  const [message] = useState(pick)
  const [show, setShow] = useState(false)
  // Nothing for the first 300ms, so fast loads never flash a loader
  useEffect(() => {
    const t = setTimeout(() => setShow(true), 300)
    return () => clearTimeout(t)
  }, [])
  if (!show) return null

  if (variant === 'full') {
    return (
      <div className="loading-full" role="status">
        <div className="ghost-stack" aria-hidden="true"><span /><span /></div>
        <p className="loading-msg">{message}</p>
      </div>
    )
  }
  return (
    <div role="status">
      <p className="loading-msg small">{message}</p>
      <ul className="list" aria-hidden="true">
        {Array.from({ length: rows }, (_, i) => (
          <li key={i} className="row skeleton-row">
            <span className="bar" style={{ width: `${[52, 38, 64, 45, 58, 34][i % 6]}%` }} />
            <span className="bar short" />
          </li>
        ))}
      </ul>
    </div>
  )
}
