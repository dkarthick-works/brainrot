import { ApiError } from '../api'

/** A page couldn't load: one fixed headline, a plain line saying what happened, Try again. */
export default function ErrorState({ variant, error, onRetry }: { variant: 'full' | 'list'; error: unknown; onRetry: () => void }) {
  const offline = !navigator.onLine || (error instanceof ApiError && error.status === 0)
  return (
    <div className={`error-state ${variant}`} role="alert">
      <h2>Brain.exe stopped working</h2>
      <p>{offline ? "Couldn't reach brainrot. Check your connection." : 'Something broke on our end. Try again in a moment.'}</p>
      <button className="primary" onClick={onRetry}>Try again</button>
    </div>
  )
}
