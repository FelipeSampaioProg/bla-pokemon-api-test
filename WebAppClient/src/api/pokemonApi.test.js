import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  SOURCES,
  createLocalPokemon,
  deleteLocalPokemon,
  fetchLocalVersion,
  fetchPokemonByName,
  fetchPokemonDetail,
  isConflict,
  restoreFromPokeApi,
  fetchPokemonPage,
  rateLimitMessage,
  updateLocalData,
  updateLocalPokemon,
} from './pokemonApi'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('pokemonApi', () => {
  it('maps paginated pokemon list response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          content: [
            {
              id: 25,
              name: 'Pikachu',
              image: 'pikachu.png',
              category: 'Mouse',
              mass: 6,
              skills: [{ name: 'static' }, { name: 'lightning-rod' }],
            },
          ],
          page: 1,
          size: 20,
          totalElements: 151,
          totalPages: 8,
        }),
      }),
    )

    const result = await fetchPokemonPage(1, 20)

    expect(fetch).toHaveBeenCalledWith('/api/v1/pokemons?page=1&size=20')
    expect(result).toEqual({
      items: [
        {
          id: 25,
          name: 'Pikachu',
          image: 'pikachu.png',
          subtitle: 'Mouse',
          mass: 6,
          tags: ['static', 'lightning-rod'],
          available: true,
          categoryAvailable: true,
        },
      ],
      page: 1,
      size: 20,
      totalElements: 151,
      totalPages: 8,
      retryAfterSeconds: null,
    })
  })

  it('throws an error when pokemon page cannot be fetched', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        json: async () => {
          throw new SyntaxError('Unexpected end of JSON input')
        },
      }),
    )

    await expect(fetchPokemonPage(0, 20)).rejects.toThrow(
      'Could not load the Pokémon list (HTTP 500)',
    )
  })

  it('returns null for a not found exact search', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
      }),
    )

    const result = await fetchPokemonByName('missingno')

    expect(result).toBeNull()
  })

  it('maps the exact search result to the same card as the list', async () => {
    const listItem = {
      id: 1,
      name: 'Bulbasaur',
      image: 'bulbasaur.png',
      category: 'Seed Pokemon',
      mass: 6.9,
      skills: [{ name: 'overgrow' }, { name: 'chlorophyll' }],
    }
    vi.stubGlobal(
      'fetch',
      vi.fn()
        .mockResolvedValueOnce({
          ok: true,
          status: 200,
          json: async () => ({
            ...listItem,
            description: 'A strange seed was planted on its back at birth.',
            stats: [
              { name: 'hp', value: 45 },
              { name: 'attack', value: 49 },
            ],
          }),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: async () => ({ content: [listItem], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
        }),
    )

    const searchCard = await fetchPokemonByName('bulbasaur')
    const listCard = (await fetchPokemonPage(0, 20)).items[0]

    expect(fetch).toHaveBeenCalledWith('/api/v1/pokemons/bulbasaur')
    expect(searchCard).toEqual({
      id: 1,
      name: 'Bulbasaur',
      image: 'bulbasaur.png',
      subtitle: 'Seed Pokemon',
      mass: 6.9,
      tags: ['overgrow', 'chlorophyll'],
      available: true,
      categoryAvailable: true,
    })
    expect(searchCard).toEqual(listCard)
  })

  it('requests the local source and maps unavailable entries', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          content: [
            { id: 4, name: 'charmander', image: null, category: null, mass: null, skills: [], available: false },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        }),
      }),
    )

    const result = await fetchPokemonPage(0, 20, SOURCES.LOCAL)

    expect(fetch).toHaveBeenCalledWith('/api/v1/local-pokemons?page=0&size=20')
    expect(result.items[0]).toEqual({
      id: 4,
      name: 'charmander',
      image: null,
      subtitle: null,
      mass: null,
      tags: [],
      available: false,
      categoryAvailable: false,
    })
  })

  it('uses the problem detail message from the API in errors', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 502,
        json: async () => ({
          type: 'about:blank',
          title: 'Upstream failure',
          status: 502,
          detail: 'PokeAPI request failed, please retry later',
        }),
      }),
    )

    await expect(fetchPokemonPage(0, 20)).rejects.toThrow(
      'Could not load the Pokémon list: PokeAPI request failed, please retry later (HTTP 502)',
    )
  })

  it('uses the problem detail message when exact search fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 504,
        json: async () => ({ status: 504, detail: 'PokeAPI did not respond in time, please retry later' }),
      }),
    )

    await expect(fetchPokemonByName('pikachu')).rejects.toThrow(
      'Could not load the Pokémon: PokeAPI did not respond in time, please retry later (HTTP 504)',
    )
  })

  it('requests the local source for exact search', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ id: 9001, name: 'localmon', image: null, description: null, mass: 12.5, stats: [] }),
      }),
    )

    const result = await fetchPokemonByName('localmon', SOURCES.LOCAL)

    expect(fetch).toHaveBeenCalledWith('/api/v1/local-pokemons/localmon')
    expect(result.name).toBe('localmon')
  })

  it('maps the full detail without truncating description or stats', async () => {
    const description = 'a'.repeat(200)
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({
          id: 133,
          name: 'eevee',
          image: 'eevee.png',
          category: 'Evolution Pokemon',
          skills: [
            { name: 'run-away', url: 'u1' },
            { name: 'adaptability', url: 'u2' },
          ],
          height: 0.3,
          mass: 6.5,
          description,
          stats: [
            { name: 'hp', value: 55 },
            { name: 'attack', value: 55 },
            { name: 'defense', value: 50 },
            { name: 'special-attack', value: 45 },
            { name: 'special-defense', value: 65 },
            { name: 'speed', value: 55 },
          ],
          evolutionLineage: { name: 'eevee', evolvesTo: [{ name: 'vaporeon', evolvesTo: [] }] },
          evolutionAvailable: false,
        }),
      }),
    )

    const detail = await fetchPokemonDetail('eevee')

    expect(fetch).toHaveBeenCalledWith('/api/v1/pokemons/eevee')
    expect(detail.description).toBe(description)
    expect(detail.stats).toHaveLength(6)
    expect(detail.height).toBe(0.3)
    expect(detail.lineage).toEqual({ name: 'eevee', evolvesTo: [{ name: 'vaporeon', evolvesTo: [] }] })
    expect(detail.evolutionAvailable).toBe(false)
    expect(detail.descriptionAvailable).toBe(true)
    expect(detail.species).toBe('eevee')
    expect(detail.category).toBe('Evolution Pokemon')
    expect(detail.skills).toEqual(['run-away', 'adaptability'])
  })

  it('maps the species and an unavailable description', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({
          id: 745,
          name: 'lycanroc-midday',
          species: 'lycanroc',
          image: null,
          description: null,
          descriptionAvailable: false,
          stats: [],
          evolutionLineage: { name: 'lycanroc', evolvesTo: [] },
          evolutionAvailable: false,
        }),
      }),
    )

    const detail = await fetchPokemonDetail('lycanroc')

    expect(detail.name).toBe('lycanroc-midday')
    expect(detail.species).toBe('lycanroc')
    expect(detail.descriptionAvailable).toBe(false)
  })

  it('returns null when the detail is not found', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 404 }))

    expect(await fetchPokemonDetail('missingno', SOURCES.LOCAL)).toBeNull()
    expect(fetch).toHaveBeenCalledWith('/api/v1/local-pokemons/missingno')
  })

  it('shows the rate-limit wait from Retry-After on a 503', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 503,
        headers: new Headers({ 'Retry-After': '20' }),
        json: async () => ({ status: 503, detail: 'PokeAPI is limiting requests, please retry later' }),
      }),
    )

    await expect(fetchPokemonByName('pikachu')).rejects.toThrow(
      'Could not load the Pokémon: PokeAPI is limiting requests; try again in 20 seconds. (HTTP 503)',
    )
  })

  it('keeps the API detail on a 503 without Retry-After', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 503,
        headers: new Headers(),
        json: async () => ({ status: 503, detail: 'Service unavailable' }),
      }),
    )

    await expect(fetchPokemonPage(0, 20)).rejects.toThrow('Could not load the Pokémon list: Service unavailable (HTTP 503)')
  })

  it('maps the rate-limit wait of a degraded detail', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({
          id: 25,
          name: 'pikachu',
          image: 'pikachu.png',
          descriptionAvailable: false,
          evolutionAvailable: false,
          evolutionLineage: { name: 'pikachu', evolvesTo: [] },
          retryAfterSeconds: 20,
        }),
      }),
    )

    expect((await fetchPokemonDetail('pikachu')).retryAfterSeconds).toBe(20)
  })

  it('words the rate-limit message the same everywhere', () => {
    expect(rateLimitMessage(1)).toBe('PokeAPI is limiting requests; try again in 1 second.')
    expect(rateLimitMessage(12)).toBe('PokeAPI is limiting requests; try again in 12 seconds.')
  })

  it('maps the proprietary fields of a detail', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({
          id: 25,
          name: 'pikachu',
          evolutionLineage: { name: 'pikachu', evolvesTo: [] },
          metadata: { localizedName: 'Pikachu (ES)', region: 'Kanto', classificationTag: 'STARTER' },
        }),
      }),
    )

    expect((await fetchPokemonDetail('pikachu')).metadata).toEqual({
      localizedName: 'Pikachu (ES)',
      region: 'Kanto',
      classificationTag: 'STARTER',
    })
  })

  it('saves only the proprietary fields with PATCH on the local resource', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ id: 25, name: 'pikachu', metadata: { localizedName: null, region: 'Kanto', classificationTag: 'STARTER' } }),
      }),
    )

    const saved = await updateLocalData(25, { localizedName: '', region: 'Kanto', classificationTag: 'STARTER' })

    expect(fetch).toHaveBeenCalledWith('/api/v1/local-pokemons/25', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ localizedName: '', region: 'Kanto', classificationTag: 'STARTER' }),
    })
    expect(saved.metadata).toEqual({ localizedName: null, region: 'Kanto', classificationTag: 'STARTER' })
  })

  it('returns null when every proprietary field was removed', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ id: 25, name: 'pikachu' }),
      }),
    )

    expect((await updateLocalData(25, {})).metadata).toBeNull()
  })

  it('explains why the local data could not be saved', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        json: async () => ({ status: 404, detail: 'The requested Pokemon does not exist in PokeAPI' }),
      }),
    )

    await expect(updateLocalData(99999, { region: 'Kanto' })).rejects.toThrow(
      'Could not save the local data: The requested Pokemon does not exist in PokeAPI (HTTP 404)',
    )
  })

  it('keeps the ETag of a local detail, never of a PokeAPI one, and the links of its abilities', async () => {
    const body = {
      id: 9001,
      name: 'localmon',
      skills: [{ name: 'static', url: 'https://pokeapi.co/api/v2/ability/9/' }, { name: 'splash' }],
      evolutionLineage: { name: 'localmon', evolvesTo: [] },
    }
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, status: 200, headers: new Headers({ ETag: '"3-100"' }), json: async () => body }),
    )

    const local = await fetchPokemonDetail('localmon', SOURCES.LOCAL)
    const remote = await fetchPokemonDetail('localmon', SOURCES.REMOTE)

    expect(local.localEtag).toBe('"3-100"')
    expect(remote.localEtag).toBeNull()
    expect(local.skillLinks).toEqual([
      { name: 'static', url: 'https://pokeapi.co/api/v2/ability/9/' },
      { name: 'splash', url: null },
    ])
  })

  it('sends the ETag as If-Match and returns the new one', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({ ETag: '"4-100"' }),
        json: async () => ({ id: 25, metadata: { region: 'Kanto' } }),
      }),
    )

    const saved = await updateLocalData(25, { region: 'Kanto' }, '"3-100"')

    expect(fetch.mock.calls[0][1].headers).toEqual({ 'Content-Type': 'application/json', 'If-Match': '"3-100"' })
    expect(saved.etag).toBe('"4-100"')
  })

  it('edits a local record sending only the changes, with If-Match', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, status: 200, headers: new Headers({ ETag: '"5-100"' }), json: async () => ({}) }),
    )

    const saved = await updateLocalPokemon(9001, { name: 'localmon-x', stats: [{ name: 'hp', value: 60 }] }, '"4-100"')

    expect(fetch).toHaveBeenCalledWith('/api/v1/local-pokemons/9001', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', 'If-Match': '"4-100"' },
      body: JSON.stringify({ name: 'localmon-x', stats: [{ name: 'hp', value: 60 }] }),
    })
    expect(saved.etag).toBe('"5-100"')
  })

  it('reads the version and the local fields of the local record behind a PokeAPI detail', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({ ETag: '"3-100"' }),
        json: async () => ({ id: 25, name: 'pikachu', metadata: { region: 'Kanto' } }),
      }),
    )

    expect(await fetchLocalVersion(25)).toEqual({ etag: '"3-100"', metadata: { region: 'Kanto' } })
    expect(fetch).toHaveBeenCalledWith('/api/v1/local-pokemons/25')
  })

  it('has no local version for a Pokemon that is not in the local store', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 404 }))

    expect(await fetchLocalVersion(25)).toBeNull()
  })

  it('treats a stale If-Match and a lost race as the same conflict, but not a duplicate name', () => {
    expect(isConflict({ status: 412 })).toBe(true)
    expect(isConflict({ status: 409, title: 'Concurrent modification' })).toBe(true)
    expect(isConflict({ status: 409, title: 'Conflict' })).toBe(false)
    expect(isConflict({ status: 400 })).toBe(false)
  })

  it('keeps the title of the problem on the error', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 409,
        json: async () => ({ status: 409, title: 'Concurrent modification', detail: 'please retry' }),
      }),
    )

    const error = await updateLocalPokemon(9001, { mass: 2 }).catch((err) => err)

    expect(error.title).toBe('Concurrent modification')
    expect(isConflict(error)).toBe(true)
  })

  it('tells an entry whose category could not be loaded apart', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          content: [
            { id: 1, name: 'bulbasaur', image: 'b.png', category: null, mass: 6.9, skills: [], available: true, categoryAvailable: false },
            { id: 4, name: 'charmander', image: 'c.png', category: 'Lizard', mass: 8.5, skills: [], available: true, categoryAvailable: true },
          ],
          page: 0,
          size: 20,
          totalElements: 2,
          totalPages: 1,
        }),
      }),
    )

    const page = await fetchPokemonPage(0, 20)

    expect(page.items.map((item) => item.categoryAvailable)).toEqual([false, true])
    expect(page.items[0].image).toBe('b.png')
  })

  it('marks the category of a search card as missing when the species could not be loaded', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ id: 1, name: 'bulbasaur', image: 'b.png', descriptionAvailable: false, skills: [] }),
      }),
    )

    expect((await fetchPokemonByName('bulbasaur')).categoryAvailable).toBe(false)
  })

  it('tells a deleted record apart and restores it from PokeAPI', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValueOnce({
        ok: false,
        status: 404,
        json: async () => ({ status: 404, detail: 'Pokemon 25 was deleted from the local store', deleted: true }),
      }),
    )
    const error = await updateLocalData(25, { region: 'Kanto' }).catch((err) => err)
    expect(error.deleted).toBe(true)

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, status: 201, headers: new Headers({ ETag: '"0-100"' }), json: async () => ({}) }),
    )
    expect(await restoreFromPokeApi(25)).toEqual({ etag: '"0-100"' })
    expect(fetch).toHaveBeenCalledWith('/api/v1/local-pokemons/25/sync', { method: 'POST' })
  })

  it('tells a stale edit apart (412) so the form can offer to reload', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 412,
        json: async () => ({ status: 412, detail: 'Pokemon 9001 was changed since it was read' }),
      }),
    )

    const error = await updateLocalPokemon(9001, { mass: 2 }, '"1-100"').catch((err) => err)

    expect(error.status).toBe(412)
    expect(error.message).toBe('Could not save the Pokémon: Pokemon 9001 was changed since it was read (HTTP 412)')
  })

  it('creates a local record with POST', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, status: 201, headers: new Headers({ ETag: '"0-100"' }), json: async () => ({ id: 9001 }) }),
    )

    expect(await createLocalPokemon({ id: 9001, name: 'localmon' })).toEqual({ id: 9001, etag: '"0-100"' })
    expect(fetch).toHaveBeenCalledWith('/api/v1/local-pokemons', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: 9001, name: 'localmon' }),
    })
  })

  it('explains why a record could not be created', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: false, status: 400, json: async () => ({ status: 400, detail: 'Pokemon with id 9001 already exists' }) }),
    )

    await expect(createLocalPokemon({ id: 9001, name: 'x' })).rejects.toThrow(
      'Could not create the Pokémon: Pokemon with id 9001 already exists (HTTP 400)',
    )
  })

  it('deletes a local record with its version', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, status: 204 }))

    await deleteLocalPokemon(9001, '"3-100"')
    await deleteLocalPokemon(9002)

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/v1/local-pokemons/9001', { method: 'DELETE', headers: { 'If-Match': '"3-100"' } })
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/v1/local-pokemons/9002', { method: 'DELETE', headers: {} })
  })

  it('tells a deletion refused because the record changed (412)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: false, status: 412, json: async () => ({ status: 412, detail: 'changed' }) }),
    )

    const error = await deleteLocalPokemon(9001, '"1-100"').catch((err) => err)
    expect(error.status).toBe(412)
  })
})

