import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api } from './api'

export function CredentialActions({ submissionId }: { submissionId: string }) {
  const [credential, setCredential] = useState<Record<string, unknown> | null>(null)
  const issue = useMutation({ mutationFn: () => api<Record<string, unknown>>(`/submissions/${submissionId}/credential`, {}), onSuccess: setCredential })
  const verification = useMutation({ mutationFn: () => {
    const id = String(credential?.id).split('/').pop()
    return api<{ status: string; valid: boolean }>(`/credentials/${id}/verify`, credential)
  } })
  return <div className="criteria"><h3>Your badge</h3>
    {credential ? <><p>Your signed credential is ready. Download it to keep your own copy.</p><div className="form-actions"><button className="button outline" onClick={() => verification.mutate()} disabled={verification.isPending}>Verify badge</button><a className="button primary" download="signet-campus-credential.json" href={`data:application/vc+ld+json;charset=utf-8,${encodeURIComponent(JSON.stringify(credential, null, 2))}`}>Download JSON</a></div></> : <><p>Issue your badge using the verified email address on your account.</p><div className="form-actions"><button className="button primary" onClick={() => issue.mutate()} disabled={issue.isPending}>{issue.isPending ? 'Issuing…' : 'Issue badge'}</button></div></>}
    {issue.isError && <p className="error" role="alert">{issue.error.message}</p>}
    {verification.isError && <p className="error" role="alert">{verification.error.message}</p>}
    {verification.data && <p className="notice" role="status">{verification.data.valid ? 'Verified: authentic, current and not revoked.' : `Verification result: ${verification.data.status.replaceAll('_', ' ').toLowerCase()}.`}</p>}
  </div>
}
