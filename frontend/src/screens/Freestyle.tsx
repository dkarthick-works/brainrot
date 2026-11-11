import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { ApiError, topicsQuery } from '../api'
import ErrorState from '../components/ErrorState'
import FreestyleStack from '../components/FreestyleStack'
import Loading from '../components/Loading'
import { useFreestyleSession } from '../hooks/useFreestyleSession'

export default function Freestyle({ menuOpen }: { menuOpen: boolean }) {
  const navigate = useNavigate()
  const session = useFreestyleSession()
  const topics = useQuery(topicsQuery)
  const names = new Map(topics.data?.map(topic => [topic.id, topic.name]))
  const hasCards = session.queue.length > 0
  const failed = session.loadState === 'error'
  const loading = !session.firstPageLoaded || session.loadState === 'loading' || session.nextCursor !== null
  const offline = !navigator.onLine || (session.error instanceof ApiError && session.error.status === 0)

  return <div className="freestyle" inert={menuOpen}>
    <header className="freestyle-header">
      <span className="freestyle-menu-space" aria-hidden="true" />
      <h1>Freestyle</h1>
      <button className="freestyle-exit" onClick={() => navigate('/')}>Exit Freestyle</button>
    </header>
    <main className="stage freestyle-stage">
      {hasCards ? <>
        <FreestyleStack queue={session.queue.slice(0, 3)} topicName={id => names.get(id)}
          counter={session.advancedCount + 1} suspended={menuOpen} onAdvance={session.advance} />
        {failed && <div className="freestyle-read-error" role="alert">
          <p>{offline ? "Couldn't reach brainrot. Check your connection." : 'Something broke on our end. Try again in a moment.'}</p>
          <button onClick={session.retry}>Try again</button>
        </div>}
      </> : failed ? <ErrorState variant="full" error={session.error} onRetry={session.retry} />
        : loading ? <Loading variant="full" />
          : <div className="done">
            <h1>{session.advancedCount > 0 ? 'Freestyle complete' : 'No cards yet'}</h1>
            <div className="done-actions">
              {session.advancedCount > 0
                ? <button className="primary" onClick={session.restart}>Play again</button>
                : <button className="primary" onClick={() => navigate('/cards')}>Add cards</button>}
              <button onClick={() => navigate('/')}>Back to home</button>
            </div>
          </div>}
    </main>
  </div>
}
