import { useCallback, useState } from 'react'
import {
  SOURCES,
  deleteLocalPokemon,
  fetchLocalVersion,
  fetchPokemonDetail,
  restoreFromPokeApi,
  updateLocalData,
  updateLocalPokemon,
} from '../api/pokemonApi'

/**
 * The detail panel and every write made from it: local data, full edit, deletion and restore. Writes carry the local
 * record's version (If-Match), so a change made meanwhile by someone else is not overwritten.
 * {@code onRecordChanged(id, { deleted })} lets the list and the search result show a record that was edited or
 * deleted.
 */
export default function useDetailPanel(source, onRecordChanged) {
  // null = closed; otherwise { requested, loading, error, detail }
  const [state, setState] = useState(null)

  // Updates the detail being shown, if it is still the one with this id.
  const updateShown = useCallback((id, update) => {
    setState((current) => (current?.detail?.id === id ? update(current) : current))
  }, [])

  // A PokeAPI detail does not carry the version of the local record behind it. Read it, together with the proprietary
  // fields of that same version, so saving them is protected by If-Match as in the local source. If the local store
  // cannot be read, saving still works, without the protection.
  const attachLocalVersion = useCallback(
    async (id) => {
      try {
        const local = await fetchLocalVersion(id)
        updateShown(id, (current) => ({
          ...current,
          detail: {
            ...current.detail,
            localEtag: local?.etag ?? null,
            metadata: local ? local.metadata : current.detail.metadata,
          },
        }))
      } catch {
        // Local store unreachable: keep what the PokeAPI detail brought.
      }
    },
    [updateShown],
  )

  const open = useCallback(
    async (name) => {
      setState({ requested: name, loading: true, error: null, detail: null })
      try {
        const detail = await fetchPokemonDetail(name, source)
        // Ignore a late answer for a Pokemon the user already moved away from.
        setState((current) =>
          current?.requested === name ? { requested: name, loading: false, error: null, detail } : current,
        )
        if (detail && source === SOURCES.REMOTE) {
          attachLocalVersion(detail.id)
        }
      } catch (err) {
        setState((current) =>
          current?.requested === name ? { requested: name, loading: false, error: err.message, detail: null } : current,
        )
      }
    },
    [source, attachLocalVersion],
  )

  const close = useCallback(() => setState(null), [])

  // Loads the detail again without closing the panel: after an edit, or to discard one that hit a newer version.
  const refresh = useCallback(
    async (id) => {
      try {
        const detail = await fetchPokemonDetail(String(id), source)
        updateShown(id, (current) => ({ ...current, error: null, detail }))
      } catch (err) {
        updateShown(id, (current) => ({ ...current, error: err.message, detail: null }))
      }
    },
    [source, updateShown],
  )

  // Proprietary fields ("Local data"); errors are shown by the form itself.
  const saveLocalData = useCallback(
    async (id, values, etag) => {
      const saved = await updateLocalData(id, values, etag)
      updateShown(id, (current) => ({
        ...current,
        detail: { ...current.detail, metadata: saved.metadata, localEtag: saved.etag ?? current.detail.localEtag },
      }))
    },
    [updateShown],
  )

  // Restores a Pokemon deleted through the API, then saves the local data typed, under the restored record's version.
  const restoreLocalData = useCallback(
    async (id, values) => {
      const restored = await restoreFromPokeApi(id)
      await saveLocalData(id, values, restored.etag)
    },
    [saveLocalData],
  )

  // After a save hit a newer version: the whole record in the local source, the local fields in the PokeAPI one.
  const reloadLocalData = useCallback(
    (id) => (source === SOURCES.LOCAL ? refresh(id) : attachLocalVersion(id)),
    [source, refresh, attachLocalVersion],
  )

  // Full edit of a local record (User Story 04).
  const saveRecord = useCallback(
    async (id, changes, etag) => {
      await updateLocalPokemon(id, changes, etag)
      await refresh(id)
      onRecordChanged(id)
    },
    [refresh, onRecordChanged],
  )

  // Deletes a local record; the panel closes and the list no longer shows it.
  const deleteRecord = useCallback(
    async (id, etag) => {
      await deleteLocalPokemon(id, etag)
      setState(null)
      onRecordChanged(id, { deleted: true })
    },
    [onRecordChanged],
  )

  return {
    state,
    open,
    close,
    refresh,
    saveLocalData,
    restoreLocalData,
    reloadLocalData,
    saveRecord,
    deleteRecord,
  }
}
