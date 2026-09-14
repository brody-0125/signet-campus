import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, expect, it, vi } from 'vitest'
import { App } from './App'
import { manageAccount, useSession } from './auth'
import { useWorkspace } from './store'

vi.mock('./auth', async () => {
  const { create } = await import('zustand')
  return { useSession: create(() => ({ ready: true, authenticated: true, reviewer: false, name: 'Learner', error: null })), signIn: vi.fn(), signOut: vi.fn(), manageAccount: vi.fn(), accessToken: async () => 'test-token' }
})
const achievement = { id: 'achievement-1', name: 'Digital Accessibility Awareness', criteria: 'Demonstrate keyboard access and text alternatives.', version: 0, published: true }
const submission = { id: 'submission-1', achievementId: achievement.id, evidence: 'My keyboard audit', status: 'PENDING', submittedAt: '2026-09-14T00:00:00Z', revision: 0, review: null }
const fetchMock = vi.fn()
function response(body: unknown, status = 200) { return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }) }
function mount() { render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}><App /></QueryClientProvider>) }
it('shows prerequisite criteria and explains why enrollment is blocked', async () => {
  const user = userEvent.setup()
  const path = { id: 'path-1', name: 'Advanced practice', description: 'Practice accessibility', achievementIds: [], prerequisiteAchievementIds: [achievement.id] }
  fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
    if (url.endsWith('/enrollment')) return response({}, 409)
    if (url.endsWith('/progress')) return response({}, 404)
    return response(url.includes('achievements') ? [achievement] : [path])
  })
  mount()
  await user.click(screen.getByRole('button', { name: 'Pathways' }))
  expect(await screen.findByText('Before you enroll')).toBeInTheDocument()
  await user.click(await screen.findByRole('button', { name: 'Enroll' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Earn every prerequisite badge before enrolling')
  await user.click(screen.getByRole('button', { name: achievement.name }))
  expect(await screen.findByText(achievement.criteria)).toBeInTheDocument()
})
it('enrolls in a pathway and displays current credential progress', async () => {
  const user = userEvent.setup()
  const path = { id: 'path-1', name: 'Accessible campus', description: 'Build accessible skills', achievementIds: [achievement.id] }
  let enrolled = false
  fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
    if (url.endsWith('/enrollment')) enrolled = true
    if (url.endsWith('/progress') || url.endsWith('/enrollment')) return enrolled
      ? response({ pathwayId: path.id, enrolledAt: '2026-09-14T00:00:00Z', earned: 1, total: 1, completed: true, requirements: [{ achievementId: achievement.id, earned: true }] })
      : response({}, 404)
    return response(url.includes('achievements') ? [achievement] : [path])
  })
  mount()
  await user.click(screen.getByRole('button', { name: 'Pathways' }))
  await user.click(await screen.findByRole('button', { name: 'Enroll' }))
  expect(await screen.findByText('Complete · 1 of 1 achievements')).toBeInTheDocument()
  expect(fetchMock).toHaveBeenCalledWith('/api/pathways/path-1/enrollment', expect.objectContaining({ method: 'POST', headers: expect.objectContaining({ Authorization: 'Bearer test-token' }) }))
})
it('retrieves a pathway completion award after live progress becomes incomplete', async () => {
  const user = userEvent.setup()
  const path = { id: 'path-1', name: 'Accessible campus', description: 'Build accessible skills', achievementIds: [achievement.id] }
  const credential = { id: 'http://localhost:5173/api/credentials/completion-1', type: ['VerifiableCredential', 'OpenBadgeCredential'] }
  fetchMock.mockImplementation(async (url: string) => {
    if (url.endsWith('/credential')) return response(credential)
    if (url.endsWith('/verify')) return response({ status: 'VALID', valid: true })
    if (url.endsWith('/progress')) return response({ pathwayId: path.id, earned: 0, total: 1, completed: false, requirements: [] })
    return response(url.includes('achievements') ? [achievement] : [path])
  })
  mount()
  await user.click(screen.getByRole('button', { name: 'Pathways' }))
  expect(await screen.findByText('In progress · 0 of 1 achievements')).toBeInTheDocument()
  const download = await screen.findByRole('link', { name: 'Download JSON' })
  expect(JSON.parse(decodeURIComponent(download.getAttribute('href')!.split(',')[1]))).toEqual(credential)
  await user.click(screen.getByRole('button', { name: 'Verify badge' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Verified: authentic, current and not revoked.')
})
it('issues a completion award and explains an unverified email failure', async () => {
  const user = userEvent.setup()
  const path = { id: 'path-1', name: 'Accessible campus', description: 'Build accessible skills', achievementIds: [achievement.id] }
  let verified = false
  fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
    if (url.endsWith('/credential')) return options?.method === 'POST'
      ? verified ? response({ id: 'https://example.test/api/credentials/completion-1' }) : response({}, 400)
      : response({}, 404)
    if (url.endsWith('/progress')) return response({ pathwayId: path.id, earned: 1, total: 1, completed: true, requirements: [] })
    return response(url.includes('achievements') ? [achievement] : [path])
  })
  mount()
  await user.click(screen.getByRole('button', { name: 'Pathways' }))
  await user.click(await screen.findByRole('button', { name: 'Issue badge' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('A verified email address is required')
  verified = true
  await user.click(screen.getByRole('button', { name: 'Issue badge' }))
  expect(await screen.findByRole('link', { name: 'Download JSON' })).toBeInTheDocument()
  expect(fetchMock).toHaveBeenCalledWith('/api/pathways/path-1/credential', expect.objectContaining({ method: 'POST' }))
})
it('lets a reviewer create an achievement with an authenticated request', async () => {
  useSession.setState({ reviewer: true })
  const user = userEvent.setup()
  fetchMock.mockImplementation(async (_url: string, options?: RequestInit) => response(options?.method === 'POST' ? achievement : [achievement]))
  mount()
  await user.click(await screen.findByRole('button', { name: 'Create achievement' }))
  await user.type(screen.getByLabelText('Achievement name'), 'Keyboard access')
  await user.type(screen.getByLabelText('Assessment criteria'), 'Audit every control')
  await user.click(screen.getByRole('button', { name: 'Save achievement' }))
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/achievements', expect.objectContaining({
    method: 'POST', headers: expect.objectContaining({ Authorization: 'Bearer test-token' }),
    body: JSON.stringify({ name: 'Keyboard access', criteria: 'Audit every control' }),
  })))
})
beforeEach(() => {
  window.history.replaceState({}, '', '/')
  useWorkspace.setState({ view: 'explore', selectedId: null, notice: '' })
  useSession.setState({ ready: true, authenticated: true, reviewer: false })
  vi.mocked(manageAccount).mockReset()
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockImplementation(async (url: string) => response(url.includes('achievements') ? [achievement] : []))
})
it('explains archival before applying the saved version and offers restoration', async () => {
  useSession.setState({ reviewer: true })
  const user = userEvent.setup()
  let archived = false
  fetchMock.mockImplementation(async (_url: string, options?: RequestInit) => {
    if (options?.method === 'POST') archived = JSON.parse(options.body as string).archived
    const entry = { ...achievement, archived, version: archived ? 1 : 0 }
    return response(options?.method === 'POST' ? entry : [entry])
  })
  mount()
  await user.click(await screen.findByRole('button', { name: `Archive ${achievement.name}` }))
  expect(screen.getByRole('dialog')).toHaveTextContent('Existing badges are not revoked')
  await user.click(screen.getByRole('button', { name: 'Archive achievement' }))
  expect(await screen.findByRole('button', { name: `Restore ${achievement.name}` })).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: `Restore ${achievement.name}` }))
  await user.click(screen.getByRole('button', { name: 'Restore achievement' }))
  expect(await screen.findByRole('button', { name: `Archive ${achievement.name}` })).toBeInTheDocument()
})
it('publishes a reviewer draft with its saved version and removes draft controls', async () => {
  useSession.setState({ reviewer: true })
  const user = userEvent.setup()
  let published = false
  fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
    if (options?.method === 'POST') published = true
    const entry = { ...achievement, published, version: published ? 4 : 3 }
    return response(options?.method === 'POST' ? entry : url.includes('/reviewer/') || published ? [entry] : [])
  })
  mount()
  expect(await screen.findByText('Draft · Only reviewers can see this achievement')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'View criteria' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: `Publish ${achievement.name}` }))
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/achievements/achievement-1/publish', expect.objectContaining({ body: '{"expectedVersion":3}' })))
  expect(await screen.findByRole('button', { name: 'View criteria' })).toBeInTheDocument()
  expect(screen.queryByText('Draft · Only reviewers can see this achievement')).not.toBeInTheDocument()
})
it('keeps a draft visible with an error when publication loses a version race', async () => {
  useSession.setState({ reviewer: true })
  const user = userEvent.setup()
  fetchMock.mockImplementation(async (url: string, options?: RequestInit) => options?.method === 'POST' ? response({}, 409)
    : response(url.includes('/reviewer/') ? [{ ...achievement, published: false }] : []))
  mount()
  await user.click(await screen.findByRole('button', { name: `Publish ${achievement.name}` }))
  expect(await screen.findByRole('alert')).toHaveTextContent('changed')
  expect(screen.getByText('Draft · Only reviewers can see this achievement')).toBeInTheDocument()
})
it('opens reviewer criteria even when the separate public catalog request fails', async () => {
  useSession.setState({ reviewer: true })
  const user = userEvent.setup()
  fetchMock.mockImplementation(async (url: string) => url.includes('/reviewer/') ? response([achievement]) : response({}, 503))
  mount()
  await user.click(await screen.findByRole('button', { name: 'View criteria' }))
  expect(await screen.findByRole('dialog')).toHaveTextContent(achievement.criteria)
})
it('keeps an archived achievement badge downloadable without starting new issuance', async () => {
  useWorkspace.setState({ view: 'submissions' })
  const user = userEvent.setup()
  fetchMock.mockImplementation(async (url: string) => response(url.endsWith('/credential') ? { id: 'http://localhost/api/credentials/existing' }
    : url.includes('achievements') ? [{ ...achievement, archived: true }] : [{ submission: { ...submission, status: 'APPROVED' }, version: 1 }]))
  mount()
  await user.click(await screen.findByRole('button', { name: 'View submission' }))
  expect(await screen.findByRole('link', { name: 'Download JSON' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Issue badge' })).not.toBeInTheDocument()
  expect(screen.getByText(/New work and first badge issuance are paused/)).toBeInTheDocument()
})
it('disables enrollment and explains a pathway paused by an archived requirement', async () => {
  useWorkspace.setState({ view: 'pathways' })
  fetchMock.mockImplementation(async (url: string) => url.endsWith('/progress') ? response({}, 404)
    : response(url.includes('achievements') ? [{ ...achievement, archived: true }] : [{ id: 'paused-path', name: 'Paused pathway', description: 'Preserved requirements', achievementIds: [achievement.id], paused: true }]))
  mount()
  expect(await screen.findByRole('button', { name: 'Enroll' })).toBeDisabled()
  expect(screen.getByText(/Enrollment is paused because/)).toBeInTheDocument()
})
it('allows retrying historical metadata without losing the submissions view', async () => {
  useWorkspace.setState({ view: 'submissions' })
  const user = userEvent.setup()
  let failed = true
  fetchMock.mockImplementation(async (url: string) => url.includes('includeArchived') ? failed ? response({}, 503) : response([achievement]) : response([]))
  mount()
  expect(await screen.findByRole('alert')).toHaveTextContent('Achievement details could not be loaded')
  failed = false
  await user.click(screen.getByRole('button', { name: 'Retry achievement details' }))
  await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  expect(screen.getByRole('heading', { name: 'My submissions' })).toBeInTheDocument()
})
it('opens account management and keeps sign out available after a failed redirect', async () => {
  const user = userEvent.setup()
  mount()
  await user.click(screen.getByRole('button', { name: 'Account' }))
  expect(screen.getByRole('heading', { name: 'Keep access to your badges' })).toBeInTheDocument()
  vi.mocked(manageAccount).mockRejectedValueOnce(new Error('Unavailable'))
  await user.click(screen.getByRole('button', { name: 'Manage account' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Unable to open account settings')
  expect(screen.getByRole('button', { name: 'Sign out' })).toBeEnabled()
  await user.click(screen.getByRole('button', { name: 'Manage account' }))
  expect(manageAccount).toHaveBeenCalledTimes(2)
})
it('does not show account controls to anonymous visitors', () => {
  useSession.setState({ authenticated: false })
  mount()
  expect(screen.queryByRole('button', { name: 'Account' })).not.toBeInTheDocument()
})
it('opens criteria and saves evidence through the API, then shows the pending submission', async () => {
  const user = userEvent.setup()
  fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
    if (options?.method === 'POST') return response({ submission, version: 0 }, 201)
    return response(url.includes('achievements') ? [achievement] : [{ submission, version: 0 }])
  })
  mount()
  await user.click(await screen.findByRole('button', { name: 'View criteria' }))
  await user.type(screen.getByLabelText('Your evidence'), 'My keyboard audit')
  await user.click(screen.getByRole('button', { name: 'Submit evidence' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Evidence submitted for review.')
  expect(await screen.findByText('Pending review')).toBeInTheDocument()
  expect(fetchMock).toHaveBeenCalledWith('/api/submissions', expect.objectContaining({ method: 'POST', body: JSON.stringify({ achievementId: achievement.id, evidence: 'My keyboard audit' }) }))
})
it('preserves evidence and explains a failed submission', async () => {
  const user = userEvent.setup()
  fetchMock.mockImplementation(async (_url: string, options?: RequestInit) => options?.method === 'POST' ? response({}, 503) : response([achievement]))
  mount()
  await user.click(await screen.findByRole('button', { name: 'View criteria' }))
  await user.type(screen.getByLabelText('Your evidence'), 'Keep this evidence')
  await user.click(screen.getByRole('button', { name: 'Submit evidence' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Please try again')
  expect(screen.getByLabelText('Your evidence')).toHaveValue('Keep this evidence')
})
it('uses the current version for a review and reports a stale update', async () => {
  const user = userEvent.setup()
  useSession.setState({ reviewer: true })
  useWorkspace.setState({ view: 'submissions' })
  fetchMock.mockImplementation(async (url: string, options?: RequestInit) => options?.method === 'POST' ? response({}, 409) : response(url.includes('achievements') ? [achievement] : [{ submission, version: 7 }]))
  mount()
  await user.click(await screen.findByRole('button', { name: 'Review evidence' }))
  await user.click(screen.getByRole('button', { name: 'Approve evidence' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('changed since you opened it')
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/submissions/submission-1/approve', expect.objectContaining({ body: '{"expectedVersion":7}' })))
})
it('issues an approved badge and verifies the returned credential', async () => {
  const user = userEvent.setup()
  useWorkspace.setState({ view: 'submissions' })
  const credential = { id: 'http://localhost:5173/api/credentials/credential-1', type: ['VerifiableCredential', 'OpenBadgeCredential'] }
  fetchMock.mockImplementation(async (url: string) => response(
    url.endsWith('/verify') ? { status: 'VALID', valid: true } :
    url.endsWith('/credential') ? credential :
    url.includes('achievements') ? [achievement] : [{ submission: { ...submission, status: 'APPROVED' }, version: 1 }],
  ))
  mount()
  await user.click(await screen.findByRole('button', { name: 'View submission' }))
  await user.click(screen.getByRole('button', { name: 'Issue badge' }))
  const download = await screen.findByRole('link', { name: 'Download JSON' })
  expect(download).toHaveAttribute('download', 'signet-campus-credential.json')
  expect(JSON.parse(decodeURIComponent(download.getAttribute('href')!.split(',')[1]))).toEqual(credential)
  await user.click(screen.getByRole('button', { name: 'Verify badge' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Verified: authentic, current and not revoked.')
})

it('requests private badge images with authentication and reports download failures', async () => {
  const user = userEvent.setup()
  useWorkspace.setState({ view: 'submissions' })
  fetchMock.mockImplementation(async (url: string) => {
    if (url.endsWith('/image/png')) return response({}, 503)
    return response(url.endsWith('/credential') ? { id: 'http://localhost:5173/api/credentials/credential-1' }
      : url.includes('achievements') ? [achievement] : [{ submission: { ...submission, status: 'APPROVED' }, version: 1 }])
  })
  mount()
  await user.click(await screen.findByRole('button', { name: 'View submission' }))
  await user.click(screen.getByRole('button', { name: 'Issue badge' }))
  await user.click(await screen.findByRole('button', { name: 'Download PNG' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Please try again')
  expect(fetchMock).toHaveBeenCalledWith('/api/credentials/credential-1/image/png', expect.objectContaining({
    headers: expect.objectContaining({ Authorization: 'Bearer test-token' }),
  }))
})

it('requires explicit consent to publish a badge and can stop sharing', async () => {
  const user = userEvent.setup()
  useWorkspace.setState({ view: 'submissions' })
  let enabled = false
  fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
    if (url.endsWith('/sharing')) {
      if (options?.method === 'POST') enabled = JSON.parse(options.body as string).enabled
      return response({ enabled, url: enabled ? 'http://localhost/shared/credential-1' : null })
    }
    return response(url.endsWith('/credential') ? { id: 'http://localhost/api/credentials/credential-1' }
      : url.includes('achievements') ? [achievement] : [{ submission: { ...submission, status: 'APPROVED' }, version: 1 }])
  })
  mount()
  await user.click(await screen.findByRole('button', { name: 'View submission' }))
  await user.click(screen.getByRole('button', { name: 'Issue badge' }))
  expect(await screen.findByRole('button', { name: 'Publish share link' })).toBeDisabled()
  await user.click(screen.getByLabelText('I want to make this credential public.'))
  await user.click(screen.getByRole('button', { name: 'Publish share link' }))
  expect(await screen.findByLabelText('Share link')).toHaveValue('http://localhost/shared/credential-1')
  await user.click(screen.getByRole('button', { name: 'Stop sharing' }))
  await waitFor(() => expect(screen.queryByLabelText('Share link')).not.toBeInTheDocument())
  expect(screen.getByRole('button', { name: 'Publish share link' })).toBeDisabled()
})

it('renders and verifies a shared credential without authentication', async () => {
  const user = userEvent.setup()
  window.history.replaceState({}, '', '/shared/credential-1')
  useSession.setState({ authenticated: false, ready: false })
  fetchMock.mockImplementation(async (url: string) => response(url.endsWith('/verify') ? { status: 'REVOKED', valid: false }
    : { id: 'http://localhost/api/credentials/credential-1', name: 'Accessible campus award', validFrom: '2026-09-14T00:00:00Z', validUntil: '2027-09-14T00:00:00Z', issuer: { name: 'Signet Campus' } }))
  mount()
  expect(await screen.findByRole('heading', { name: 'Accessible campus award' })).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Verify credential' }))
  expect(await screen.findByRole('status')).toHaveTextContent('revoked')
  expect(fetchMock).toHaveBeenCalledWith('/api/shared/credentials/credential-1', expect.objectContaining({ headers: {} }))
  expect(fetchMock).toHaveBeenCalledWith('/api/credentials/credential-1/verify', expect.objectContaining({ headers: { 'Content-Type': 'application/json' } }))
  window.history.replaceState({}, '', '/')
})

it('creates a private next edition without editing its archived source', async () => {
  useSession.setState({ reviewer: true })
  const user = userEvent.setup()
  const source = { ...achievement, archived: true, version: 4 }
  let created = false
  fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
    if (url.endsWith('/successors')) { created = true; return response({ ...source, id: 'edition-2', published: false, archived: false, predecessorId: source.id }, 201) }
    return response(url.includes('achievements') ? [source, ...(created ? [{ ...source, id: 'edition-2', name: 'Next year', published: false, archived: false, predecessorId: source.id }] : [])] : [])
  })
  mount()
  await user.click(await screen.findByRole('button', { name: `Create next edition of ${source.name}` }))
  expect(screen.getByRole('dialog')).toHaveTextContent('Existing badges and pathway requirements stay with the original edition.')
  await user.clear(screen.getByLabelText('Achievement name'))
  await user.type(screen.getByLabelText('Achievement name'), 'Next year')
  await user.click(screen.getByRole('button', { name: 'Save achievement' }))
  expect(await screen.findByText('Next year')).toBeInTheDocument()
  expect(fetchMock).toHaveBeenCalledWith('/api/achievements/achievement-1/successors', expect.objectContaining({
    method: 'POST', body: JSON.stringify({ name: 'Next year', criteria: source.criteria, expectedVersion: 4 }),
  }))
})

it('loads archived previous criteria publicly and retries a failed lookup', async () => {
  useSession.setState({ authenticated: false })
  const user = userEvent.setup()
  let lookups = 0
  fetchMock.mockImplementation(async (url: string) => {
    if (url.endsWith('/achievements/original')) return ++lookups === 1 ? response({}, 503) : response({ ...achievement, id: 'original', name: 'Original edition', criteria: 'Original criteria', archived: true })
    return response(url.includes('achievements') ? [{ ...achievement, predecessorId: 'original' }] : [])
  })
  mount()
  await user.click(await screen.findByRole('button', { name: 'View criteria' }))
  await user.click(screen.getByRole('button', { name: 'Previous edition criteria' }))
  await user.click(await screen.findByRole('button', { name: 'Retry previous edition' }))
  expect(await screen.findByText('Original criteria')).toBeInTheDocument()
  expect(screen.getByText('Archived edition')).toBeInTheDocument()
  expect(fetchMock).toHaveBeenCalledWith('/api/achievements/original', expect.objectContaining({ headers: {} }))
})

it('returns to the first page only after a successful resubmission', async () => {
  useWorkspace.setState({ view: 'submissions' })
  const user = userEvent.setup()
  let saved = false
  const rejected = { submission: { ...submission, status: 'REJECTED', review: { reason: 'Add keyboard evidence' } }, version: 1 }
  const first = Array.from({ length: 20 }, (_, index) => ({ submission: { ...submission, id: 'earlier-' + index }, version: 0 }))
  fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
    if (url.endsWith('/resubmit')) { saved = true; return response({}) }
    if (url.includes('achievements')) return response([achievement])
    if (url.includes('offset=20')) return response(saved ? [] : [rejected])
    return response(saved ? [{ submission: { ...submission, evidence: 'Updated keyboard evidence' }, version: 2 }, ...first.slice(0, 19)] : first)
  })
  mount()
  await user.click(await screen.findByRole('button', { name: 'Next' }))
  await user.click(await screen.findByRole('button', { name: 'View submission' }))
  await user.click(screen.getByRole('button', { name: 'Update evidence' }))
  await user.click(screen.getByRole('button', { name: 'Cancel' }))
  expect(screen.getByText('Page 2')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'View submission' }))
  await user.click(screen.getByRole('button', { name: 'Update evidence' }))
  await user.clear(screen.getByLabelText('Your evidence'))
  await user.type(screen.getByLabelText('Your evidence'), 'Updated keyboard evidence')
  await user.click(screen.getByRole('button', { name: 'Resubmit evidence' }))
  expect(await screen.findByText('Page 1')).toBeInTheDocument()
  expect(await screen.findByText('Updated keyboard evidence')).toBeInTheDocument()
})
