import { useMutation, useQuery } from '@tanstack/react-query'
import { api } from './api'

type PublicCredential = Record<string, unknown> & {
  id: string; name?: string; validFrom?: string; validUntil?: string; issuer?: { name?: string }
  credentialSubject?: { achievement?: { criteria?: { narrative?: string } } }
}

export function SharedCredential({ id }: { id: string }) {
  const credential = useQuery({ queryKey: ['shared-credential', id], staleTime: 0, gcTime: 0, retry: false,
    queryFn: ({ signal }) => api<PublicCredential>(`/shared/credentials/${encodeURIComponent(id)}`, undefined, signal) })
  const verification = useMutation({ mutationFn: () => api<{ valid: boolean; status: string }>(`/credentials/${encodeURIComponent(id)}/verify`, credential.data) })
  return <><a className="skip-link" href="#main">Skip to content</a>
    <header className="site-header"><div className="container header-inner"><a className="wordmark" href="/">signet campus</a></div></header>
    <main id="main" className="container workspace">
      {credential.isPending ? <p role="status">Loading shared credential…</p> : credential.isError ? <div role="alert"><h1>Credential unavailable</h1><p>{credential.error.message}</p></div> : credential.data && <article className="pathway-card">
        <p className="eyebrow">Shared achievement</p><h1>{credential.data.name || 'Open Badge credential'}</h1>
        <p>Issued by {credential.data.issuer?.name || 'the credential issuer'}</p>
        <p>{credential.data.credentialSubject?.achievement?.criteria?.narrative}</p>
        <dl><dt>Issued</dt><dd>{credential.data.validFrom || 'Not specified'}</dd><dt>Valid until</dt><dd>{credential.data.validUntil || 'Not specified'}</dd></dl>
        <p>Sharing does not prove that a badge is current. Verify its signature, validity period and revocation status.</p>
        <div className="form-actions"><button className="button primary" disabled={verification.isPending} onClick={() => verification.mutate()}>Verify credential</button>
          <a className="button outline" download="signet-campus-credential.json" href={`data:application/vc+ld+json;charset=utf-8,${encodeURIComponent(JSON.stringify(credential.data, null, 2))}`}>Download JSON</a></div>
        {verification.isError && <p className="error" role="alert">{verification.error.message}</p>}
        {verification.data && <p className="notice" role="status">{verification.data.valid ? 'Verified: authentic, current and not revoked.' : `Verification result: ${verification.data.status.replaceAll('_', ' ').toLowerCase()}.`}</p>}
        <details><summary>View signed credential</summary><pre className="credential-document">{JSON.stringify(credential.data, null, 2)}</pre></details>
      </article>}
      <button className="button outline" disabled={credential.isFetching} onClick={() => { verification.reset(); void credential.refetch() }}>Refresh shared credential</button>
    </main></>
}
