import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api, type Achievement } from './api'
import { Dialog } from './Dialog'

export function ArchiveAchievement({ achievement }: { achievement: Achievement }) {
  const [confirming, setConfirming] = useState(false)
  const client = useQueryClient()
  const action = achievement.archived ? 'Restore' : 'Archive'
  const mutation = useMutation({
    mutationFn: () => api(`/achievements/${achievement.id}/archive`, { expectedVersion: achievement.version, archived: !achievement.archived }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['achievements'] })
      void client.invalidateQueries({ queryKey: ['pathways'] })
      setConfirming(false)
    },
  })
  return <>
    <button className="button outline" aria-label={`${action} ${achievement.name}`} onClick={() => { mutation.reset(); setConfirming(true) }}>{action}</button>
    {confirming && <Dialog title={`${action} achievement`} onClose={() => setConfirming(false)}>
      <h3>{achievement.name}</h3>
      <p className="field-help">{achievement.archived ? 'Restore this achievement to the catalog and resume submissions, resubmissions, badge issuance and pathway enrollment.' : 'Archiving hides this achievement from discovery and pauses new submissions, resubmissions and badge issuance. Pathways that require it pause new enrollment. Pending evidence can still be reviewed; unfinished work waits for restoration.'}</p>
      <p className="field-help">Existing badges are not revoked. Evidence, criteria and pathway progress are retained. Learners who already hold every required badge can still claim their pathway award.</p>
      {mutation.isError && <p className="error" role="alert">{mutation.error.message}</p>}
      <div className="form-actions"><button className="button outline" onClick={() => setConfirming(false)}>Cancel</button>
        <button className="button primary" disabled={mutation.isPending} onClick={() => mutation.mutate()}>{mutation.isPending ? 'Saving…' : `${action} achievement`}</button></div>
    </Dialog>}
  </>
}
