// Same limits as the API (User Story 04), so most mistakes are caught before the request; the API checks them again.
export const LIMITS = {
  name: 100,
  image: 512,
  category: 100,
  description: 5000,
  childName: 100,
  stats: 20,
  skills: 20,
  statValue: 255,
  height: 1000,
  mass: 10000,
}

// PokeAPI's name format (mr-mime): the name is part of the record's URL.
const NAME_FORMAT = /^[a-z0-9]+(-[a-z0-9]+)*$/

function text(value) {
  return value === null || value === undefined ? '' : String(value)
}

export function toForm(detail) {
  return {
    name: text(detail.name),
    image: text(detail.image),
    category: text(detail.category),
    height: text(detail.height),
    mass: text(detail.mass),
    description: text(detail.description),
    stats: (detail.stats ?? []).map((stat) => ({ name: stat.name, value: text(stat.value) })),
    skills: (detail.skillLinks ?? []).map((skill) => skill.name),
  }
}

const normalizeName = (value) => value.trim().toLowerCase()

function isHttpUrl(value) {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

function hasDuplicates(names) {
  const normalized = names.map(normalizeName)
  return new Set(normalized).size !== normalized.length
}

// Returns the first problem of the form, or null. Like the API, only the fields being changed are checked, so a
// value stored before these rules existed does not block editing the others.
export function validate(form, detail) {
  const changes = changesFrom(detail, form)
  if ('name' in changes) {
    const name = normalizeName(form.name)
    if (!name) return 'Name is required.'
    if (name.length > LIMITS.name) return `Name must have at most ${LIMITS.name} characters.`
    if (!NAME_FORMAT.test(name)) return 'Name may only contain letters, digits and single hyphens between them (like mr-mime).'
    if (/^\d+$/.test(name)) return 'Name must contain a letter; a numeric name would be read as an id.'
  }
  if ('image' in changes && form.image.trim() && !isHttpUrl(form.image.trim())) {
    return 'Image must be an absolute http or https URL.'
  }
  for (const [key, label, max, unit] of [
    ['height', 'Height', LIMITS.height, 'm'],
    ['mass', 'Weight', LIMITS.mass, 'kg'],
  ]) {
    const raw = form[key].trim()
    if (!raw) {
      // PATCH cannot remove a measure: an empty field keeps the stored value, so do not let it look removed.
      if (detail[key] !== null && detail[key] !== undefined) return `${label} cannot be removed; enter a value.`
      continue
    }
    const value = Number(raw)
    if (key in changes && (!Number.isFinite(value) || value < 0 || value > max)) {
      return `${label} must be between 0 and ${max} ${unit}.`
    }
  }
  if ('stats' in changes) {
    if (form.stats.length > LIMITS.stats) return `A Pokémon may have at most ${LIMITS.stats} stats.`
    for (const stat of form.stats) {
      if (!stat.name.trim()) return 'Every stat needs a name.'
      const value = Number(stat.value)
      if (stat.value.trim() === '' || !Number.isInteger(value) || value < 0 || value > LIMITS.statValue) {
        return `Stat values must be whole numbers between 0 and ${LIMITS.statValue}.`
      }
    }
    if (hasDuplicates(form.stats.map((stat) => stat.name))) return 'Each stat may appear only once.'
  }
  if ('skills' in changes) {
    if (form.skills.length > LIMITS.skills) return `A Pokémon may have at most ${LIMITS.skills} abilities.`
    if (form.skills.some((skill) => !skill.trim())) return 'Every ability needs a name.'
    if (hasDuplicates(form.skills)) return 'Each ability may appear only once.'
  }
  return null
}

// Only what changed is sent (PATCH), so an untouched field is never rewritten.
export function changesFrom(detail, form) {
  const changes = {}
  if (normalizeName(form.name) !== text(detail.name)) changes.name = normalizeName(form.name)
  // Blank text clears the field.
  for (const key of ['image', 'category', 'description']) {
    if (form[key].trim() !== text(detail[key]).trim()) changes[key] = form[key].trim()
  }
  for (const key of ['height', 'mass']) {
    const raw = form[key].trim()
    if (raw && Number(raw) !== detail[key]) changes[key] = Number(raw)
  }
  const stats = form.stats.map((stat) => ({ name: normalizeName(stat.name), value: Number(stat.value) }))
  const currentStats = (detail.stats ?? []).map((stat) => ({ name: stat.name, value: stat.value }))
  if (JSON.stringify(stats) !== JSON.stringify(currentStats)) changes.stats = stats
  const skills = form.skills.map(normalizeName)
  const links = new Map((detail.skillLinks ?? []).map((skill) => [skill.name, skill.url]))
  if (JSON.stringify(skills) !== JSON.stringify((detail.skillLinks ?? []).map((skill) => skill.name))) {
    // Abilities that stay keep their link; new ones have none.
    changes.skills = skills.map((name) => ({ name, url: links.get(name) ?? null }))
  }
  return changes
}

// ---- Creation (POST): a new record owned by the local database ----

// Max lengths of the proprietary fields (database columns).
export const PROPRIETARY_LIMITS = { localizedName: 200, region: 100, classificationTag: 100 }

export function emptyCreateForm() {
  return { id: '', ...toForm({}), localizedName: '', region: '', classificationTag: '' }
}

// Returns the first problem of a creation form, or null. Same rules as an edit, plus the id and a required name.
export function validateNew(form) {
  const raw = form.id.trim()
  const id = Number(raw)
  if (!raw || !Number.isInteger(id) || id <= 0) return 'Id must be a positive whole number.'
  if (!normalizeName(form.name)) return 'Name is required.'
  return validate(form, {})
}

// The POST body: the id, the name and only the fields that were filled in.
export function toCreateRequest(form) {
  const request = { id: Number(form.id.trim()), ...changesFrom({}, form) }
  for (const key of Object.keys(PROPRIETARY_LIMITS)) {
    if (form[key].trim()) request[key] = form[key].trim()
  }
  return request
}
