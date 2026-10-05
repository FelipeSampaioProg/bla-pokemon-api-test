const API_ROOT = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

export const SOURCES = {
  REMOTE: 'remote',
  LOCAL: 'local',
}

// Each source is its own REST resource: PokeAPI data is read-only, local records support full CRUD.
const RESOURCE_URLS = {
  [SOURCES.REMOTE]: `${API_ROOT}/pokemons`,
  [SOURCES.LOCAL]: `${API_ROOT}/local-pokemons`,
}

function resourceUrl(source) {
  const url = RESOURCE_URLS[source]
  if (!url) throw new Error(`Unknown data source: ${source}`)
  return url
}

function normalizeListItem(item) {
  return {
    id: item.id,
    name: item.name,
    image: item.image,
    subtitle: item.category,
    mass: item.mass,
    tags: (item.skills ?? []).map((skill) => skill.name),
    // Older responses have no flags: treat them as available.
    available: item.available !== false,
    // false when only the species failed: the rest of the card is still shown, as in the detail view.
    categoryAvailable: item.available !== false && item.categoryAvailable !== false,
  }
}

// Exact search result shown as a card: same fields as a list card, so a Pokemon looks the same in both.
function normalizeDetailAsCard(detail) {
  return {
    id: detail.id,
    name: detail.name,
    image: detail.image,
    subtitle: detail.category ?? null,
    mass: detail.mass,
    tags: (detail.skills ?? []).map((skill) => skill.name),
    available: true,
    // The category comes from the species, like the description.
    categoryAvailable: detail.descriptionAvailable !== false,
  }
}

// The one message shown wherever PokeAPI's rate limit hides data: list, detail panel and errors.
export function rateLimitMessage(seconds) {
  return `PokeAPI is limiting requests; try again in ${seconds} ${seconds === 1 ? 'second' : 'seconds'}.`
}

function retryAfterSeconds(response) {
  const value = Number.parseInt(response.headers?.get?.('Retry-After') ?? '', 10)
  return Number.isFinite(value) && value > 0 ? value : null
}

// Errors come as application/problem+json; use its "detail" when present. A 503 with Retry-After is PokeAPI's
// rate limit: show the same message as the list and the detail panel, with the wait.
async function buildError(response, message) {
  let detail = null
  let title = null
  let deleted = false
  try {
    const body = await response.json()
    detail = body?.detail ?? null
    title = body?.title ?? null
    deleted = body?.deleted === true
  } catch {
    // Body is empty or not JSON: keep the generic message.
  }
  const wait = response.status === 503 ? retryAfterSeconds(response) : null
  if (wait) {
    detail = rateLimitMessage(wait)
  }
  const suffix = `(HTTP ${response.status})`
  const error = new Error(detail ? `${message}: ${detail} ${suffix}` : `${message} ${suffix}`)
  error.status = response.status
  error.title = title
  // 404 for a record deleted through the API: the UI can offer to restore it.
  error.deleted = deleted
  return error
}

// The record changed since it was read: 412 (If-Match did not match) or 409 "Concurrent modification" (another write
// won the race inside the server). Both mean the same to the user, and reloading the record solves them; a 409 for a
// duplicate name does not, so it is not one of them.
export function isConflict(error) {
  return error?.status === 412 || (error?.status === 409 && error?.title === 'Concurrent modification')
}

function etagOf(response) {
  return response.headers?.get?.('ETag') ?? null
}

// Writes to a local record. With the ETag read before, the server refuses the write (412) if someone changed the
// record in between, instead of silently overwriting that change.
function writeHeaders(etag) {
  return etag ? { 'Content-Type': 'application/json', 'If-Match': etag } : { 'Content-Type': 'application/json' }
}

export async function fetchPokemonPage(page, size, source = SOURCES.REMOTE) {
  const response = await fetch(`${resourceUrl(source)}?page=${page}&size=${size}`)
  if (!response.ok) {
    throw await buildError(response, 'Could not load the Pokémon list')
  }
  const data = await response.json()
  return {
    items: data.content.map(normalizeListItem),
    page: data.page,
    size: data.size,
    totalElements: data.totalElements,
    totalPages: data.totalPages,
    // Seconds until PokeAPI stops rate limiting us; only present when entries are missing for that reason.
    retryAfterSeconds: data.retryAfterSeconds ?? null,
  }
}

function normalizeLineage(node) {
  if (!node) return null
  return {
    name: node.name,
    evolvesTo: (node.evolvesTo ?? []).map(normalizeLineage),
  }
}

async function requestDetail(name, source) {
  const response = await fetch(`${resourceUrl(source)}/${encodeURIComponent(name)}`)
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw await buildError(response, 'Could not load the Pokémon')
  }
  return { data: await response.json(), etag: etagOf(response) }
}

// Exact search: the detail shaped as a list card.
export async function fetchPokemonByName(name, source = SOURCES.REMOTE) {
  const result = await requestDetail(name, source)
  return result ? normalizeDetailAsCard(result.data) : null
}

// Detail view: every field of the detail, nothing truncated.
export async function fetchPokemonDetail(name, source = SOURCES.REMOTE) {
  const result = await requestDetail(name, source)
  if (!result) return null
  const { data } = result
  return {
    id: data.id,
    name: data.name,
    image: data.image,
    height: data.height,
    mass: data.mass,
    description: data.description,
    category: data.category ?? null,
    skills: (data.skills ?? []).map((skill) => skill.name),
    stats: data.stats ?? [],
    // How this Pokemon appears in the lineage (lycanroc for lycanroc-midday).
    species: data.species ?? data.name,
    lineage: normalizeLineage(data.evolutionLineage),
    // Older responses have no flags: treat the data as available.
    evolutionAvailable: data.evolutionAvailable !== false,
    descriptionAvailable: data.descriptionAvailable !== false,
    // Seconds until PokeAPI stops rate limiting us; only present when that is why data is missing.
    retryAfterSeconds: data.retryAfterSeconds ?? null,
    // Proprietary fields added locally (localized name, region, classification tag); null when none.
    metadata: data.metadata ?? null,
    // Abilities with their links, so an edit of a local record keeps the links of the abilities it keeps.
    skillLinks: (data.skills ?? []).map((skill) => ({ name: skill.name, url: skill.url ?? null })),
    // Version of the local record, sent back as If-Match when saving. PokeAPI details are not local records.
    localEtag: source === SOURCES.LOCAL ? result.etag : null,
  }
}

// The local record behind a PokeAPI detail: its ETag, sent as If-Match when its proprietary fields are saved, and the
// proprietary fields of that same version. null when the Pokemon is not in the local store yet (saving replicates it).
export async function fetchLocalVersion(id) {
  const result = await requestDetail(String(id), SOURCES.LOCAL)
  if (!result) return null
  return { etag: result.etag, metadata: result.data.metadata ?? null }
}

// Saves the proprietary fields of a Pokemon (User Story 03). Only these fields are sent, so the Pokemon keeps
// receiving PokeAPI updates, and the server replicates it first if it was never stored locally: this works for
// any PokeAPI Pokemon, not only local ones. An empty value removes the field. Returns the saved fields (null when
// none is left) and the record's new ETag.
export async function updateLocalData(id, { localizedName, region, classificationTag }, etag = null) {
  const response = await fetch(`${resourceUrl(SOURCES.LOCAL)}/${id}`, {
    method: 'PATCH',
    headers: writeHeaders(etag),
    body: JSON.stringify({
      localizedName: localizedName ?? '',
      region: region ?? '',
      classificationTag: classificationTag ?? '',
    }),
  })
  if (!response.ok) {
    throw await buildError(response, 'Could not save the local data')
  }
  // The answer is the record as GET returns it: the proprietary fields are in "metadata", absent when none is set.
  const data = await response.json()
  return { metadata: data.metadata ?? null, etag: etagOf(response) }
}

// Brings a Pokemon back from PokeAPI into the local store (POST /{id}/sync), which also lifts an earlier deletion.
// An explicit user action, as the deletion was. Returns the record's ETag.
export async function restoreFromPokeApi(id) {
  const response = await fetch(`${resourceUrl(SOURCES.LOCAL)}/${id}/sync`, { method: 'POST' })
  if (!response.ok) {
    throw await buildError(response, 'Could not restore the Pokémon from PokeAPI')
  }
  return { etag: etagOf(response) }
}

// Edits a local record (User Story 04): sends only the fields that changed, with the ETag read before. Changing
// PokeAPI data (name, image, stats...) takes the record over: the PokeAPI synchronisation stops updating it.
// Returns the record's new ETag.
export async function updateLocalPokemon(id, changes, etag = null) {
  const response = await fetch(`${resourceUrl(SOURCES.LOCAL)}/${id}`, {
    method: 'PATCH',
    headers: writeHeaders(etag),
    body: JSON.stringify(changes),
  })
  if (!response.ok) {
    throw await buildError(response, 'Could not save the Pokémon')
  }
  return { etag: etagOf(response) }
}

// Creates a record owned by the local database (POST). Neither its id nor its name may belong to a Pokemon of
// PokeAPI: to add local data to one of those, enrich it from its detail instead. Returns the new record's id.
export async function createLocalPokemon(request) {
  const response = await fetch(resourceUrl(SOURCES.LOCAL), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) {
    throw await buildError(response, 'Could not create the Pokémon')
  }
  const data = await response.json()
  return { id: data.id, etag: etagOf(response) }
}

// Deletes a local record (DELETE), with the ETag read before: a record changed meanwhile is not deleted (412). The
// deletion is remembered, so the PokeAPI synchronisation does not bring the record back.
export async function deleteLocalPokemon(id, etag = null) {
  const response = await fetch(`${resourceUrl(SOURCES.LOCAL)}/${id}`, {
    method: 'DELETE',
    headers: etag ? { 'If-Match': etag } : {},
  })
  if (!response.ok) {
    throw await buildError(response, 'Could not delete the Pokémon')
  }
}
