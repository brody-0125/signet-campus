import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Achievement, type Submission } from './api'
import { signIn, useSession } from './auth'
import { Dialog } from './Dialog'
import { EvidenceForm } from './EvidenceForm'
import { useWorkspace } from './store'
import { CredentialActions } from './CredentialActions'

const statusLabel = { PENDING: 'Pending review', APPROVED: 'Approved', REJECTED: 'Changes requested' }
export function Submissions({ achievements }: { achievements: Achievement[] }) {
  const { authenticated, reviewer, ready } = useSession()
  const [offset, setOffset] = useState(0)
  const [selected, setSelected] = useState<Submission | null>(null)
  const [editing, setEditing] = useState(false)
  const query = useQuery({ queryKey: ['submissions', offset], queryFn: ({ signal }) => api<Submission[]>(`/submissions?offset=${offset}&limit=20`, undefined, signal), enabled: authenticated })
  const achievement = (id: string) => achievements.find(a => a.id === id) || { id, name: 'Achievement', criteria: 'Review the achievement criteria in Explore.' }
  const close = () => { setSelected(null); setEditing(false) }
  return <section className="container workspace" aria-labelledby="workspace-title">
    <div className="section-heading"><div><h1 id="workspace-title">{reviewer ? 'Review queue' : 'My submissions'}</h1><p className="section-intro">{reviewer ? 'Give thoughtful feedback. Recognize the work.' : 'Your work, and what comes next.'}</p></div>{authenticated && <button className="button outline" onClick={() => void query.refetch()} disabled={query.isFetching}>Refresh</button>}</div>
    {!authenticated ? <div className="empty-state"><h2>Your progress belongs here.</h2><p>Sign in to see your submissions and reviewer feedback.</p><button className="button primary" disabled={!ready} onClick={signIn}>Sign in to continue</button></div> : <>
      {query.isPending && <p role="status">Loading submissions…</p>}
      {query.isError && <p className="error" role="alert">{query.error.message}</p>}
      {query.data?.length === 0 && <div className="empty-state"><h2>{reviewer ? 'All caught up.' : 'Every achievement starts with evidence.'}</h2><p>{reviewer ? 'There are no submissions waiting on this page.' : 'Explore the criteria, then share a piece of work you are proud of.'}</p>{!reviewer && <button className="button primary" onClick={() => useWorkspace.getState().navigate('explore')}>Explore achievements</button>}</div>}
      {query.data?.map(item => <article className="submission-row" key={item.submission.id}><div className="row-copy"><p className="status-label">{statusLabel[item.submission.status]}</p><h2>{achievement(item.submission.achievementId).name}</h2><p className="evidence-preview">{item.submission.evidence}</p><p className="metadata">Submitted {new Date(item.submission.submittedAt).toLocaleDateString('en', { month: 'short', day: 'numeric', year: 'numeric' })}</p></div><button className="button outline" onClick={() => setSelected(item)}>{reviewer ? 'Review evidence' : 'View submission'}</button></article>)}
      {(offset > 0 || query.data?.length === 20) && <nav className="pagination" aria-label="Submission pages"><button className="button outline" disabled={offset === 0} onClick={() => setOffset(offset - 20)}>Previous</button><span>Page {offset / 20 + 1}</span><button className="button outline" disabled={!query.data || query.data.length < 20} onClick={() => setOffset(offset + 20)}>Next</button></nav>}
    </>}
    {authenticated && selected && (editing ? <EvidenceForm achievement={achievement(selected.submission.achievementId)} existing={selected} onClose={close}/> : <ReviewDetails item={selected} achievement={achievement(selected.submission.achievementId)} reviewer={reviewer} onClose={close} onEdit={() => setEditing(true)}/>)}
  </section>
}
function ReviewDetails({ item, achievement, reviewer, onClose, onEdit }: { item: Submission; achievement: Achievement; reviewer: boolean; onClose: () => void; onEdit: () => void }) {
  const [reason, setReason] = useState('')
  const client = useQueryClient()
  const mutation = useMutation({
    mutationFn: (decision: 'approve' | 'reject') => api(`/submissions/${item.submission.id}/${decision}`, { expectedVersion: item.version, ...(decision === 'reject' ? { reason: reason.trim() } : {}) }),
    onSuccess: (_data, decision) => {
      void client.invalidateQueries({ queryKey: ['submissions'] })
      useWorkspace.setState({ notice: decision === 'approve' ? 'Evidence approved.' : 'Feedback sent to the learner.' })
      onClose()
    },
  })
  return <Dialog title={achievement.name} onClose={onClose}>
    <p className="status-label">{statusLabel[item.submission.status]}</p>
    <div className="criteria"><h3>Achievement criteria</h3><p>{achievement.criteria}</p></div>
    <h3>Submitted evidence</h3><p className="evidence-content">{item.submission.evidence}</p>
    {item.submission.review?.reason && <div className="criteria"><h3>Reviewer feedback</h3><p>{item.submission.review.reason}</p></div>}
    {reviewer && item.submission.status === 'PENDING' && <>
      <label htmlFor="feedback">Feedback for requested changes</label><textarea id="feedback" rows={3} maxLength={1000} value={reason} onChange={e => setReason(e.target.value)}/><p className="field-help">Required when requesting changes. Up to 1,000 characters.</p>
      {mutation.isError && <p className="error" role="alert">{mutation.error.message}</p>}
      <div className="form-actions"><button className="button outline" disabled={!reason.trim() || mutation.isPending} onClick={() => mutation.mutate('reject')}>Request changes</button><button className="button primary" disabled={mutation.isPending} onClick={() => mutation.mutate('approve')}>{mutation.isPending ? 'Saving…' : 'Approve evidence'}</button></div>
    </>}
    {!reviewer && item.submission.status === 'REJECTED' && <button className="button primary" onClick={onEdit}>Update evidence</button>}
    {!reviewer && item.submission.status === 'APPROVED' && <CredentialActions submissionId={item.submission.id}/>}
  </Dialog>
}
