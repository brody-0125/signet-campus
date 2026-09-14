import { useState } from 'react'
import { manageAccount } from './auth'

export function Account() {
  const [error, setError] = useState('')
  const [opening, setOpening] = useState(false)
  async function open() {
    setError(''); setOpening(true)
    try { await manageAccount() }
    catch { setError('Unable to open account settings. Please try again.') }
    finally { setOpening(false) }
  }
  return <section className="container workspace account-page" aria-labelledby="account-title">
    <p className="eyebrow">Your account</p><h1 id="account-title">Keep access to your badges</h1>
    <article className="pathway-card"><h2>Manage your sign-in details</h2>
      <p>Open account settings to review your email, password and sign-in methods. Before losing access to a school or work email, use an address you can keep and complete any email verification requested.</p>
      <p>Changing your email on the same account keeps your submissions and badges. Existing signed badges retain their original recipient identifier. A different account does not gain access just because it uses the same email.</p>
      <button className="button primary" disabled={opening} onClick={() => void open()}>Manage account</button>
      {error && <p className="error" role="alert">{error}</p>}
    </article>
    <article className="pathway-card"><h2>Keep a copy of your achievements</h2>
      <p>Download signed JSON or PNG/SVG badges from My submissions or Pathways. Downloading does not publish them, and a saved copy does not guarantee that a badge remains valid.</p>
      <p>If your sign-in depends on an institution, arrange continued access with its account administrator before leaving. Updating an email alone does not preserve a disabled account or transfer badges to another sign-in provider.</p>
    </article>
  </section>
}
