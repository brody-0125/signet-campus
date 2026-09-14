import { accessToken } from './auth'

export type Achievement = { id: string; name: string; criteria: string; version: number; published: boolean; archived: boolean; predecessorId?: string | null }
export type Pathway = { id: string; name: string; description: string; achievementIds: string[]; prerequisiteAchievementIds?: string[]; paused?: boolean }
export type PathwayProgress = { pathwayId: string; enrolledAt: string; earned: number; total: number; completed: boolean; requirements: { achievementId: string; earned: boolean }[] }
export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message) }
}
export type Submission = {
  submission: {
    id: string; achievementId: string; learnerId: string; evidence: string
    status: 'PENDING' | 'APPROVED' | 'REJECTED'; submittedAt: string; revision: number
    review: { reason: string | null } | null
  }
  version: number
}
export async function api<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  return (await request(path, body, signal)).json()
}
export async function credentialImage(id: string, format: 'png' | 'svg'): Promise<Blob> {
  return (await request(`/credentials/${id}/image/${format}`)).blob()
}
async function request(path: string, body?: unknown, signal?: AbortSignal): Promise<Response> {
  const headers: Record<string, string> = {}
  const publicRequest = (body === undefined && (['/achievements', '/achievements?includeArchived=true', '/pathways'].includes(path) || path.startsWith('/shared/credentials/')))
    || (body === undefined && /^\/achievements\/[^/?]+$/.test(path))
    || (body !== undefined && /^\/credentials\/[^/]+\/verify$/.test(path))
  if (!publicRequest) headers.Authorization = `Bearer ${await accessToken()}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const response = await fetch(`/api${path}`, {
    headers, signal,
    ...(body === undefined ? {} : { method: 'POST', body: JSON.stringify(body) }),
  })
  if (!response.ok) {
    if (response.status === 423) throw new ApiError(423, 'Work is paused because an achievement is archived. Contact the issuer about restoration. Previously issued badges remain available.')
    if (path.startsWith('/shared/credentials/')) throw new ApiError(response.status, 'This shared credential is unavailable. It may be private or sharing may have stopped.')
    if (path.endsWith('/credential')) {
      if (response.status === 400) throw new ApiError(400, 'A verified email address is required on your account to issue a badge.')
      if (response.status === 404) throw new ApiError(404, 'No credential is available for this record.')
      if (response.status === 409) throw new ApiError(409, 'The requirements for this badge are not met. Refresh your progress and check that every required badge is current and not revoked.')
    }
    if (path.startsWith('/pathways')) {
      if (response.status === 409) throw new ApiError(409, 'Earn every prerequisite badge before enrolling. Each badge must be current and not revoked.')
      if (response.status === 400) throw new ApiError(400, 'Enter a name and description. Choose 1–50 completion badges and up to 50 different prerequisites, with no overlap.')
      if (response.status === 404) throw new ApiError(404, 'This pathway or enrollment is not available.')
    }
    if (path.startsWith('/achievements')) {
      if (response.status === 400) throw new Error('Enter a name and criteria within the displayed limits.')
      if (response.status === 409) throw new Error('This achievement changed, is already published, or has submissions that prevent editing. Refresh the catalog before trying again.')
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
  return response
}
