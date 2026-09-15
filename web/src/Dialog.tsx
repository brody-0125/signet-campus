import { useEffect, useRef, type ReactNode } from 'react'

export function Dialog({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = ref.current!
    dialog.showModal()
    const original = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { dialog.close(); document.body.style.overflow = original }
  }, [])
  return <dialog ref={ref} aria-labelledby="dialog-title" onCancel={onClose}>
    <div className="dialog-heading"><h2 id="dialog-title">{title}</h2><button className="close-button" onClick={onClose} aria-label="Close">×</button></div>
    <div className="dialog-body">{children}</div>
  </dialog>
}
