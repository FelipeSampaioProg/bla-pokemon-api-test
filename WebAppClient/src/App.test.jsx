import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

vi.mock('./api/pokemonApi', () => ({
  SOURCES: { REMOTE: 'remote', LOCAL: 'local' },
  rateLimitMessage: (seconds) => `PokeAPI is limiting requests; try again in ${seconds} seconds.`,
  fetchPokemonPage: vi.fn(),
  fetchPokemonByName: vi.fn(),
  fetchPokemonDetail: vi.fn(),
  updateLocalData: vi.fn(),
  updateLocalPokemon: vi.fn(),
  fetchLocalVersion: vi.fn(),
  restoreFromPokeApi: vi.fn(),
  createLocalPokemon: vi.fn(),
  deleteLocalPokemon: vi.fn(),
  isConflict: (error) => error?.status === 412,
}))

import {
  createLocalPokemon,
  deleteLocalPokemon,
  restoreFromPokeApi,
  fetchLocalVersion,
  fetchPokemonByName,
  fetchPokemonDetail,
  fetchPokemonPage,
  updateLocalData,
  updateLocalPokemon,
} from './api/pokemonApi'

const pagePayload = {
  items: [
    { id: 1, name: 'Bulbasaur', image: null, subtitle: 'Seed', mass: 6.9, tags: ['grass'] },
    { id: 4, name: 'Charmander', image: null, subtitle: 'Lizard', mass: 8.5, tags: ['fire'] },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
}

const bulbasaurDetail = {
  id: 1,
  name: 'bulbasaur',
  image: 'bulbasaur.png',
  height: 0.7,
  mass: 6.9,
  description: 'A strange seed was planted on its back at birth.',
  stats: [{ name: 'hp', value: 45 }],
  lineage: { name: 'bulbasaur', evolvesTo: [{ name: 'ivysaur', evolvesTo: [] }] },
  evolutionAvailable: true,
}

beforeEach(() => {
  vi.clearAllMocks()
  fetchPokemonPage.mockResolvedValue(pagePayload)
  fetchLocalVersion.mockResolvedValue(null)
  fetchPokemonByName.mockResolvedValue({
    id: 25,
    name: 'Pikachu',
    image: null,
    subtitle: 'Mouse',
    mass: 6,
    tags: ['electric'],
  })
})

describe('App', () => {
  it('loads and renders paginated pokemon list', async () => {
    render(<App />)

    expect(await screen.findByText('Bulbasaur')).toBeInTheDocument()
    expect(screen.getByText('Charmander')).toBeInTheDocument()
    expect(screen.getByText('Page 1 of 1 · 2 Pokémon')).toBeInTheDocument()
    expect(fetchPokemonPage).toHaveBeenCalledWith(0, 20, 'remote')
  })

  it('runs exact search on submit and renders only result card', async () => {
    render(<App />)

    await screen.findByText('Bulbasaur')
    fireEvent.change(screen.getByLabelText('Search Pokémon by name or id'), {
      target: { value: 'pikachu' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Search' }))

    await screen.findByText('Pikachu')
    expect(fetchPokemonByName).toHaveBeenCalledWith('pikachu', 'remote')
    expect(screen.queryByText('Bulbasaur')).not.toBeInTheDocument()
    expect(screen.queryByText('Page 1 of 1 · 2 Pokémon')).not.toBeInTheDocument()
  })

  it('shows live filter no-match message when query has no local matches', async () => {
    render(<App />)

    await screen.findByText('Bulbasaur')
    fireEvent.change(screen.getByLabelText('Search Pokémon by name or id'), {
      target: { value: 'xyz' },
    })

    await waitFor(() => {
      expect(screen.getByText('No matches for "xyz".')).toBeInTheDocument()
    })
  })

  it('switches to the local source and reloads from the first page', async () => {
    fetchPokemonPage.mockImplementation(async (page, size, source) =>
      source === 'local'
        ? {
            items: [{ id: 9001, name: 'Localmon', image: null, subtitle: 'Custom', mass: 1, tags: [] }],
            page: 0,
            size: 20,
            totalElements: 1,
            totalPages: 1,
          }
        : pagePayload,
    )
    render(<App />)

    await screen.findByText('Bulbasaur')
    fireEvent.click(screen.getByRole('button', { name: 'Local database' }))

    expect(await screen.findByText('Localmon')).toBeInTheDocument()
    expect(screen.queryByText('Bulbasaur')).not.toBeInTheDocument()
    expect(fetchPokemonPage).toHaveBeenLastCalledWith(0, 20, 'local')
    expect(screen.getByRole('button', { name: 'Local database' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('shows a message when the local database is empty', async () => {
    fetchPokemonPage.mockImplementation(async (page, size, source) =>
      source === 'local'
        ? { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
        : pagePayload,
    )
    render(<App />)

    await screen.findByText('Bulbasaur')
    fireEvent.click(screen.getByRole('button', { name: 'Local database' }))

    expect(await screen.findByText('There are no Pokémon in the local database yet.')).toBeInTheDocument()
  })

  it('warns when some pokemon could not be loaded from PokeAPI', async () => {
    fetchPokemonPage.mockResolvedValue({
      ...pagePayload,
      items: [
        pagePayload.items[0],
        { id: 4, name: 'Charmander', image: null, subtitle: null, mass: null, tags: [], available: false },
      ],
    })
    render(<App />)

    await screen.findByText('Bulbasaur')
    expect(screen.getByText(/1 Pokémon could not be loaded from PokeAPI\./)).toBeInTheDocument()
    expect(screen.getByText('Data not available right now. Try again later.')).toBeInTheDocument()
  })

  it('runs exact search against the local source when it is selected', async () => {
    fetchPokemonByName.mockResolvedValue(null)
    render(<App />)

    await screen.findByText('Bulbasaur')
    fireEvent.click(screen.getByRole('button', { name: 'Local database' }))
    await screen.findByText('Bulbasaur')
    fireEvent.change(screen.getByLabelText('Search Pokémon by name or id'), {
      target: { value: 'pikachu' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Search' }))

    expect(await screen.findByText(/No Pokémon named "pikachu"/)).toHaveTextContent(
      'No Pokémon named "pikachu" was found in the local database.',
    )
    expect(fetchPokemonByName).toHaveBeenCalledWith('pikachu', 'local')
  })

  it('opens the detail of the chosen pokemon and navigates through its lineage', async () => {
    fetchPokemonDetail.mockImplementation(async (name) =>
      name === 'ivysaur'
        ? { ...bulbasaurDetail, id: 2, name: 'ivysaur', lineage: bulbasaurDetail.lineage }
        : bulbasaurDetail,
    )
    render(<App />)

    await screen.findByText('Bulbasaur')
    fireEvent.click(screen.getByRole('button', { name: 'View details of Bulbasaur' }))

    expect(await screen.findByRole('dialog', { name: 'bulbasaur' })).toBeInTheDocument()
    expect(screen.getByText('A strange seed was planted on its back at birth.')).toBeInTheDocument()
    expect(fetchPokemonDetail).toHaveBeenCalledWith('Bulbasaur', 'remote')

    fireEvent.click(screen.getByRole('button', { name: 'ivysaur' }))

    expect(await screen.findByRole('dialog', { name: 'ivysaur' })).toBeInTheDocument()
    expect(fetchPokemonDetail).toHaveBeenLastCalledWith('ivysaur', 'remote')
  })

  it('closes the detail with Escape', async () => {
    fetchPokemonDetail.mockResolvedValue(bulbasaurDetail)
    render(<App />)

    await screen.findByText('Bulbasaur')
    fireEvent.click(screen.getByRole('button', { name: 'View details of Bulbasaur' }))
    await screen.findByRole('dialog', { name: 'bulbasaur' })
    fireEvent.keyDown(document, { key: 'Escape' })

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('opens local details without lineage navigation', async () => {
    fetchPokemonPage.mockImplementation(async (page, size, source) =>
      source === 'local'
        ? {
            items: [{ id: 9001, name: 'Localmon', image: null, subtitle: 'Custom', mass: 1, tags: [] }],
            page: 0,
            size: 20,
            totalElements: 1,
            totalPages: 1,
          }
        : pagePayload,
    )
    fetchPokemonDetail.mockResolvedValue({
      ...bulbasaurDetail,
      id: 9001,
      name: 'localmon',
      lineage: { name: 'localmon', evolvesTo: [] },
    })
    render(<App />)

    await screen.findByText('Bulbasaur')
    fireEvent.click(screen.getByRole('button', { name: 'Local database' }))
    await screen.findByText('Localmon')
    fireEvent.click(screen.getByRole('button', { name: 'View details of Localmon' }))

    expect(await screen.findByRole('dialog', { name: 'localmon' })).toBeInTheDocument()
    expect(fetchPokemonDetail).toHaveBeenCalledWith('Localmon', 'local')
  })

  it('tells when to reload a page whose entries were rate limited', async () => {
    fetchPokemonPage.mockResolvedValue({
      ...pagePayload,
      items: [
        pagePayload.items[0],
        { id: 4, name: 'Charmander', image: null, subtitle: null, mass: null, tags: [], available: false },
      ],
      retryAfterSeconds: 12,
    })
    render(<App />)

    await screen.findByText('Bulbasaur')
    expect(
      screen.getByText(/PokeAPI is limiting requests; try again in 12 seconds\./),
    ).toBeInTheDocument()
  })

  it('adds local data to the chosen pokemon from its detail panel', async () => {
    fetchPokemonDetail.mockResolvedValue(bulbasaurDetail)
    updateLocalData.mockResolvedValue({
      metadata: { localizedName: null, region: 'Kanto', classificationTag: null },
      etag: '"1-100"',
    })
    render(<App />)

    await screen.findByText('Bulbasaur')
    fireEvent.click(screen.getByRole('button', { name: 'View details of Bulbasaur' }))
    await screen.findByRole('dialog', { name: 'bulbasaur' })
    await waitFor(() => expect(fetchLocalVersion).toHaveBeenCalledWith(1))
    fireEvent.click(screen.getByRole('button', { name: 'Add local data' }))
    fireEvent.change(screen.getByLabelText('Region'), { target: { value: 'Kanto' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Kanto')).toBeInTheDocument()
    // Not in the local store yet: no version to send, the server replicates it.
    expect(updateLocalData).toHaveBeenCalledWith(1, { localizedName: '', region: 'Kanto', classificationTag: '' }, null)
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument()
  })

  it('protects the local data of a PokeAPI Pokemon with the version of its local record', async () => {
    fetchPokemonDetail.mockResolvedValue({ ...bulbasaurDetail, metadata: { region: 'Kanto' } })
    fetchLocalVersion.mockResolvedValue({ etag: '"3-100"', metadata: { region: 'Johto' } })
    updateLocalData.mockResolvedValue({ metadata: { region: 'Hoenn' }, etag: '"4-100"' })
    render(<App />)

    await screen.findByText('Bulbasaur')
    fireEvent.click(screen.getByRole('button', { name: 'View details of Bulbasaur' }))
    await screen.findByRole('dialog', { name: 'bulbasaur' })

    // The local fields shown are the ones of the version that will be sent as If-Match.
    expect(await screen.findByText('Johto')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))
    fireEvent.change(screen.getByLabelText('Region'), { target: { value: 'Hoenn' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Hoenn')).toBeInTheDocument()
    expect(updateLocalData).toHaveBeenCalledWith(1, { localizedName: '', region: 'Hoenn', classificationTag: '' }, '"3-100"')
  })

  it('reloads the local data when someone changed it meanwhile', async () => {
    fetchPokemonDetail.mockResolvedValue(bulbasaurDetail)
    fetchLocalVersion
      .mockResolvedValueOnce({ etag: '"3-100"', metadata: { region: 'Johto' } })
      .mockResolvedValueOnce({ etag: '"5-100"', metadata: { region: 'Sinnoh' } })
    updateLocalData.mockRejectedValue(Object.assign(new Error('Could not save the local data (HTTP 412)'), { status: 412 }))
    render(<App />)

    await screen.findByText('Bulbasaur')
    fireEvent.click(screen.getByRole('button', { name: 'View details of Bulbasaur' }))
    expect(await screen.findByText('Johto')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))
    fireEvent.change(screen.getByLabelText('Region'), { target: { value: 'Hoenn' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Load latest version' }))

    expect(await screen.findByText('Sinnoh')).toBeInTheDocument()
    expect(fetchLocalVersion).toHaveBeenCalledTimes(2)
  })

  it('tells how many cards are missing their category', async () => {
    fetchPokemonPage.mockResolvedValue({
      ...pagePayload,
      items: [
        { ...pagePayload.items[0], subtitle: null, categoryAvailable: false },
        { ...pagePayload.items[1], subtitle: null, categoryAvailable: false },
      ],
      retryAfterSeconds: 12,
    })
    render(<App />)

    await screen.findByText('Bulbasaur')
    expect(screen.getByRole('status')).toHaveTextContent(
      'The category of 2 Pokémon could not be loaded. PokeAPI is limiting requests; try again in 12 seconds.',
    )
    expect(screen.getAllByText('Category not available right now')).toHaveLength(2)
  })

  it('restores a deleted Pokemon from PokeAPI and saves its local data', async () => {
    fetchPokemonDetail.mockResolvedValue(bulbasaurDetail)
    updateLocalData
      .mockRejectedValueOnce(Object.assign(new Error('Could not save the local data (HTTP 404)'), { status: 404, deleted: true }))
      .mockResolvedValueOnce({ metadata: { region: 'Kanto' }, etag: '"1-100"' })
    restoreFromPokeApi.mockResolvedValue({ etag: '"0-100"' })
    render(<App />)

    await screen.findByText('Bulbasaur')
    fireEvent.click(screen.getByRole('button', { name: 'View details of Bulbasaur' }))
    await screen.findByRole('dialog', { name: 'bulbasaur' })
    await waitFor(() => expect(fetchLocalVersion).toHaveBeenCalledWith(1))
    fireEvent.click(screen.getByRole('button', { name: 'Add local data' }))
    fireEvent.change(screen.getByLabelText('Region'), { target: { value: 'Kanto' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Restore from PokeAPI and save' }))

    expect(await screen.findByText('Kanto')).toBeInTheDocument()
    expect(restoreFromPokeApi).toHaveBeenCalledWith(1)
    // The save after the restore is protected by the restored record's version.
    expect(updateLocalData).toHaveBeenLastCalledWith(1, { localizedName: '', region: 'Kanto', classificationTag: '' }, '"0-100"')
  })

  it('offers the full edit only for local records', async () => {
    fetchPokemonDetail.mockResolvedValue(bulbasaurDetail)
    render(<App />)

    await screen.findByText('Bulbasaur')
    fireEvent.click(screen.getByRole('button', { name: 'View details of Bulbasaur' }))
    await screen.findByRole('dialog', { name: 'bulbasaur' })

    expect(screen.queryByRole('button', { name: 'Edit Pokémon' })).not.toBeInTheDocument()
  })

  it('edits a local record with its ETag and shows the saved data', async () => {
    const localDetail = { ...bulbasaurDetail, skillLinks: [], localEtag: '"2-100"' }
    fetchPokemonDetail
      .mockResolvedValueOnce(localDetail)
      .mockResolvedValueOnce({ ...localDetail, mass: 7.5, localEtag: '"4-100"' })
    updateLocalPokemon.mockResolvedValue({ etag: '"4-100"' })
    render(<App />)

    await screen.findByText('Bulbasaur')
    fireEvent.click(screen.getByRole('button', { name: 'Local database' }))
    await waitFor(() => expect(fetchPokemonPage).toHaveBeenLastCalledWith(0, 20, 'local'))
    fireEvent.click(await screen.findByRole('button', { name: 'View details of Bulbasaur' }))
    await screen.findByRole('dialog', { name: 'bulbasaur' })
    fireEvent.click(screen.getByRole('button', { name: 'Edit Pokémon' }))
    fireEvent.change(screen.getByLabelText('Weight (kg)'), { target: { value: '7.5' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Weight: 7.5 kg')).toBeInTheDocument()
    expect(updateLocalPokemon).toHaveBeenCalledWith(1, { mass: 7.5 }, '"2-100"')
    expect(fetchPokemonDetail).toHaveBeenLastCalledWith('1', 'local')
  })

  // ---- complete CRUD in the interface, and the stale-answer fix ----

  const localPage = {
    items: [{ id: 9001, name: 'Localmon', image: null, subtitle: 'Custom', mass: 1, tags: [] }],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  }
  const localDetail = {
    ...bulbasaurDetail,
    id: 9001,
    name: 'localmon',
    skillLinks: [],
    lineage: { name: 'localmon', evolvesTo: [] },
    localEtag: '"2-100"',
  }

  async function openLocalSource() {
    render(<App />)
    await screen.findByText('Bulbasaur')
    fireEvent.click(screen.getByRole('button', { name: 'Local database' }))
    await screen.findByText('Localmon')
  }

  it('keeps the local list when a PokeAPI page answers late', async () => {
    let answerRemote
    fetchPokemonPage.mockImplementation((page, size, source) =>
      source === 'remote'
        ? new Promise((resolve) => { answerRemote = () => resolve(pagePayload) })
        : Promise.resolve(localPage))
    render(<App />)

    fireEvent.click(screen.getByRole('button', { name: 'Local database' }))
    await screen.findByText('Localmon')
    await act(async () => answerRemote())

    expect(screen.getByText('Localmon')).toBeInTheDocument()
    expect(screen.queryByText('Bulbasaur')).not.toBeInTheDocument()
  })

  it('offers to create a Pokemon only in the local source', async () => {
    fetchPokemonPage.mockImplementation(async (page, size, source) => (source === 'local' ? localPage : pagePayload))
    render(<App />)

    await screen.findByText('Bulbasaur')
    expect(screen.queryByRole('button', { name: '+ New Pokémon' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Local database' }))
    expect(await screen.findByRole('button', { name: '+ New Pokémon' })).toBeInTheDocument()
  })

  it('creates a local Pokemon, lists it and opens it', async () => {
    fetchPokemonPage.mockImplementation(async (page, size, source) => (source === 'local' ? localPage : pagePayload))
    createLocalPokemon.mockResolvedValue({ id: 9002, etag: '"0-100"' })
    fetchPokemonDetail.mockResolvedValue({ ...localDetail, id: 9002, name: 'newmon' })
    await openLocalSource()
    const listLoads = fetchPokemonPage.mock.calls.length

    fireEvent.click(screen.getByRole('button', { name: '+ New Pokémon' }))
    fireEvent.change(screen.getByLabelText('Id'), { target: { value: '9002' } })
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'newmon' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create' }))

    expect(await screen.findByRole('dialog', { name: 'newmon' })).toBeInTheDocument()
    expect(createLocalPokemon).toHaveBeenCalledWith({ id: 9002, name: 'newmon' })
    expect(fetchPokemonDetail).toHaveBeenCalledWith('9002', 'local')
    expect(fetchPokemonPage.mock.calls.length).toBeGreaterThan(listLoads)
    expect(screen.queryByRole('dialog', { name: 'New Pokémon' })).not.toBeInTheDocument()
  })

  it('deletes a local Pokemon with its version and takes it off the list', async () => {
    fetchPokemonPage.mockImplementation(async (page, size, source) => (source === 'local' ? localPage : pagePayload))
    fetchPokemonDetail.mockResolvedValue(localDetail)
    deleteLocalPokemon.mockResolvedValue(undefined)
    await openLocalSource()
    fireEvent.click(screen.getByRole('button', { name: 'View details of Localmon' }))
    await screen.findByRole('dialog', { name: 'localmon' })

    fetchPokemonPage.mockImplementation(async (page, size, source) =>
      source === 'local' ? { ...localPage, items: [], totalElements: 0, totalPages: 0 } : pagePayload)
    fireEvent.click(screen.getByRole('button', { name: 'Delete Pokémon' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes, delete' }))

    expect(await screen.findByText('There are no Pokémon in the local database yet.')).toBeInTheDocument()
    expect(deleteLocalPokemon).toHaveBeenCalledWith(9001, '"2-100"')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('offers no deletion for PokeAPI Pokemon', async () => {
    fetchPokemonDetail.mockResolvedValue(bulbasaurDetail)
    render(<App />)

    await screen.findByText('Bulbasaur')
    fireEvent.click(screen.getByRole('button', { name: 'View details of Bulbasaur' }))
    await screen.findByRole('dialog', { name: 'bulbasaur' })

    expect(screen.queryByRole('button', { name: 'Delete Pokémon' })).not.toBeInTheDocument()
  })

  it('shows the list as it is now after a record found by the search was deleted', async () => {
    fetchPokemonPage.mockImplementation(async (page, size, source) => (source === 'local' ? localPage : pagePayload))
    fetchPokemonByName.mockResolvedValueOnce({ id: 9001, name: 'Localmon', image: null, subtitle: null, mass: 1, tags: [] })
    fetchPokemonDetail.mockResolvedValue(localDetail)
    deleteLocalPokemon.mockResolvedValue(undefined)
    await openLocalSource()

    fireEvent.change(screen.getByLabelText('Search Pokémon by name or id'), { target: { value: 'localmon' } })
    fireEvent.click(screen.getByRole('button', { name: 'Search' }))
    fireEvent.click(await screen.findByRole('button', { name: 'View details of Localmon' }))
    await screen.findByRole('dialog', { name: 'localmon' })
    fetchPokemonPage.mockImplementation(async (page, size, source) =>
      source === 'local' ? { ...localPage, items: [], totalElements: 0, totalPages: 0 } : pagePayload)
    fireEvent.click(screen.getByRole('button', { name: 'Delete Pokémon' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes, delete' }))

    expect(await screen.findByText('No Pokémon named "localmon" was found in the local database.')).toBeInTheDocument()
    // The deleted record is not asked for again (that request would only log a 404 in the browser console).
    expect(fetchPokemonByName).toHaveBeenCalledTimes(1)
    // Back to the list: it no longer has the deleted Pokemon.
    fireEvent.change(screen.getByLabelText('Search Pokémon by name or id'), { target: { value: '' } })
    expect(await screen.findByText('There are no Pokémon in the local database yet.')).toBeInTheDocument()
    expect(screen.queryByText('Localmon')).not.toBeInTheDocument()
  })
})

