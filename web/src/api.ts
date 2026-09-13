import { accessToken } from './auth'

export type Achievement = { id: string; name: string; criteria: string }
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
  if (path !== '/achievements') headers.Authorization = `Bearer ${await accessToken()}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const response = await fetch(`/api${path}`, {
    headers, signal,
    ...(body === undefined ? {} : { method: 'POST', body: JSON.stringify(body) }),
  })
  if (!response.ok) {
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
