import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { AchievementForm, PublishAchievement } from './AchievementForm'
import { api, type Achievement } from './api'
import { signIn, signOut, useSession } from './auth'
import { useWorkspace } from './store'
import { EvidenceForm } from './EvidenceForm'
import { Submissions } from './Submissions'
import { Pathways } from './Pathways'
import { SharedCredential } from './SharedCredential'
import { Account } from './Account'

export function AccessibilityIcon() {
  return <svg width="36" height="36" viewBox="0 0 36 36" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="18" cy="7" r="3"/><path d="M7 14l11 2 11-2M18 16v7m0 0-5 10m5-10 5 10M12 15l1 9m11-9-1 9"/></svg>
}
export function App() {
  return window.location.pathname.startsWith('/shared/')
    ? <SharedCredential id={window.location.pathname.slice('/shared/'.length)}/>
    : <WorkspaceApp/>
}
function WorkspaceApp() {
  const [editing, setEditing] = useState<Achievement | 'new' | null>(null)
  const view = useWorkspace(s => s.view)
  const selectedId = useWorkspace(s => s.selectedId)
  const notice = useWorkspace(s => s.notice)
  const navigate = useWorkspace(s => s.navigate)
  const session = useSession()
  const achievements = useQuery({ queryKey: ['achievements', 'public'], queryFn: ({ signal }) => api<Achievement[]>('/achievements', undefined, signal) })
  const reviewerAchievements = useQuery({ queryKey: ['achievements', 'reviewer'], queryFn: ({ signal }) => api<Achievement[]>('/reviewer/achievements', undefined, signal), enabled: session.ready && session.authenticated && session.reviewer })
  const catalog = session.reviewer ? reviewerAchievements : achievements
  const selected = achievements.data?.find(a => a.id === selectedId)
  return <>
    <a className="skip-link" href="#main">Skip to content</a>
    <header className="site-header"><div className="container header-inner">
      <a className="wordmark" href="#" onClick={() => navigate('explore')}>signet campus</a>
      <nav aria-label="Main navigation">
        <button className="nav-link" aria-current={view === 'explore' ? 'page' : undefined} onClick={() => navigate('explore')}>Explore</button>
        <button className="nav-link" aria-current={view === 'pathways' ? 'page' : undefined} onClick={() => navigate('pathways')}>Pathways</button>
        <button className="nav-link" aria-current={view === 'submissions' ? 'page' : undefined} onClick={() => navigate('submissions')}>{session.reviewer ? 'Review queue' : 'My submissions'}</button>
        {session.authenticated && <button className="nav-link" aria-current={view === 'account' ? 'page' : undefined} onClick={() => navigate('account')}>Account</button>}
      </nav>
      <button className="button outline" disabled={!session.ready || !!session.error} onClick={session.authenticated ? signOut : signIn}>{session.authenticated ? 'Sign out' : 'Sign in'}</button>
    </div></header>
    <main id="main">
      {session.error && <p className="container error" role="alert">{session.error}</p>}
      {notice && <div className="container notice" role="status">{notice}</div>}
      {view === 'explore' ? <>
        <section className="container hero" aria-labelledby="hero-title">
          <div className="hero-copy"><h1 id="hero-title">Make your skills<br/>count.</h1><p>Build your evidence. Get it reviewed.<br/>Take your learning further.</p><a className="button primary" href="#achievements">Explore achievements <span aria-hidden="true">→</span></a></div>
          <img className="hero-art" src="/skills-cluster.png" width="1254" height="1254" alt="" fetchPriority="high"/>
        </section>
        <section className="catalog" id="achievements" aria-labelledby="catalog-title"><div className="container">
          <h2 id="catalog-title">Find your next achievement</h2><p className="section-intro">Start with the criteria. Show what you can do.</p>
          {session.reviewer && <button className="button primary" onClick={() => setEditing('new')}>Create achievement</button>}
          {catalog.isPending && <p role="status">Loading achievements…</p>}
          {catalog.isError && <div role="alert"><p>Achievements could not be loaded.</p><button className="button outline" onClick={() => void catalog.refetch()}>Try again</button></div>}
          {catalog.data?.length === 0 && <p>No achievements are available yet.</p>}
          {catalog.data?.map(a => <article className="achievement-row" key={a.id}><div className="icon-tile"><AccessibilityIcon/></div><div className="row-copy"><h3>{a.name}</h3><p>{a.criteria}</p>{!a.published && <p className="field-help">Draft · Only reviewers can see this achievement</p>}</div>{a.published && <button className="button outline" onClick={() => useWorkspace.setState({ selectedId: a.id })}>View criteria</button>}{session.reviewer && <button className="button outline" aria-label={`Edit ${a.name}`} onClick={() => setEditing(a)}>Edit</button>}{session.reviewer && !a.published && <PublishAchievement achievement={a}/>}</article>)}
        </div></section>
      </> : view === 'pathways' ? <Pathways achievements={achievements.data || []}/> : view === 'account' ? session.authenticated ? <Account/> : <p className="container">Sign in to manage your account.</p> : <Submissions achievements={achievements.data || []}/>}
    </main>
    <footer><div className="container"><a className="wordmark" href="#" onClick={() => navigate('explore')}>signet campus</a><p>Learning, made visible.</p></div></footer>
    {selected && <EvidenceForm achievement={selected} onClose={() => useWorkspace.setState({ selectedId: null })}/>}
    {editing && session.reviewer && <AchievementForm achievement={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)}/>}
  </>
}
