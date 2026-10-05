import { useState } from 'react'
import { isConflict } from '../api/pokemonApi'

// Proprietary fields (User Story 03): kept on this server, not in PokeAPI. Max lengths match the database columns.
const FIELDS = [
  { key: 'localizedName', label: 'Localized name', maxLength: 200 },
  { key: 'region', label: 'Region', maxLength: 100 },
  { key: 'classificationTag', label: 'Classification', maxLength: 100 },
]

function formValues(metadata) {
  return Object.fromEntries(FIELDS.map((field) => [field.key, metadata?.[field.key] ?? '']))
}

export default function LocalDataSection({ pokemonId, metadata, onSave, onReload, onRestore }) {
  const [editing, setEditing] = useState(false)
  const [values, setValues] = useState(() => formValues(metadata))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const [conflict, setConflict] = useState(false)
  // The record was deleted through the API: saving needs an explicit restore first.
  const [deleted, setDeleted] = useState(false)

  const hasData = Boolean(metadata) && FIELDS.some((field) => metadata[field.key])
  if (!hasData && !onSave) return null

  function startEditing() {
    setValues(formValues(metadata))
    setError(null)
    setConflict(false)
    setDeleted(false)
    setEditing(true)
  }

  function cancel() {
    setEditing(false)
    setError(null)
    setConflict(false)
    setDeleted(false)
  }

  // Someone changed the record meanwhile: drop this edit and show the latest values.
  function loadLatest() {
    cancel()
    onReload()
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    setConflict(false)
    try {
      await onSave(pokemonId, values)
      setEditing(false)
    } catch (err) {
      setError(err.message)
      setConflict(Boolean(onReload) && isConflict(err))
      setDeleted(Boolean(onRestore) && err.deleted === true)
    } finally {
      setSaving(false)
    }
  }

  async function restoreAndSave() {
    setSaving(true)
    setError(null)
    try {
      await onRestore(pokemonId, values)
      cancel()
    } catch (err) {
      setError(err.message)
      setDeleted(false)
    } finally {
      setSaving(false)
    }
  }

  // Escape while editing cancels the edit instead of closing the panel, so typed values are not lost by accident.
  function handleKeyDown(event) {
    if (event.key === 'Escape') {
      event.stopPropagation()
      if (!saving) cancel()
    }
  }

  return (
    <section className="detail__section" aria-labelledby="detail-local">
      <div className="detail__section-header">
        <h3 id="detail-local" className="detail__heading">Local data</h3>
        {onSave && !editing && (
          <button type="button" className="detail__edit" onClick={startEditing}>
            {hasData ? 'Edit' : 'Add local data'}
          </button>
        )}
      </div>

      {!editing && hasData && (
        <dl className="detail__local">
          {FIELDS.filter((field) => metadata[field.key]).map((field) => (
            <div key={field.key} className="detail__local-row">
              <dt>{field.label}</dt>
              <dd>{metadata[field.key]}</dd>
            </div>
          ))}
        </dl>
      )}

      {!editing && !hasData && (
        <p className="detail__empty">
          No local data yet. Add a localized name, region or classification kept on this server.
        </p>
      )}

      {editing && (
        <form className="local-form" onSubmit={handleSubmit} onKeyDown={handleKeyDown}>
          {FIELDS.map((field, index) => (
            <label key={field.key} className="local-form__field">
              <span>{field.label}</span>
              <input
                type="text"
                value={values[field.key]}
                maxLength={field.maxLength}
                disabled={saving}
                autoFocus={index === 0}
                onChange={(event) => setValues((current) => ({ ...current, [field.key]: event.target.value }))}
              />
            </label>
          ))}
          <p className="local-form__hint">
            Leave a field empty to remove it. These fields are kept on this server; the Pokémon keeps receiving
            PokeAPI updates.
          </p>
          {error && (
            <p className="local-form__error" role="alert">
              {error}
            </p>
          )}
          <div className="local-form__actions">
            {deleted ? (
              <button type="button" className="local-form__save" onClick={restoreAndSave} disabled={saving}>
                {saving ? 'Restoring…' : 'Restore from PokeAPI and save'}
              </button>
            ) : conflict ? (
              <button type="button" className="local-form__save" onClick={loadLatest}>
                Load latest version
              </button>
            ) : (
              <button type="submit" className="local-form__save" disabled={saving}>
                {saving ? 'Saving…' : 'Save'}
              </button>
            )}
            <button type="button" className="local-form__cancel" onClick={cancel} disabled={saving}>
              Cancel
            </button>
          </div>
        </form>
      )}
    </section>
  )
}
