import { accessToken } from './auth'

export type Achievement = { id: string; name: string; criteria: string; version: number }
export type Submission = {
  submission: {
    id: string; achievementId: string; learnerId: string; evidence: string
    status: 'PENDING' | 'APPROVED' | 'REJECTED'; submittedAt: string; revision: number
    review: { reason: string | null } | null
  }
  version: number
}
export async function api<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  const headers: Record<string, string> = {}
  if (path !== '/achievements' || body !== undefined) headers.Authorization = `Bearer ${await accessToken()}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const response = await fetch(`/api${path}`, {
    headers, signal,
    ...(body === undefined ? {} : { method: 'POST', body: JSON.stringify(body) }),
  })
  if (!response.ok) {
    if (path.startsWith('/achievements')) {
      if (response.status === 400) throw new Error('Enter a name and criteria within the displayed limits.')
      if (response.status === 409) throw new Error('This achievement changed or already has submissions. Refresh the catalog, or create a new achievement for different criteria.')
      if (response.status === 404) throw new Error('This achievement is no longer available.')
    }
    const messages: Record<number, string> = {
      400: 'Check your evidence and try again.',
      401: 'Your session has expired. Sign in again to continue.',
      403: 'You do not have permission to perform this action.',
      404: 'This submission is no longer available.',
      409: 'This submission changed since you opened it. Close it and refresh the list before trying again.',
    }
    throw new Error(messages[response.status] || 'We could not complete your request. Please try again.')
  }
  return response.json()
}
