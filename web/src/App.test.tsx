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
const achievement = { id: 'achievement-1', name: 'Digital Accessibility Awareness', criteria: 'Demonstrate keyboard access and text alternatives.' }
const submission = { id: 'submission-1', achievementId: achievement.id, evidence: 'My keyboard audit', status: 'PENDING', submittedAt: '2026-09-14T00:00:00Z', revision: 0, review: null }
const fetchMock = vi.fn()
function response(body: unknown, status = 200) { return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }) }
function mount() { render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}><App /></QueryClientProvider>) }
beforeEach(() => {
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
