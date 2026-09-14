import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Achievement, type Submission } from './api'
import { signIn, useSession } from './auth'
import { useWorkspace } from './store'
import { Dialog } from './Dialog'

export function EvidenceForm({ achievement, onClose, existing, onSubmitted }: { achievement: Achievement; onClose: () => void; existing?: Submission; onSubmitted?: () => void }) {
  const authenticated = useSession(s => s.authenticated)
  const ready = useSession(s => s.ready)
  const [evidence, setEvidence] = useState(existing?.submission.evidence || '')
  const client = useQueryClient()
  const mutation = useMutation({
    mutationFn: () => existing
      ? api(`/submissions/${existing.submission.id}/resubmit`, { expectedVersion: existing.version, evidence: evidence.trim() })
      : api('/submissions', { achievementId: achievement.id, evidence: evidence.trim() }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['submissions'] })
      useWorkspace.setState({ view: 'submissions', selectedId: null, notice: 'Evidence submitted for review.' })
      onSubmitted?.()
      onClose()
    },
  })
  function submit(event: FormEvent) { event.preventDefault(); if (evidence.trim()) mutation.mutate() }
  return <Dialog title={achievement.name} onClose={onClose}>
    <div className="criteria"><h3>Achievement criteria</h3><p>{achievement.criteria}</p></div>
    {achievement.predecessorId && <PreviousEdition id={achievement.predecessorId}/>}
    {existing?.submission.review?.reason && <p className="notice">Reviewer feedback: {existing.submission.review.reason}</p>}
    {achievement.archived ? <p className="notice">This achievement is archived. New and revised evidence are paused. Contact the issuer about restoration; your existing work is retained.</p> : authenticated ? <form onSubmit={submit}>
      <label htmlFor="evidence">Your evidence</label><p className="field-help" id="evidence-help">Describe your work and include links that your reviewer can access. Your evidence is visible to you and reviewers.</p>
      <textarea id="evidence" required maxLength={4000} rows={7} value={evidence} onChange={e => setEvidence(e.target.value)} aria-describedby="evidence-help evidence-count"/>
      <p id="evidence-count" className="character-count">{evidence.length} / 4,000 characters</p>
      {mutation.isError && <p className="error" role="alert">{mutation.error.message}</p>}
      <div className="form-actions"><button type="button" className="button outline" onClick={onClose}>Cancel</button><button className="button primary" disabled={!evidence.trim() || mutation.isPending}>{mutation.isPending ? 'Submitting…' : existing ? 'Resubmit evidence' : 'Submit evidence'}</button></div>
    </form> : <div className="sign-in-prompt"><p>Sign in to submit your evidence and follow its review.</p><button className="button primary" disabled={!ready} onClick={signIn}>Sign in to continue</button></div>}
  </Dialog>
}

function PreviousEdition({ id }: { id: string }) {
  const [opened, setOpened] = useState(false)
  const previous = useQuery({ queryKey: ['achievements', id], queryFn: ({ signal }) => api<Achievement>(`/achievements/${id}`, undefined, signal), enabled: opened })
  return <div className="criteria">
    <button className="button outline" aria-expanded={opened} onClick={() => setOpened(!opened)}>Previous edition criteria</button>
    {opened && <div>
      {previous.isPending && <p role="status">Loading previous edition…</p>}
      {previous.isError && <p role="alert">{previous.error.message} <button className="button outline" onClick={() => void previous.refetch()}>Retry previous edition</button></p>}
      {previous.data && <><h3>{previous.data.name}</h3><p>{previous.data.criteria}</p>{previous.data.archived && <p className="field-help">Archived edition</p>}</>}
    </div>}
  </div>
}
