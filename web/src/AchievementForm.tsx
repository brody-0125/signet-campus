import { useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api, type Achievement } from './api'
import { Dialog } from './Dialog'
import { useWorkspace } from './store'

export function AchievementForm({ achievement, predecessor, onClose }: { achievement?: Achievement; predecessor?: Achievement; onClose: () => void }) {
  const source = predecessor || achievement
  const [name, setName] = useState(source?.name || '')
  const [criteria, setCriteria] = useState(source?.criteria || '')
  const client = useQueryClient()
  const mutation = useMutation({
    mutationFn: () => api(predecessor ? `/achievements/${predecessor.id}/successors` : achievement ? `/achievements/${achievement.id}` : '/achievements', {
      name: name.trim(), criteria: criteria.trim(), ...(source ? { expectedVersion: source.version } : {}),
    }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['achievements'] })
      useWorkspace.setState({ notice: achievement?.published ? 'Achievement saved.' : 'Draft saved. Publish it when the criteria are ready.' })
      onClose()
    },
  })
  function submit(event: FormEvent) { event.preventDefault(); if (name.trim() && criteria.trim()) mutation.mutate() }
  return <Dialog title={predecessor ? 'Create next edition' : achievement ? 'Edit achievement' : 'Create achievement'} onClose={onClose}>
    <p className="field-help">New achievements are saved as drafts, visible only to reviewers. Publish when the criteria are ready. Once someone submits evidence, the criteria are locked.</p>
    {predecessor && <p className="notice">Based on {predecessor.name}. Existing badges and pathway requirements stay with the original edition.</p>}
    <form onSubmit={submit}>
      <label htmlFor="achievement-name">Achievement name</label>
      <input id="achievement-name" required maxLength={120} value={name} onChange={event => setName(event.target.value)}/>
      <label htmlFor="achievement-criteria">Assessment criteria</label>
      <textarea id="achievement-criteria" required maxLength={5000} rows={6} value={criteria} onChange={event => setCriteria(event.target.value)}/>
      {mutation.isError && <p className="error" role="alert">{mutation.error.message}</p>}
      <div className="form-actions"><button type="button" className="button outline" onClick={onClose}>Cancel</button><button className="button primary" disabled={mutation.isPending || !name.trim() || !criteria.trim()}>{mutation.isPending ? 'Saving…' : 'Save achievement'}</button></div>
    </form>
  </Dialog>
}

export function PublishAchievement({ achievement }: { achievement: Achievement }) {
  const client = useQueryClient()
  const mutation = useMutation({
    mutationFn: () => api(`/achievements/${achievement.id}/publish`, { expectedVersion: achievement.version }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['achievements'] })
      useWorkspace.setState({ notice: 'Achievement published. Learners can now submit evidence.' })
    },
  })
  return <div className="publication-actions">
    <button className="button primary" aria-label={`Publish ${achievement.name}`} disabled={mutation.isPending} onClick={() => mutation.mutate()}>{mutation.isPending ? 'Publishing…' : 'Publish'}</button>
    {mutation.isError && <p className="error" role="alert">{mutation.error.message}</p>}
  </div>
}
