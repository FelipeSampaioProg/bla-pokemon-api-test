import { useCallback, useEffect, useState } from 'react'
import { SOURCES, fetchPokemonPage } from '../api/pokemonApi'

const DEFAULT_SIZE = 20
const EMPTY_PAGE = { items: [], totalPages: 0, totalElements: 0, retryAfterSeconds: null }

/**
 * The page being browsed: source, page, size, and the answer for them. A response that arrives after the user moved
 * to another page, size or source is discarded, so the list never shows data of a request that is no longer current
 * (a slow PokeAPI page used to replace the local one when the source was switched while it loaded).
 */
export default function usePokemonPage() {
  const [source, setSource] = useState(SOURCES.REMOTE)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(DEFAULT_SIZE)
  // Bumped to load the same page again (after a record was created, edited or deleted).
  const [version, setVersion] = useState(0)
  // The last answer and the request it belongs to.
  const [result, setResult] = useState({ key: null, data: EMPTY_PAGE, error: null })

  const key = `${source}|${page}|${size}|${version}`

  useEffect(() => {
    let current = true
    fetchPokemonPage(page, size, source)
      .then((data) => current && setResult({ key, data, error: null }))
      .catch((err) => current && setResult({ key, data: EMPTY_PAGE, error: err.message }))
    return () => {
      current = false
    }
  }, [key, page, size, source])

  const loading = result.key !== key

  const changeSource = useCallback((next) => {
    setSource(next)
    // Page 7 of PokeAPI has nothing to do with page 7 of the local database.
    setPage(0)
  }, [])

  const changeSize = useCallback((next) => {
    setSize(next)
    setPage(0)
  }, [])

  const reload = useCallback(() => setVersion((current) => current + 1), [])

  return {
    source,
    page,
    size,
    pageData: loading ? EMPTY_PAGE : result.data,
    loading,
    error: loading ? null : result.error,
    changeSource,
    changePage: setPage,
    changeSize,
    reload,
  }
}
