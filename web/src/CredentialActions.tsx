import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { api, ApiError } from './api'

export function CredentialActions({ source, canIssue = true }: { source: { type: 'submissions' | 'pathways'; id: string }; canIssue?: boolean }) {
  const [issued, setCredential] = useState<Record<string, unknown> | null>(null)
  const path = `/${source.type}/${source.id}/credential`
  const existing = useQuery({ queryKey: ['credential', source.type, source.id], enabled: source.type === 'pathways', retry: false,
    queryFn: async ({ signal }) => {
      try { return await api<Record<string, unknown>>(path, undefined, signal) }
      catch (error) { if (error instanceof ApiError && error.status === 404) return null; throw error }
    } })
  const credential = issued || existing.data
  const issue = useMutation({ mutationFn: () => api<Record<string, unknown>>(path, {}), onSuccess: setCredential })
  const verification = useMutation({ mutationFn: () => {
    const id = String(credential?.id).split('/').pop()
    return api<{ status: string; valid: boolean }>(`/credentials/${id}/verify`, credential)
  } })
  return <div className="criteria"><h3>{source.type === 'pathways' ? 'Your pathway award' : 'Your badge'}</h3>
    {source.type === 'pathways' && <p className="field-help">This award records completion at issuance. Later changes to individual badges do not cancel it.</p>}
    {source.type === 'pathways' && existing.isPending && <p role="status">Checking for your award…</p>}
    {existing.isError && <p className="error" role="alert">{existing.error.message} <button className="nav-link" onClick={() => void existing.refetch()}>Try again</button></p>}
    {credential ? <><p>Your signed credential is ready. Download it to keep your own copy.</p><div className="form-actions"><button className="button outline" onClick={() => verification.mutate()} disabled={verification.isPending}>Verify badge</button><a className="button primary" download="signet-campus-credential.json" href={`data:application/vc+ld+json;charset=utf-8,${encodeURIComponent(JSON.stringify(credential, null, 2))}`}>Download JSON</a></div></> : canIssue && (source.type === 'submissions' || existing.data === null) ? <><p>Issue your badge using the verified email address on your account.</p><div className="form-actions"><button className="button primary" onClick={() => issue.mutate()} disabled={issue.isPending}>{issue.isPending ? 'Issuing…' : 'Issue badge'}</button></div></> : !canIssue && existing.data === null && <p>Earn every required badge to claim this award.</p>}
    {issue.isError && <p className="error" role="alert">{issue.error.message}</p>}
    {verification.isError && <p className="error" role="alert">{verification.error.message}</p>}
    {verification.data && <p className="notice" role="status">{verification.data.valid ? 'Verified: authentic, current and not revoked.' : `Verification result: ${verification.data.status.replaceAll('_', ' ').toLowerCase()}.`}</p>}
  </div>
}
