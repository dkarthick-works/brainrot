import { useState } from 'react'
import type { ApiKey } from './mock'
import SwipeRow from './SwipeRow'
import Loading, { LOADING } from './Loading'
import ErrorState, { LOAD_ERROR } from './ErrorState'
import { Icon, SEND, TRASH, useModal } from './ui'

type Props = { apiKeys: ApiKey[]; setApiKeys: (fn: (k: ApiKey[]) => ApiKey[]) => void }

// Placeholder host: the real app fills in its own origin
const MCP_URL = 'https://<your-host>/mcp'
const CLAUDE_CMD = `claude mcp add --transport http brainrot ${MCP_URL} --header "Authorization: Bearer <your key>"`

function ago(iso: string | null) {
  if (!iso) return 'Never used'
  const mins = Math.round((Date.now() - new Date(iso).getTime()) / 6e4)
  if (mins < 60) return `Used ${mins}m ago`
  if (mins < 60 * 24) return `Used ${Math.round(mins / 60)}h ago`
  return `Used ${Math.round(mins / 1440)}d ago`
}
const day = (iso: string) => new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })

// Mock of the server's key format: "fc_" + 32 random bytes, base64url
function mockKey() {
  const bytes = crypto.getRandomValues(new Uint8Array(32))
  return 'fc_' + btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function CopyButton({ text, label = 'Copy' }: { text: string; label?: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      type="button"
      className="copy-btn"
      onClick={() => {
        navigator.clipboard?.writeText(text).catch(() => {})
        setCopied(true)
        setTimeout(() => setCopied(false), 1500)
      }}
    >
      {copied ? 'Copied' : label}
    </button>
  )
}

export default function AgentAccess({ apiKeys, setApiKeys }: Props) {
  const [name, setName] = useState('')
  const [openId, setOpenId] = useState<string | null>(null)
  const [toRevoke, setToRevoke] = useState<ApiKey | null>(null)
  const [created, setCreated] = useState<{ name: string; key: string } | null>(null)
  const revokeDialog = useModal(toRevoke != null)
  const createdDialog = useModal(created != null)
  const trimmed = name.trim()

  function create(e: React.FormEvent) {
    e.preventDefault()
    if (!trimmed) return
    const key = mockKey()
    setApiKeys(ks => [...ks, { id: crypto.randomUUID(), name: trimmed, prefix: key.slice(0, 8), createdAt: new Date().toISOString(), lastUsedAt: null }])
    setCreated({ name: trimmed, key })
    setName('')
  }

  return (
    <>
      <main className="screen">
        <header className="topbar">
          <h1>Agent access</h1>
        </header>

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
        {LOAD_ERROR ? (
          <ErrorState variant="list" kind={LOAD_ERROR} />
        ) : LOADING ? (
          <Loading variant="list" />
        ) : (
          <>
            {apiKeys.length === 0 && <p className="empty">No keys yet. Name one below to connect an agent.</p>}
            <ul className="list" hidden={apiKeys.length === 0}>
              {apiKeys.map(k => (
                <SwipeRow
                  key={k.id}
                  className="key-row"
                  open={openId === k.id}
                  onOpenChange={o => setOpenId(o ? k.id : null)}
                  onTap={() => setOpenId(null)}
                  actions={[{ label: `Revoke ${k.name}`, icon: TRASH, danger: true, onClick: () => setToRevoke(k) }]}
                >
                  <span className="card-text">
                    <span className="card-q">{k.name}</span>
                    <code className="key-prefix">{k.prefix}…</code>
                  </span>
                  <span className="key-meta">
                    <span>Created {day(k.createdAt)}</span>
                    <span>{ago(k.lastUsedAt)}</span>
                  </span>
                </SwipeRow>
              ))}
            </ul>
          </>
        )}
        <p className="section-note">Swipe a key left to revoke it.</p>
      </main>

      <form className="composer" onSubmit={create}>
        <div className="composer-bar">
          <input
            value={name}
            maxLength={100}
            placeholder="Name a new key…"
            aria-label="New API key name"
            enterKeyHint="send"
            onChange={e => setName(e.target.value)}
          />
          <button type="submit" className="send" aria-label="Create key" disabled={!trimmed}>
            <Icon>{SEND}</Icon>
          </button>
        </div>
      </form>

      {/* Shown once: the backend only stores a hash, so the full key can never be fetched again */}
      <dialog ref={createdDialog} className="sheet" onClose={() => setCreated(null)}>
        {created && (
          <>
            <h2>Key created</h2>
            <p>Copy <strong>{created.name}</strong> now. You won't be able to see it again.</p>
            <div className="code-box key-reveal">
              <code>{created.key}</code>
            </div>
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
              <button
                className="danger"
                onClick={() => {
                  setApiKeys(ks => ks.filter(k => k.id !== toRevoke.id))
                  setToRevoke(null)
                }}
              >
                Revoke
              </button>
            </div>
          </>
        )}
      </dialog>
    </>
  )
}
