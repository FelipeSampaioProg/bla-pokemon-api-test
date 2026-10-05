import { useState } from 'react'
import { emptyCreateForm, toCreateRequest, validateNew } from './localRecordForm'
import Modal from './Modal'
import PokemonFormFields from './PokemonFormFields'

/**
 * Creates a record owned by the local database (POST). Checks the same rules as the API before sending; the API's
 * answer (for example an id or a name that belongs to a PokeAPI Pokemon) is shown in the form, which stays open.
 */
export default function CreatePokemonDialog({ onCreate, onClose }) {
  const [form, setForm] = useState(emptyCreateForm)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  async function handleSubmit(event) {
    event.preventDefault()
    const problem = validateNew(form)
    if (problem) {
      setError(problem)
      return
    }
    setSaving(true)
    setError(null)
    try {
      // On success the parent closes the dialog.
      await onCreate(toCreateRequest(form))
    } catch (err) {
      setError(err.message)
      setSaving(false)
    }
  }

  return (
    <Modal labelledBy="create-title" initialFocus="input" onClose={() => !saving && onClose()}>
      <h2 id="create-title" className="detail__name">New Pokémon</h2>
      <p className="detail__empty">
        Creates a record that belongs to the local database. To add local data to a Pokémon of PokeAPI, open it and use
        "Local data" instead.
      </p>
      <form className="local-form" onSubmit={handleSubmit} noValidate>
        <PokemonFormFields form={form} setForm={setForm} disabled={saving} withIdAndLocalData />
        {error && (
          <p className="local-form__error" role="alert">
            {error}
          </p>
        )}
        <div className="local-form__actions">
          <button type="submit" className="local-form__save" disabled={saving}>
            {saving ? 'Creating…' : 'Create'}
          </button>
          <button type="button" className="local-form__cancel" onClick={onClose} disabled={saving}>
            Cancel
          </button>
        </div>
      </form>
    </Modal>
  )
}
