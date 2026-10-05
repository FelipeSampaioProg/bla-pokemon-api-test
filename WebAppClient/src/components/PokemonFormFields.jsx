import { LIMITS, PROPRIETARY_LIMITS } from './localRecordForm'

/**
 * The fields of a local record, shared by the edit form and the creation form. {@code form} is the form state (see
 * localRecordForm.js) and {@code setForm} its updater. The creation form also shows the id and the proprietary
 * fields; the edit form leaves those out (the id never changes, and "Local data" edits the proprietary fields).
 */
export default function PokemonFormFields({ form, setForm, disabled, withIdAndLocalData = false }) {
  const setField = (key) => (event) => setForm((current) => ({ ...current, [key]: event.target.value }))

  function setStat(index, key, value) {
    setForm((current) => ({
      ...current,
      stats: current.stats.map((stat, i) => (i === index ? { ...stat, [key]: value } : stat)),
    }))
  }

  function setSkill(index, value) {
    setForm((current) => ({ ...current, skills: current.skills.map((skill, i) => (i === index ? value : skill)) }))
  }

  const removeAt = (key, index) =>
    setForm((current) => ({ ...current, [key]: current[key].filter((_, i) => i !== index) }))

  return (
    <>
      {withIdAndLocalData && (
        <label className="local-form__field">
          <span>Id</span>
          <input type="number" min="1" step="1" value={form.id} disabled={disabled} onChange={setField('id')} />
        </label>
      )}
      <label className="local-form__field">
        <span>Name</span>
        <input type="text" value={form.name} maxLength={LIMITS.name} disabled={disabled} autoFocus={!withIdAndLocalData} onChange={setField('name')} />
      </label>
      <label className="local-form__field">
        <span>Image URL</span>
        <input type="url" value={form.image} maxLength={LIMITS.image} disabled={disabled} onChange={setField('image')} />
      </label>
      <label className="local-form__field">
        <span>Category</span>
        <input type="text" value={form.category} maxLength={LIMITS.category} disabled={disabled} onChange={setField('category')} />
      </label>
      <div className="local-form__pair">
        <label className="local-form__field">
          <span>Height (m)</span>
          <input type="number" min="0" max={LIMITS.height} step="any" value={form.height} disabled={disabled} onChange={setField('height')} />
        </label>
        <label className="local-form__field">
          <span>Weight (kg)</span>
          <input type="number" min="0" max={LIMITS.mass} step="any" value={form.mass} disabled={disabled} onChange={setField('mass')} />
        </label>
      </div>
      <label className="local-form__field">
        <span>Description</span>
        <textarea rows={3} value={form.description} maxLength={LIMITS.description} disabled={disabled} onChange={setField('description')} />
      </label>

      <fieldset className="local-form__group">
        <legend>Stats</legend>
        {form.stats.map((stat, index) => (
          <div key={index} className="local-form__row">
            <input type="text" aria-label={`Stat ${index + 1} name`} value={stat.name} maxLength={LIMITS.childName} disabled={disabled} onChange={(event) => setStat(index, 'name', event.target.value)} />
            <input type="number" aria-label={`Stat ${index + 1} value`} min="0" max={LIMITS.statValue} step="1" value={stat.value} disabled={disabled} onChange={(event) => setStat(index, 'value', event.target.value)} />
            <button type="button" className="local-form__remove" aria-label={`Remove stat ${stat.name || index + 1}`} disabled={disabled} onClick={() => removeAt('stats', index)}>
              ×
            </button>
          </div>
        ))}
        {form.stats.length < LIMITS.stats && (
          <button type="button" className="local-form__add" disabled={disabled} onClick={() => setForm((current) => ({ ...current, stats: [...current.stats, { name: '', value: '' }] }))}>
            Add stat
          </button>
        )}
      </fieldset>

      <fieldset className="local-form__group">
        <legend>Abilities</legend>
        {form.skills.map((skill, index) => (
          <div key={index} className="local-form__row">
            <input type="text" aria-label={`Ability ${index + 1}`} value={skill} maxLength={LIMITS.childName} disabled={disabled} onChange={(event) => setSkill(index, event.target.value)} />
            <button type="button" className="local-form__remove" aria-label={`Remove ability ${skill || index + 1}`} disabled={disabled} onClick={() => removeAt('skills', index)}>
              ×
            </button>
          </div>
        ))}
        {form.skills.length < LIMITS.skills && (
          <button type="button" className="local-form__add" disabled={disabled} onClick={() => setForm((current) => ({ ...current, skills: [...current.skills, ''] }))}>
            Add ability
          </button>
        )}
      </fieldset>

      {withIdAndLocalData && (
        <fieldset className="local-form__group">
          <legend>Local data</legend>
          <label className="local-form__field">
            <span>Localized name</span>
            <input type="text" value={form.localizedName} maxLength={PROPRIETARY_LIMITS.localizedName} disabled={disabled} onChange={setField('localizedName')} />
          </label>
          <label className="local-form__field">
            <span>Region</span>
            <input type="text" value={form.region} maxLength={PROPRIETARY_LIMITS.region} disabled={disabled} onChange={setField('region')} />
          </label>
          <label className="local-form__field">
            <span>Classification</span>
            <input type="text" value={form.classificationTag} maxLength={PROPRIETARY_LIMITS.classificationTag} disabled={disabled} onChange={setField('classificationTag')} />
          </label>
        </fieldset>
      )}
    </>
  )
}
