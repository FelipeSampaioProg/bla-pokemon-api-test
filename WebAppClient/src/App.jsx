import { useCallback, useState } from 'react'
import SearchBar from './components/SearchBar'
import PokemonCard from './components/PokemonCard'
import Pagination from './components/Pagination'
import SourceToggle from './components/SourceToggle'
import PokemonDetailPanel from './components/PokemonDetailPanel'
import CreatePokemonDialog from './components/CreatePokemonDialog'
import { SOURCES, createLocalPokemon, rateLimitMessage } from './api/pokemonApi'
import usePokemonPage from './hooks/usePokemonPage'
import usePokemonSearch from './hooks/usePokemonSearch'
import useDetailPanel from './hooks/useDetailPanel'
import './App.css'

// The page itself: puts together the list (usePokemonPage), the search (usePokemonSearch) and the detail panel
// (useDetailPanel), and decides what to show. The state of each lives in its hook.
function App() {
  const list = usePokemonPage()
  const search = usePokemonSearch(list.source, list.pageData.items)
  const [creating, setCreating] = useState(false)

  const { reload: reloadList } = list
  const { refresh: refreshSearch, forget: forgetInSearch, search: searchState } = search
  // A record was edited or deleted from the panel: show the list and the search result as they are now. The list is
  // reloaded even while a search is shown, so going back to it never shows the record as it was. A deleted record is
  // simply dropped from the search result: it is known to be gone.
  const recordChanged = useCallback(
    (id, { deleted = false } = {}) => {
      reloadList()
      if (searchState === null) return
      if (deleted) forgetInSearch(id)
      else refreshSearch(id)
    },
    [searchState, refreshSearch, forgetInSearch, reloadList],
  )
  const detail = useDetailPanel(list.source, recordChanged)

  const { open: openDetail } = detail
  // Creates a local record, then shows it in the list and opens its detail.
  const createPokemon = useCallback(
    async (request) => {
      const created = await createLocalPokemon(request)
      setCreating(false)
      reloadList()
      openDetail(String(created.id))
    },
    [reloadList, openDetail],
  )
  const closeCreate = useCallback(() => setCreating(false), [])

  function handleSourceChange(nextSource) {
    if (nextSource === list.source) return
    list.changeSource(nextSource)
    search.reset()
    detail.close()
    setCreating(false)
  }

  function handlePageChange(nextPage) {
    if (nextPage >= 0 && nextPage < list.pageData.totalPages) {
      list.changePage(nextPage)
    }
  }

  const { source, pageData } = list
  const isLocal = source === SOURCES.LOCAL
  const isSearching = searchState !== null
  const isLiveFiltering = !isSearching && search.liveFilterItems !== null
  const isBrowsing = !isSearching && !isLiveFiltering
  const isLoading = isSearching ? searchState.loading : list.loading
  const error = isSearching ? searchState.error : list.error
  const cardsToShow = isSearching
    ? (searchState.result ? [searchState.result] : [])
    : isLiveFiltering
      ? search.liveFilterItems
      : pageData.items
  const unavailableCount = isBrowsing ? pageData.items.filter((item) => item.available === false).length : 0
  // Entries shown without their category (only the species failed).
  const partialCount = isBrowsing
    ? pageData.items.filter((item) => item.available !== false && item.categoryAvailable === false).length
    : 0

  return (
    <div className="app">
      <header className="app__header">
        <h1 className="app__title">Pokedex</h1>
        <p className="app__subtitle">Find your favorite Pokémon by name or id</p>
        <SearchBar value={search.query} onChange={search.changeQuery} onSubmit={search.submit} />
        <SourceToggle value={source} onChange={handleSourceChange} />
        {isLocal && (
          <div className="app__toolbar">
            <button type="button" className="app__create" onClick={() => setCreating(true)}>
              + New Pokémon
            </button>
          </div>
        )}
      </header>

      <main className="app__main">
        {isLoading && <p className="app__status">Loading...</p>}
        {!isLoading && error && <p className="app__status app__status--error">{error}</p>}
        {!isLoading && !error && isSearching && !searchState.result && (
          <p className="app__status">
            No Pokémon named "{searchState.term}" was found
            {isLocal ? ' in the local database' : ''}.
          </p>
        )}
        {!isLoading && !error && isLiveFiltering && search.liveFilterItems.length === 0 && (
          <p className="app__status">No matches for "{search.query.trim()}".</p>
        )}
        {!isLoading && !error && isBrowsing && isLocal && pageData.items.length === 0 && (
          <p className="app__status">There are no Pokémon in the local database yet.</p>
        )}
        {!isLoading && !error && unavailableCount + partialCount > 0 && (
          <p className="app__status app__status--warning" role="status">
            {unavailableCount === 1 && '1 Pokémon could not be loaded from PokeAPI. '}
            {unavailableCount > 1 && `${unavailableCount} Pokémon could not be loaded from PokeAPI. `}
            {partialCount === 1 && 'The category of 1 Pokémon could not be loaded. '}
            {partialCount > 1 && `The category of ${partialCount} Pokémon could not be loaded. `}
            {pageData.retryAfterSeconds
              ? rateLimitMessage(pageData.retryAfterSeconds)
              : 'Try reloading the page in a few minutes.'}
          </p>
        )}
        {!isLoading && !error && cardsToShow.length > 0 && (
          <div className="app__grid">
            {cardsToShow.map((pokemon) => (
              <PokemonCard key={pokemon.id} pokemon={pokemon} onSelect={() => openDetail(pokemon.name)} />
            ))}
          </div>
        )}
      </main>

      {isBrowsing && !isLoading && !error && pageData.totalPages > 0 && (
        <Pagination
          page={list.page}
          size={list.size}
          totalPages={pageData.totalPages}
          totalElements={pageData.totalElements}
          onPageChange={handlePageChange}
          onSizeChange={list.changeSize}
        />
      )}

      {/* Local records have no evolution data, so lineage names only navigate within PokeAPI. */}
      {detail.state && (
        <PokemonDetailPanel
          state={detail.state}
          onClose={detail.close}
          onSaveLocalData={detail.saveLocalData}
          onReloadLocalData={detail.reloadLocalData}
          onRestoreLocalData={detail.restoreLocalData}
          onSaveRecord={isLocal ? detail.saveRecord : undefined}
          onReloadRecord={detail.refresh}
          onDeleteRecord={isLocal ? detail.deleteRecord : undefined}
          onSelectEvolution={isLocal ? undefined : openDetail}
        />
      )}

      {creating && <CreatePokemonDialog onCreate={createPokemon} onClose={closeCreate} />}
    </div>
  )
}

export default App
