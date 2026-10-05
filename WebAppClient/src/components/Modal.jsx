import { useEffect, useRef } from 'react'
import './PokemonDetailPanel.css'

const FOCUSABLE = 'button:not([disabled]), [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'

/**
 * Modal dialog shared by the detail panel and the creation form: focus moves into the dialog, Tab stays inside it,
 * the page behind does not scroll, Escape or a click outside closes it, and on close the focus returns to whatever
 * opened it. {@code initialFocus} (a selector) moves the focus to a field instead of the close button.
 */
export default function Modal({ labelledBy, label, onClose, initialFocus, children }) {
  const dialogRef = useRef(null)
  const closeRef = useRef(null)
  // The latest onClose, so a parent passing a new callback on every render does not re-run the setup below (which
  // would move the focus back to the close button while the user types).
  const onCloseRef = useRef(onClose)
  useEffect(() => {
    onCloseRef.current = onClose
  })

  useEffect(() => {
    const opener = document.activeElement
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    // The close button, unless the dialog names a first field (a form to fill in).
    const first = initialFocus ? dialogRef.current?.querySelector(initialFocus) : null
    ;(first ?? closeRef.current)?.focus()

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        onCloseRef.current()
        return
      }
      if (event.key !== 'Tab' || !dialogRef.current) return
      const focusable = [...dialogRef.current.querySelectorAll(FOCUSABLE)]
      if (focusable.length === 0) return
      const first = focusable[0]
      const last = focusable.at(-1)
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = previousOverflow
      if (opener instanceof HTMLElement && opener.isConnected) opener.focus()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- set up once per opening
  }, [])

  return (
    <div className="detail-overlay" onClick={onClose}>
      <section
        ref={dialogRef}
        className="detail"
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy}
        aria-label={labelledBy ? undefined : label}
        onClick={(event) => event.stopPropagation()}
      >
        <button ref={closeRef} type="button" className="detail__close" onClick={onClose} aria-label="Close">
          ×
        </button>
        {children}
      </section>
    </div>
  )
}
