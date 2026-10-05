import { useState } from 'react'
import { isConflict } from '../api/pokemonApi'
import { changesFrom, toForm, validate } from './localRecordForm'
import PokemonFormFields from './PokemonFormFields'

/**
 * "Local record" section of the detail panel, in the local source only: edits the record (PATCH, only the fields that
 * changed) and deletes it after a confirmation. Both carry the record's version (If-Match), so a change made
 * meanwhile by someone else is never overwritten nor deleted unseen.
 */
export default function LocalRecordEditor({ detail, onSave, onReload, onDelete }) {
  // null, 'editing' or 'deleting'
  const [mode, setMode] = useState(null)
  const [form, setForm] = useState(() => toForm(detail))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [conflict, setConflict] = useState(false)

  function start(nextMode) {
    setForm(toForm(detail))
    setError(null)
    setConflict(false)
    setMode(nextMode)
  }

  function cancel() {
    setMode(null)
    setError(null)
    setConflict(false)
  }

  function fail(err) {
    setError(err.message)
    setConflict(isConflict(err))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const problem = validate(form, detail)
    if (problem) {
      setError(problem)
      return
    }
    const changes = changesFrom(detail, form)
    if (Object.keys(changes).length === 0) {
      cancel()
      return
    }
    setBusy(true)
    setError(null)
    setConflict(false)
    try {
      await onSave(detail.id, changes)
      setMode(null)
    } catch (err) {
      fail(err)
    } finally {
      setBusy(false)
    }
  }

  async function confirmDelete() {
    setBusy(true)
    setError(null)
    setConflict(false)
    try {
      // On success the panel closes: this component goes away with it.
      await onDelete(detail.id)
    } catch (err) {
      fail(err)
      setBusy(false)
    }
  }

  // Escape cancels the edit or the deletion instead of closing the panel, so typed values are not lost by accident.
  function handleKeyDown(event) {
    if (event.key === 'Escape') {
      event.stopPropagation()
      if (!busy) cancel()
    }
  }

  const errorMessage = error && (
    <p className="local-form__error" role="alert">
      {error}
    </p>
  )

  return (
    <section className="detail__section" aria-labelledby="detail-record">
      <div className="detail__section-header">
        <h3 id="detail-record" className="detail__heading">Local record</h3>
        {mode === null && (
          <div className="detail__section-actions">
            <button type="button" className="detail__edit" onClick={() => start('editing')}>
              Edit Pokémon
            </button>
            {onDelete && (
              <button type="button" className="detail__edit detail__edit--danger" onClick={() => start('deleting')}>
                Delete Pokémon
              </button>
            )}
          </div>
        )}
      </div>

      {mode === null && (
        <p className="detail__empty">Stored in the local database: its name, picture, measures, description, stats and abilities can be edited.</p>
      )}

      {mode === 'deleting' && (
        <div className="local-form" role="group" aria-label="Confirm deletion" onKeyDown={handleKeyDown}>
          <p className="local-form__hint local-form__hint--strong">
            Delete <strong>{detail.name}</strong> from the local database? Its local data goes with it, and it is not
            copied back from PokeAPI unless you restore it.
          </p>
          {errorMessage}
          <div className="local-form__actions">
            {conflict ? (
              <button type="button" className="local-form__save" onClick={onReload}>
                Load latest version
              </button>
            ) : (
              <button type="button" className="local-form__danger" onClick={confirmDelete} disabled={busy} autoFocus>
                {busy ? 'Deleting…' : 'Yes, delete'}
              </button>
            )}
            <button type="button" className="local-form__cancel" onClick={cancel} disabled={busy}>
              Keep it
            </button>
          </div>
        </div>
      )}

      {mode === 'editing' && (
        <form className="local-form" onSubmit={handleSubmit} onKeyDown={handleKeyDown} noValidate>
          <PokemonFormFields form={form} setForm={setForm} disabled={busy} />
          <p className="local-form__hint">
            Only the fields you change are saved. Changing this data takes the record over: if it came from PokeAPI,
            it stops receiving PokeAPI updates.
          </p>
          {errorMessage}
          <div className="local-form__actions">
            {conflict ? (
              <button type="button" className="local-form__save" onClick={onReload}>
                Load latest version
              </button>
            ) : (
              <button type="submit" className="local-form__save" disabled={busy}>
                {busy ? 'Saving…' : 'Save'}
              </button>
            )}
            <button type="button" className="local-form__cancel" onClick={cancel} disabled={busy}>
              Cancel
            </button>
          </div>
        </form>
      )}
    </section>
  )
}
