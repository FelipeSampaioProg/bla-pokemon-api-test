import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import PokemonCard from './PokemonCard'

describe('PokemonCard', () => {
  it('renders pokemon details and limits tags to four items', () => {
    render(
      <PokemonCard
        pokemon={{
          id: 7,
          name: 'Squirtle',
          image: 'squirtle.png',
          subtitle: 'Tiny Turtle',
          mass: 9,
          tags: ['torrent', 'rain-dish', 'shell', 'water', 'extra-tag'],
        }}
      />,
    )

    expect(screen.getByText('#007')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Squirtle' })).toHaveAttribute('src', 'squirtle.png')
    expect(screen.getByText('Tiny Turtle')).toBeInTheDocument()
    expect(screen.getByText('Weight: 9 kg')).toBeInTheDocument()
    expect(screen.getByText('torrent')).toBeInTheDocument()
    expect(screen.queryByText('extra-tag')).not.toBeInTheDocument()
  })

  it('shows placeholder when image is missing', () => {
    render(
      <PokemonCard
        pokemon={{
          id: 4,
          name: 'Charmander',
          image: null,
          subtitle: null,
          mass: null,
          tags: [],
        }}
      />,
    )

    expect(screen.queryByRole('img', { name: 'Charmander' })).not.toBeInTheDocument()
    expect(screen.getByText('?')).toBeInTheDocument()
  })

  it('keeps the card when only the category could not be loaded', () => {
    render(
      <PokemonCard
        pokemon={{ id: 1, name: 'bulbasaur', image: 'b.png', subtitle: null, mass: 6.9, tags: ['overgrow'], categoryAvailable: false }}
      />,
    )

    expect(screen.getByText('Category not available right now')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'bulbasaur' })).toBeInTheDocument()
    expect(screen.getByText('Weight: 6.9 kg')).toBeInTheDocument()
    expect(screen.getByText('overgrow')).toBeInTheDocument()
    expect(screen.queryByText(/Data not available/)).not.toBeInTheDocument()
  })

  it('shows an unavailable state when the API could not load the pokemon', () => {
    render(
      <PokemonCard
        pokemon={{
          id: 4,
          name: 'charmander',
          image: null,
          subtitle: null,
          mass: null,
          tags: [],
          available: false,
        }}
      />,
    )

    expect(screen.getByText('charmander')).toBeInTheDocument()
    expect(screen.getByText('!')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Data not available right now')
    expect(screen.queryByText(/Weight:/)).not.toBeInTheDocument()
  })

  it('opens the detail when the card is selected', () => {
    const onSelect = vi.fn()
    render(
      <PokemonCard
        pokemon={{ id: 7, name: 'Squirtle', image: null, subtitle: null, mass: 9, tags: [] }}
        onSelect={onSelect}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'View details of Squirtle' }))

    expect(onSelect).toHaveBeenCalledTimes(1)
  })

  it('is not selectable without an onSelect handler', () => {
    render(<PokemonCard pokemon={{ id: 7, name: 'Squirtle', image: null, subtitle: null, mass: 9, tags: [] }} />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})
