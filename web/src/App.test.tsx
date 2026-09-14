import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, expect, it, vi } from 'vitest'
import { App } from './App'
import { useSession } from './auth'
import { useWorkspace } from './store'

vi.mock('./auth', async () => {
  const { create } = await import('zustand')
  return { useSession: create(() => ({ ready: true, authenticated: true, reviewer: false, name: 'Learner', error: null })), signIn: vi.fn(), signOut: vi.fn(), accessToken: async () => 'test-token' }
})
const achievement = { id: 'achievement-1', name: 'Digital Accessibility Awareness', criteria: 'Demonstrate keyboard access and text alternatives.', version: 0 }
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
  useSession.setState({ authenticated: true, reviewer: false })
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockImplementation(async (url: string) => response(url.includes('achievements') ? [achievement] : []))
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
