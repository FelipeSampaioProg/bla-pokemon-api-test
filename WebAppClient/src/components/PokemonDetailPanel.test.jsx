import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import PokemonDetailPanel from './PokemonDetailPanel'

const longDescription =
  'When several of these POKéMON gather, their electricity could build and cause lightning storms. It keeps its tail raised.'

const eeveeDetail = {
  id: 133,
  name: 'eevee',
  image: 'eevee.png',
  height: 0.3,
  mass: 6.5,
  description: longDescription,
  category: 'Evolution Pokemon',
  skills: ['run-away', 'adaptability'],
  stats: [
    { name: 'hp', value: 55 },
    { name: 'attack', value: 55 },
    { name: 'defense', value: 50 },
    { name: 'special-attack', value: 45 },
    { name: 'special-defense', value: 65 },
    { name: 'speed', value: 55 },
  ],
  lineage: {
    name: 'eevee',
    evolvesTo: [
      { name: 'vaporeon', evolvesTo: [] },
      { name: 'jolteon', evolvesTo: [] },
    ],
  },
  evolutionAvailable: true,
}

function renderPanel(state, props = {}) {
  const onClose = vi.fn()
  render(<PokemonDetailPanel state={state} onClose={onClose} {...props} />)
  return { onClose }
}

describe('PokemonDetailPanel', () => {
  it('shows image, all six stats, the full description and the lineage', () => {
    renderPanel({ requested: 'eevee', loading: false, error: null, detail: eeveeDetail })

    const dialog = screen.getByRole('dialog', { name: 'eevee' })
    expect(within(dialog).getByRole('img', { name: 'eevee' })).toHaveAttribute('src', 'eevee.png')
    expect(screen.getByText('#133')).toBeInTheDocument()
    expect(screen.getByText('Height: 0.3 m')).toBeInTheDocument()
    expect(screen.getByText('Weight: 6.5 kg')).toBeInTheDocument()
    for (const label of ['HP', 'Attack', 'Defense', 'Sp. Attack', 'Sp. Defense', 'Speed']) {
      expect(screen.getByText(label)).toBeInTheDocument()
    }
    expect(screen.getByText(longDescription)).toBeInTheDocument()
    expect(screen.getByText('Evolution Pokemon')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Abilities' })).toBeInTheDocument()
    expect(screen.getByText('run-away')).toBeInTheDocument()
    expect(screen.getByText('adaptability')).toBeInTheDocument()
    expect(screen.getByText('eevee', { selector: '[aria-current="true"]' })).toBeInTheDocument()
    expect(screen.getByText('vaporeon')).toBeInTheDocument()
    expect(screen.getByText('jolteon')).toBeInTheDocument()
  })

  it('navigates to an evolution when it is clicked', () => {
    const onSelectEvolution = vi.fn()
    renderPanel({ requested: 'eevee', loading: false, error: null, detail: eeveeDetail }, { onSelectEvolution })

    fireEvent.click(screen.getByRole('button', { name: 'jolteon' }))

    expect(onSelectEvolution).toHaveBeenCalledWith('jolteon')
  })

  it('shows evolution names as plain text when navigation is not available', () => {
    renderPanel({ requested: 'eevee', loading: false, error: null, detail: eeveeDetail })

    expect(screen.queryByRole('button', { name: 'jolteon' })).not.toBeInTheDocument()
    expect(screen.getByText('jolteon')).toBeInTheDocument()
  })

  it('warns when the evolution chain could not be loaded', () => {
    renderPanel({
      requested: 'pikachu',
      loading: false,
      error: null,
      detail: { ...eeveeDetail, name: 'pikachu', lineage: { name: 'pikachu', evolvesTo: [] }, evolutionAvailable: false },
    })

    expect(screen.getByRole('status')).toHaveTextContent('Could not load the evolution line')
    expect(screen.queryByText('vaporeon')).not.toBeInTheDocument()
  })

  it('shows loading, error and not found states', () => {
    const { unmount } = render(
      <PokemonDetailPanel state={{ requested: 'eevee', loading: true, error: null, detail: null }} onClose={vi.fn()} />,
    )
    expect(screen.getByText('Loading...')).toBeInTheDocument()
    unmount()

    const second = render(
      <PokemonDetailPanel
        state={{ requested: 'eevee', loading: false, error: 'Could not load the Pokémon (HTTP 502)', detail: null }}
        onClose={vi.fn()}
      />,
    )
    expect(screen.getByText('Could not load the Pokémon (HTTP 502)')).toBeInTheDocument()
    second.unmount()

    render(
      <PokemonDetailPanel state={{ requested: 'missingno', loading: false, error: null, detail: null }} onClose={vi.fn()} />,
    )
    expect(screen.getByText('No Pokémon named "missingno" was found.')).toBeInTheDocument()
  })

  it('closes with the close button, the Escape key and a click outside', () => {
    const { onClose } = renderPanel({ requested: 'eevee', loading: false, error: null, detail: eeveeDetail })

    fireEvent.click(screen.getByRole('button', { name: 'Close' }))
    fireEvent.keyDown(document, { key: 'Escape' })
    fireEvent.click(screen.getByRole('dialog').parentElement)
    fireEvent.click(screen.getByRole('dialog'))

    expect(onClose).toHaveBeenCalledTimes(3)
  })

  it('highlights the species in the lineage when the pokemon has another name', () => {
    renderPanel({
      requested: 'lycanroc',
      loading: false,
      error: null,
      detail: {
        ...eeveeDetail,
        id: 745,
        name: 'lycanroc-midday',
        species: 'lycanroc',
        lineage: { name: 'rockruff', evolvesTo: [{ name: 'lycanroc', evolvesTo: [] }] },
      },
    })

    expect(screen.getByText('lycanroc', { selector: '[aria-current="true"]' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'lycanroc-midday' })).toBeInTheDocument()
  })

  it('warns when the description could not be loaded', () => {
    renderPanel({
      requested: 'pikachu',
      loading: false,
      error: null,
      detail: { ...eeveeDetail, description: null, descriptionAvailable: false },
    })

    expect(screen.getByRole('status')).toHaveTextContent('Could not load the description')
    expect(screen.queryByText('No description available.')).not.toBeInTheDocument()
  })

  it('keeps the focus inside the dialog and locks the page scroll while open', () => {
    const opener = document.createElement('button')
    opener.textContent = 'open'
    document.body.appendChild(opener)
    opener.focus()

    const { unmount } = render(
      <PokemonDetailPanel
        state={{ requested: 'eevee', loading: false, error: null, detail: eeveeDetail }}
        onClose={vi.fn()}
        onSelectEvolution={vi.fn()}
      />,
    )

    const close = screen.getByRole('button', { name: 'Close' })
    const lastEvolution = screen.getByRole('button', { name: 'jolteon' })
    expect(close).toHaveFocus()
    expect(document.body.style.overflow).toBe('hidden')

    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true })
    expect(lastEvolution).toHaveFocus()

    fireEvent.keyDown(document, { key: 'Tab' })
    expect(close).toHaveFocus()

    unmount()
    expect(document.body.style.overflow).toBe('')
    expect(opener).toHaveFocus()
    opener.remove()
  })

  it('tells when to try again when the rate limit hid the description and the lineage', () => {
    renderPanel({
      requested: 'pikachu',
      loading: false,
      error: null,
      detail: {
        ...eeveeDetail,
        name: 'pikachu',
        description: null,
        descriptionAvailable: false,
        lineage: { name: 'pikachu', evolvesTo: [] },
        evolutionAvailable: false,
        retryAfterSeconds: 20,
      },
    })

    const notices = screen.getAllByRole('status')
    expect(notices[0]).toHaveTextContent(
      'Could not load the description. PokeAPI is limiting requests; try again in 20 seconds.',
    )
    expect(notices[1]).toHaveTextContent(
      'Could not load the evolution line. PokeAPI is limiting requests; try again in 20 seconds.',
    )
  })

  it('shows the proprietary fields added locally', () => {
    renderPanel({
      requested: 'eevee',
      loading: false,
      error: null,
      detail: { ...eeveeDetail, metadata: { localizedName: 'Evoli', region: 'Kanto', classificationTag: null } },
    })

    expect(screen.getByRole('heading', { name: 'Local data' })).toBeInTheDocument()
    expect(screen.getByText('Evoli')).toBeInTheDocument()
    expect(screen.getByText('Kanto')).toBeInTheDocument()
    expect(screen.queryByText('Classification')).not.toBeInTheDocument()
  })

  it('has no local data section when nothing was added locally', () => {
    renderPanel({ requested: 'eevee', loading: false, error: null, detail: eeveeDetail })

    expect(screen.queryByRole('heading', { name: 'Local data' })).not.toBeInTheDocument()
  })
})
