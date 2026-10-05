import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import CreatePokemonDialog from './CreatePokemonDialog'

function fill(label, value) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

describe('CreatePokemonDialog', () => {
  it('creates a record with the fields that were filled in', async () => {
    const onCreate = vi.fn().mockResolvedValue(undefined)
    render(<CreatePokemonDialog onCreate={onCreate} onClose={vi.fn()} />)

    expect(screen.getByRole('dialog', { name: 'New Pokémon' })).toBeInTheDocument()
    expect(screen.getByLabelText('Id')).toHaveFocus()
    fill('Id', '9001')
    fill('Name', ' Localmon ')
    fill('Weight (kg)', '12.5')
    fill('Region', 'Kanto')
    fireEvent.click(screen.getByRole('button', { name: 'Add stat' }))
    fill('Stat 1 name', 'hp')
    fill('Stat 1 value', '50')
    fireEvent.click(screen.getByRole('button', { name: 'Create' }))

    await waitFor(() => expect(onCreate).toHaveBeenCalled())
    expect(onCreate).toHaveBeenCalledWith({
      id: 9001,
      name: 'localmon',
      mass: 12.5,
      stats: [{ name: 'hp', value: 50 }],
      region: 'Kanto',
    })
  })

  it('checks the id and the name before sending', () => {
    const onCreate = vi.fn()
    render(<CreatePokemonDialog onCreate={onCreate} onClose={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Create' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Id must be a positive whole number.')

    fill('Id', '9001')
    fireEvent.click(screen.getByRole('button', { name: 'Create' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Name is required.')

    fill('Name', 'mr mime')
    fireEvent.click(screen.getByRole('button', { name: 'Create' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Name may only contain letters')
    expect(onCreate).not.toHaveBeenCalled()
  })

  it('keeps the form open with the API error', async () => {
    const onCreate = vi.fn().mockRejectedValue(
      new Error('Could not create the Pokémon: Pokemon id 25 belongs to PokeAPI Pokemon pikachu (HTTP 400)'),
    )
    render(<CreatePokemonDialog onCreate={onCreate} onClose={vi.fn()} />)

    fill('Id', '25')
    fill('Name', 'my-mon')
    fireEvent.click(screen.getByRole('button', { name: 'Create' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('belongs to PokeAPI Pokemon pikachu')
    expect(screen.getByRole('button', { name: 'Create' })).toBeEnabled()
  })

  it('closes with Cancel and with Escape', () => {
    const onClose = vi.fn()
    render(<CreatePokemonDialog onCreate={vi.fn()} onClose={onClose} />)

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    fireEvent.keyDown(document, { key: 'Escape' })

    expect(onClose).toHaveBeenCalledTimes(2)
  })
})
