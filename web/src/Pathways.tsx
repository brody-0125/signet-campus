import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError, type Achievement, type Pathway, type PathwayProgress } from './api'
import { signIn, useSession } from './auth'
import { Dialog } from './Dialog'
import { useWorkspace } from './store'
import { CredentialActions } from './CredentialActions'

function PathwayCard({ pathway, achievements }: { pathway: Pathway; achievements: Achievement[] }) {
  const session = useSession()
  const client = useQueryClient()
  const queryKey = ['pathway-progress', pathway.id]
  const progress = useQuery({ queryKey, enabled: session.authenticated, retry: false, queryFn: async ({ signal }) => {
    try { return await api<PathwayProgress>(`/pathways/${pathway.id}/progress`, undefined, signal) }
    catch (error) { if (error instanceof ApiError && error.status === 404) return null; throw error }
  } })
  const enroll = useMutation({ mutationFn: () => api<PathwayProgress>(`/pathways/${pathway.id}/enrollment`, {}),
    onSuccess: value => client.setQueryData(queryKey, value) })
  return <article className="pathway-card">
    <h2>{pathway.name}</h2><p>{pathway.description}</p>
    {pathway.paused && <p className="notice">Enrollment is paused because a required achievement is archived. Existing progress and badges are retained. Contact the issuer about restoration to continue unfinished work.</p>}
    {!!pathway.prerequisiteAchievementIds?.length && <div className="criteria"><h3>Before you enroll</h3>
      <p className="field-help">Earn these badges first. They must be current when you enroll.</p>
      <ul>{pathway.prerequisiteAchievementIds.map(id => <li key={id}><button className="nav-link" onClick={() => useWorkspace.setState({ selectedId: id })}>{achievements.find(a => a.id === id)?.name || 'Achievement'}</button></li>)}</ul>
    </div>}
    <h3>To complete this pathway</h3>
    <ul>{pathway.achievementIds.map(id => <li key={id}>
      <button className="nav-link" onClick={() => useWorkspace.setState({ selectedId: id })}>{achievements.find(a => a.id === id)?.name || 'Achievement'}</button>
      {progress.data?.requirements.find(r => r.achievementId === id)?.earned && <span className="badge-status">Earned</span>}
    </li>)}</ul>
    {!session.authenticated ? <button className="button outline" disabled={!session.ready || !!session.error || !!session.notice} onClick={signIn}>Sign in to enroll</button> : <>
      {progress.isPending && <p role="status">Loading your progress…</p>}
      {progress.isError && <p className="error" role="alert">{progress.error.message}</p>}
      {progress.data && <><p className="pathway-summary">{progress.data.completed ? 'Complete' : 'In progress'} · {progress.data.earned} of {progress.data.total} achievements</p>
        <progress aria-label={`${pathway.name} progress`} value={progress.data.earned} max={progress.data.total}/></>}
      {progress.data === null && <button className="button primary" disabled={enroll.isPending || pathway.paused} onClick={() => enroll.mutate()}>{enroll.isPending ? 'Enrolling…' : 'Enroll'}</button>}
      {(progress.data || progress.isError) && <button className="button outline" disabled={progress.isFetching} onClick={() => void progress.refetch()}>Refresh progress</button>}
      {enroll.isError && <p className="error" role="alert">{enroll.error.message}</p>}
      {progress.data && <CredentialActions source={{ type: 'pathways', id: pathway.id }} canIssue={progress.data.completed}/>}
    </>}
  </article>
}

function PathwayForm({ achievements, onClose }: { achievements: Achievement[]; onClose: () => void }) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [ids, setIds] = useState<string[]>([])
  const [prerequisites, setPrerequisites] = useState<string[]>([])
  const client = useQueryClient()
  const save = useMutation({ mutationFn: () => api<Pathway>('/pathways', { name: name.trim(), description: description.trim(), achievementIds: ids, prerequisiteAchievementIds: prerequisites }),
    onSuccess: () => { void client.invalidateQueries({ queryKey: ['pathways'] }); onClose() } })
  function submit(event: FormEvent) { event.preventDefault(); save.mutate() }
  return <Dialog title="Create pathway" onClose={onClose}><form onSubmit={submit}>
    <p className="field-help">Choose completion badges and optional enrollment prerequisites. Published requirements cannot be changed; create a new pathway for a different set.</p>
    <label htmlFor="pathway-name">Pathway name</label><input id="pathway-name" required maxLength={120} value={name} onChange={e => setName(e.target.value)}/>
    <label htmlFor="pathway-description">Description</label><textarea id="pathway-description" required maxLength={5000} value={description} onChange={e => setDescription(e.target.value)}/>
    <fieldset className="pathway-choices"><legend>Required achievements (1–50)</legend>{achievements.map(a => <label key={a.id}>
      <input type="checkbox" checked={ids.includes(a.id)} disabled={prerequisites.includes(a.id) || (ids.length >= 50 && !ids.includes(a.id))} onChange={e => setIds(e.target.checked ? [...ids, a.id] : ids.filter(id => id !== a.id))}/>{a.name}
    </label>)}</fieldset>
    <fieldset className="pathway-choices"><legend>Before enrollment (optional, up to 50)</legend>
      <p className="field-help">Choose prerequisite badges. They cannot also be completion requirements.</p>
      {achievements.map(a => <label key={a.id}><input type="checkbox" aria-label={`Prerequisite: ${a.name}`} checked={prerequisites.includes(a.id)} disabled={ids.includes(a.id) || (prerequisites.length >= 50 && !prerequisites.includes(a.id))} onChange={e => setPrerequisites(e.target.checked ? [...prerequisites, a.id] : prerequisites.filter(id => id !== a.id))}/>{a.name}</label>)}
    </fieldset>
    {save.isError && <p className="error" role="alert">{save.error.message}</p>}
    <div className="form-actions"><button type="button" className="button outline" onClick={onClose}>Cancel</button>
      <button className="button primary" disabled={save.isPending || !name.trim() || !description.trim() || ids.length === 0}>{save.isPending ? 'Saving…' : 'Publish pathway'}</button></div>
  </form></Dialog>
}

export function Pathways({ achievements, achievementError, onRetryAchievements }: { achievements: Achievement[]; achievementError: boolean; onRetryAchievements: () => void }) {
  const [creating, setCreating] = useState(false)
  const session = useSession()
  const pathways = useQuery({ queryKey: ['pathways'], queryFn: ({ signal }) => api<Pathway[]>('/pathways', undefined, signal) })
  return <section className="container workspace"><div className="section-heading"><h1>Learning pathways</h1>
    {session.reviewer && <button className="button primary" onClick={() => setCreating(true)}>Create pathway</button>}</div>
    <p className="section-intro">A clear route to your next milestone. Earn every required badge to complete a pathway.</p>
    <p className="field-help">Progress counts current, issued badges. Expired or revoked badges no longer count.</p>
    {pathways.isPending && <p role="status">Loading pathways…</p>}
    {pathways.isError && <div role="alert"><p>Pathways could not be loaded.</p><button className="button outline" onClick={() => void pathways.refetch()}>Try again</button></div>}
    {pathways.isSuccess && achievementError && <p className="notice" role="status">Achievement details could not be loaded. <button className="nav-link" onClick={onRetryAchievements}>Retry achievement details</button></p>}
    {pathways.data?.length === 0 && <p className="empty-state">No pathways have been published yet.</p>}
    {pathways.data?.map(p => <PathwayCard key={p.id} pathway={p} achievements={achievements}/>)}
    {creating && session.reviewer && <PathwayForm achievements={achievements.filter(a => !a.archived)} onClose={() => setCreating(false)}/>}
  </section>
}
