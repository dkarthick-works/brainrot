// Prototype only: ?error=offline or ?error=server shows the load-failure state on every screen.
// ?failsave makes every add / edit / delete fail, to preview the "change undone" banner.
const params = new URLSearchParams(location.search)
export const LOAD_ERROR = params.get('error') as 'offline' | 'server' | null
export const FAIL_SAVES = params.has('failsave')

// One general-failure headline; the line under it says what actually happened, in plain words
const DETAIL = {
  offline: "Couldn't reach brainrot. Check your connection.",
  server: 'Something broke on our end. Try again in a moment.',
}

const retry = () => (location.href = location.pathname) // prototype: reload without ?error

export default function ErrorState({ variant, kind }: { variant: 'full' | 'list'; kind: 'offline' | 'server' }) {
  return (
    <div className={`error-state ${variant}`} role="alert">
      <h2>Brain.exe stopped working</h2>
      <p>{DETAIL[kind]}</p>
      <button className="primary" onClick={retry}>Try again</button>
    </div>
  )
}

// Bottom banner for a failed save: the change was already undone, Try again re-sends it
export function SaveError({ onRetry, onDismiss }: { onRetry: () => void; onDismiss: () => void }) {
  return (
    <div className="save-error" role="alert">
      <span>Couldn't save. Your change was undone.</span>
      <button onClick={onRetry}>Try again</button>
      <button className="dismiss" aria-label="Dismiss" onClick={onDismiss}>
        <svg className="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12" /></svg>
      </button>
    </div>
  )
}
