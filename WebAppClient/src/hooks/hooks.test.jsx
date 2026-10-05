import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import usePokemonPage from './usePokemonPage'
import usePokemonSearch from './usePokemonSearch'

vi.mock('../api/pokemonApi', () => ({
  SOURCES: { REMOTE: 'remote', LOCAL: 'local' },
  fetchPokemonPage: vi.fn(),
  fetchPokemonByName: vi.fn(),
}))

import { fetchPokemonByName, fetchPokemonPage } from '../api/pokemonApi'

const pageOf = (name) => ({ items: [{ id: 1, name }], page: 0, size: 20, totalElements: 1, totalPages: 1 })

beforeEach(() => {
  vi.clearAllMocks()
})

describe('usePokemonPage', () => {
  it('loads the first page of PokeAPI', async () => {
    fetchPokemonPage.mockResolvedValue(pageOf('bulbasaur'))
    const { result } = renderHook(() => usePokemonPage())

    expect(result.current.loading).toBe(true)
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.pageData.items[0].name).toBe('bulbasaur')
    expect(fetchPokemonPage).toHaveBeenCalledWith(0, 20, 'remote')
  })

  it('discards a late answer for a source that is no longer selected', async () => {
    let answerRemote
    fetchPokemonPage.mockImplementation((page, size, source) =>
      source === 'remote'
        ? new Promise((resolve) => { answerRemote = () => resolve(pageOf('from-pokeapi')) })
        : Promise.resolve(pageOf('from-local')))
    const { result } = renderHook(() => usePokemonPage())

    act(() => result.current.changeSource('local'))
    await waitFor(() => expect(result.current.pageData.items[0]?.name).toBe('from-local'))
    await act(async () => answerRemote())

    expect(result.current.source).toBe('local')
    expect(result.current.pageData.items[0].name).toBe('from-local')
    expect(result.current.loading).toBe(false)
  })

  it('goes back to the first page when the source or the size changes, and reloads on demand', async () => {
    fetchPokemonPage.mockResolvedValue(pageOf('bulbasaur'))
    const { result } = renderHook(() => usePokemonPage())
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.changePage(3))
    act(() => result.current.changeSize(50))
    expect(result.current.page).toBe(0)
    act(() => result.current.changePage(2))
    act(() => result.current.changeSource('local'))
    expect(result.current.page).toBe(0)
    await waitFor(() => expect(result.current.loading).toBe(false))

    const calls = fetchPokemonPage.mock.calls.length
    act(() => result.current.reload())
    await waitFor(() => expect(fetchPokemonPage.mock.calls.length).toBe(calls + 1))
    expect(fetchPokemonPage).toHaveBeenLastCalledWith(0, 50, 'local')
  })

  it('reports the error of the current request', async () => {
    fetchPokemonPage.mockRejectedValue(new Error('Could not load the Pokémon list (HTTP 502)'))
    const { result } = renderHook(() => usePokemonPage())

    await waitFor(() => expect(result.current.error).toBe('Could not load the Pokémon list (HTTP 502)'))
    expect(result.current.pageData.items).toEqual([])
  })
})

describe('usePokemonSearch', () => {
  const items = [{ name: 'bulbasaur' }, { name: 'ivysaur' }, { name: 'charmander' }]

  it('filters the loaded page while typing, without requests', () => {
    const { result } = renderHook(() => usePokemonSearch('remote', items))

    act(() => result.current.changeQuery('SAUR'))

    expect(result.current.liveFilterItems.map((item) => item.name)).toEqual(['bulbasaur', 'ivysaur'])
    expect(fetchPokemonByName).not.toHaveBeenCalled()
  })

  it('shows only the answer of the latest search', async () => {
    let answerFirst
    fetchPokemonByName.mockImplementation((name) =>
      name === 'pikachu'
        ? new Promise((resolve) => { answerFirst = () => resolve({ id: 25, name: 'pikachu' }) })
        : Promise.resolve({ id: 133, name: 'eevee' }))
    const { result } = renderHook(() => usePokemonSearch('remote', items))

    act(() => { result.current.submit('pikachu') })
    await act(async () => { await result.current.submit('eevee') })
    await act(async () => answerFirst())

    expect(result.current.search).toMatchObject({ term: 'eevee', loading: false, result: { name: 'eevee' } })
    expect(result.current.liveFilterItems).toBeNull()
  })

  it('goes back to the list when the box is emptied or reset', async () => {
    fetchPokemonByName.mockResolvedValue({ id: 25, name: 'pikachu' })
    const { result } = renderHook(() => usePokemonSearch('remote', items))

    await act(async () => { await result.current.submit('pikachu') })
    act(() => result.current.changeQuery(''))
    expect(result.current.search).toBeNull()

    await act(async () => { await result.current.submit('pikachu') })
    act(() => result.current.reset())
    expect(result.current.search).toBeNull()
    expect(result.current.query).toBe('')
  })

  it('shows a record as it is now after it changed, keeping the term', async () => {
    fetchPokemonByName.mockResolvedValueOnce({ id: 25, name: 'pikachu' }).mockResolvedValueOnce(null)
    const { result } = renderHook(() => usePokemonSearch('local', items))

    await act(async () => { await result.current.submit('pikachu') })
    await act(async () => { await result.current.refresh(25) })

    expect(fetchPokemonByName).toHaveBeenLastCalledWith('25', 'local')
    expect(result.current.search).toMatchObject({ term: 'pikachu', result: null })
  })

  it('drops a deleted record from the result without asking for it again', async () => {
    fetchPokemonByName.mockResolvedValue({ id: 25, name: 'pikachu' })
    const { result } = renderHook(() => usePokemonSearch('local', items))

    await act(async () => { await result.current.submit('pikachu') })
    act(() => result.current.forget(133))
    expect(result.current.search.result).toMatchObject({ id: 25 })
    act(() => result.current.forget(25))

    expect(result.current.search).toMatchObject({ term: 'pikachu', loading: false, result: null })
    expect(fetchPokemonByName).toHaveBeenCalledTimes(1)
  })
})
