import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api, MCP_URL } from '../api'
import Composer from '../components/Composer'
import ErrorState from '../components/ErrorState'
import Loading from '../components/Loading'
import { useSave } from '../components/SaveError'
import SwipeRow from '../components/SwipeRow'
import { TRASH, useModal } from '../components/ui'
import { usedLabel } from '../logic'
import type { ApiKey } from '../types'

const KEYS = ['api-keys']
const CLAUDE_CMD = `claude mcp add --transport http brainrot ${MCP_URL} --header "Authorization: Bearer <your key>"`
const day = (iso: string) => new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })

function CopyButton({ text, label = 'Copy' }: { text: string; label?: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      type="button"
      className="copy-btn"
      onClick={() =>
        navigator.clipboard.writeText(text).then(() => {
          setCopied(true)
          setTimeout(() => setCopied(false), 1500)
        }, () => {})
      }
    >
      {copied ? 'Copied' : label}
    </button>
  )
}

export default function AgentAccess() {
  const keys = useQuery({ queryKey: KEYS, queryFn: api.apiKeys })
  const [name, setName] = useState('')
  const [openId, setOpenId] = useState<string | null>(null)
  const [toRevoke, setToRevoke] = useState<ApiKey | null>(null)
  const [created, setCreated] = useState<{ name: string; key: string } | null>(null)
  const revokeDialog = useModal(toRevoke != null)
  const createdDialog = useModal(created != null)
  const trimmed = name.trim()

  const create = useSave({
    fn: (k: ApiKey) => api.createKey(k.name),
    touches: [KEYS],
    apply: (k, qc) => qc.setQueryData<ApiKey[]>(KEYS, ks => ks && [...ks, k]),
    // The full key exists only in this response (the server keeps a hash), so it's shown once, now
    onSuccess: r => setCreated({ name: r.name, key: r.key }),
  })
  const revoke = useSave({
    fn: (id: string) => api.revokeKey(id),
    touches: [KEYS],
    apply: (id, qc) => qc.setQueryData<ApiKey[]>(KEYS, ks => ks && ks.filter(k => k.id !== id)),
  })

  const list = keys.data ?? []
  return (
    <>
      <main className="screen">
        <header className="topbar"><h1>Agent access</h1></header>

        <h2 className="section-label">Connect</h2>
        <div className="connect">
          <span className="connect-label">MCP endpoint</span>
          <div className="code-box">
            <code>{MCP_URL}</code>
            <CopyButton text={MCP_URL} />
          </div>
          <span className="connect-label">Claude Code</span>
          <div className="code-box">
            <code>{CLAUDE_CMD}</code>
            <CopyButton text={CLAUDE_CMD} />
          </div>
        </div>

        <h2 className="section-label">Keys</h2>
        {keys.isError ? (
          <ErrorState variant="list" error={keys.error} onRetry={() => keys.refetch()} />
        ) : keys.isPending ? (
          <Loading variant="list" />
        ) : (
          <>
            {list.length === 0 && <p className="empty">No keys yet. Name one below to connect an agent.</p>}
            <ul className="list" hidden={list.length === 0}>
              {list.map(k => (
                <SwipeRow
                  key={k.id}
                  className="key-row"
                  open={openId === k.id}
                  onOpenChange={o => setOpenId(o ? k.id : null)}
                  onTap={() => setOpenId(null)}
                  actions={k.id.startsWith('tmp-') ? [] : [{ label: `Revoke ${k.name}`, icon: TRASH, danger: true, onClick: () => setToRevoke(k) }]}
                >
                  <span className="card-text">
                    <span className="card-q">{k.name}</span>
                    <code className="key-prefix">{k.prefix}…</code>
                  </span>
                  <span className="key-meta">
                    <span>Created {day(k.createdAt)}</span>
                    <span>{usedLabel(k.lastUsedAt)}</span>
                  </span>
                </SwipeRow>
              ))}
            </ul>
            {list.length > 0 && <p className="section-note">Swipe a key left to revoke it.</p>}
          </>
        )}
      </main>

      <Composer
        value={name}
        onChange={setName}
        onSubmit={() => {
          create({ id: 'tmp-' + crypto.randomUUID(), name: trimmed, prefix: 'fc_', createdAt: new Date().toISOString(), lastUsedAt: null })
          setName('')
        }}
        canSend={trimmed !== ''}
        maxLength={100}
        placeholder="Name a new key…"
        label="New API key name"
        sendLabel="Create key"
      />

      <dialog ref={createdDialog} className="sheet" onClose={() => setCreated(null)}>
        {created && (
          <>
            <h2>Key created</h2>
            <p>Copy <strong>{created.name}</strong> now. You won't be able to see it again.</p>
            <div className="code-box key-reveal"><code>{created.key}</code></div>
            <div className="sheet-actions">
              <CopyButton text={created.key} label="Copy key" />
              <button className="primary" onClick={() => setCreated(null)}>Done</button>
            </div>
          </>
        )}
      </dialog>

      <dialog ref={revokeDialog} className="sheet" onClose={() => setToRevoke(null)}>
        {toRevoke && (
          <>
            <h2>Revoke {toRevoke.name}?</h2>
            <p>Agents using this key stop working immediately. This can't be undone.</p>
            <div className="sheet-actions">
              <button onClick={() => setToRevoke(null)}>Cancel</button>
              <button className="danger" onClick={() => { revoke(toRevoke.id); setToRevoke(null) }}>Revoke</button>
            </div>
          </>
        )}
      </dialog>
    </>
  )
}
