import { create } from 'zustand'

type Workspace = {
  view: 'explore' | 'submissions'
  selectedId: string | null
  notice: string
  navigate: (view: Workspace['view']) => void
}
export const useWorkspace = create<Workspace>((set) => ({
  view: 'explore', selectedId: null, notice: '',
  navigate: (view) => set({ view, selectedId: null, notice: '' }),
}))
