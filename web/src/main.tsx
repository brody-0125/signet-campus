import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import '@fontsource/inter/400.css'
import './styles.css'
import { App } from './App'
import { initializeSession, useSession } from './auth'

const client = new QueryClient({ defaultOptions: { queries: { staleTime: 30_000, retry: 1 } } })
useSession.subscribe((state, previous) => {
  if (previous.authenticated && !state.authenticated) client.clear()
})
createRoot(document.getElementById('root')!).render(<StrictMode><QueryClientProvider client={client}><App /></QueryClientProvider></StrictMode>)
void initializeSession()
