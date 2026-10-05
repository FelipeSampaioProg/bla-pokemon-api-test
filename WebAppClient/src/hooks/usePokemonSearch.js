import { useCallback, useMemo, useRef, useState } from 'react'
import { fetchPokemonByName } from '../api/pokemonApi'

/**
 * The search box. Typing filters the page already loaded (no request); submitting looks the name or id up in the
 * selected source. The live filter is derived from the text and the page, not stored. Only the answer of the latest
 * search is shown, and the search has its own loading and error, apart from the list's.
 */
export default function usePokemonSearch(source, pageItems) {
  const [query, setQuery] = useState('')
  // null when not searching; otherwise { term, loading, error, result }
  const [search, setSearch] = useState(null)
  const latest = useRef(0)

  const liveFilterItems = useMemo(() => {
    const text = query.trim().toLowerCase()
    if (search !== null || !text) return null
    return pageItems.filter((item) => item.name.toLowerCase().includes(text))
  }, [query, search, pageItems])

  const find = useCallback(
    async (term, nameOrId = term) => {
      const request = ++latest.current
      setSearch((current) => ({ term, loading: true, error: null, result: current?.term === term ? current.result : null }))
      try {
        const result = await fetchPokemonByName(nameOrId, source)
        if (latest.current === request) setSearch({ term, loading: false, error: null, result })
      } catch (err) {
        if (latest.current === request) setSearch({ term, loading: false, error: err.message, result: null })
      }
    },
    [source],
  )

  const clear = useCallback(() => {
    latest.current++
    setSearch(null)
  }, [])

  const changeQuery = useCallback(
    (value) => {
      setQuery(value)
      if (!value.trim()) clear()
    },
    [clear],
  )

  // Enter / Search: an empty box goes back to the list.
  const submit = useCallback((trimmed) => (trimmed ? find(trimmed) : clear()), [find, clear])

  const reset = useCallback(() => {
    setQuery('')
    clear()
  }, [clear])

  // After the shown record changed (edited or deleted), show it as it is now, keeping the search term.
  const refresh = useCallback(
    (id) => {
      if (search !== null) find(search.term, String(id))
    },
    [search, find],
  )

  // A record shown by the search was deleted: it is known to be gone, so it is not asked for again (that request
  // would only answer 404, and the browser would log it as an error).
  const forget = useCallback((id) => {
    latest.current++
    setSearch((current) => (current?.result?.id === id ? { ...current, loading: false, error: null, result: null } : current))
  }, [])

  return { query, changeQuery, submit, reset, refresh, forget, search, liveFilterItems }
}
