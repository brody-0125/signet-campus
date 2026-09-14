import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'

type SharingStatus = { enabled: boolean; url: string | null }

export function CredentialSharing({ credential }: { credential: Record<string, unknown> }) {
  const [consent, setConsent] = useState(false)
  const id = String(credential.id).split('/').pop()!
  const path = `/credentials/${id}/sharing`
  const queryKey = ['credential-sharing', id]
  const client = useQueryClient()
  const sharing = useQuery({ queryKey, queryFn: ({ signal }) => api<SharingStatus>(path, undefined, signal) })
  const change = useMutation({ mutationFn: (enabled: boolean) => api<SharingStatus>(path, { enabled }),
    onSuccess: value => { client.setQueryData(queryKey, value); setConsent(false) } })
  return <section aria-label="Public sharing"><h3>Public sharing</h3>
    <p className="field-help">Publishing lets anyone with the link read and copy this credential, including its achievement, issuer, dates and recipient identifier. Private evidence and your account email are not included. Stopping sharing cannot erase copies already downloaded.</p>
    <details><summary>Review the credential before sharing</summary><pre className="credential-document">{JSON.stringify(credential, null, 2)}</pre></details>
    {sharing.isPending && <p>Loading sharing settings…</p>}
    {sharing.isError && <p role="alert" className="error">{sharing.error.message} <button className="nav-link" onClick={() => void sharing.refetch()}>Try again</button></p>}
    {sharing.data && (sharing.data.enabled ? <>
      <label>Share link<input readOnly value={sharing.data.url || ''} onFocus={event => event.currentTarget.select()}/></label>
      <div className="form-actions"><a className="button outline" href={sharing.data.url || ''} target="_blank" rel="noopener noreferrer">Open shared badge</a>
        <button className="button outline" disabled={change.isPending} onClick={() => change.mutate(false)}>Stop sharing</button></div>
    </> : <>
      <label className="consent-choice"><input type="checkbox" checked={consent} onChange={event => setConsent(event.target.checked)}/>I want to make this credential public.</label>
      <button className="button outline" disabled={!consent || change.isPending} onClick={() => change.mutate(true)}>Publish share link</button>
    </>)}
    {change.isError && <p role="alert" className="error">{change.error.message}</p>}
  </section>
}
