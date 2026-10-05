import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import LocalDataSection from './LocalDataSection'

describe('LocalDataSection', () => {
  it('shows nothing when there is no local data and it cannot be edited', () => {
    const { container } = render(<LocalDataSection pokemonId={25} metadata={null} />)

    expect(container).toBeEmptyDOMElement()
  })

  it('adds local data to a Pokemon that has none', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined)
    render(<LocalDataSection pokemonId={25} metadata={null} onSave={onSave} />)

    expect(screen.getByText(/No local data yet/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Add local data' }))

    expect(screen.getByLabelText('Localized name')).toHaveFocus()
    fireEvent.change(screen.getByLabelText('Localized name'), { target: { value: 'Pikachu (ES)' } })
    fireEvent.change(screen.getByLabelText('Region'), { target: { value: 'Kanto' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument())
    expect(onSave).toHaveBeenCalledWith(25, { localizedName: 'Pikachu (ES)', region: 'Kanto', classificationTag: '' })
  })

  it('edits the existing values', () => {
    render(
      <LocalDataSection
        pokemonId={25}
        metadata={{ localizedName: 'Pikachu (ES)', region: 'Kanto', classificationTag: null }}
        onSave={vi.fn()}
      />,
    )

    expect(screen.getByText('Kanto')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))

    expect(screen.getByLabelText('Localized name')).toHaveValue('Pikachu (ES)')
    expect(screen.getByLabelText('Region')).toHaveValue('Kanto')
    expect(screen.getByLabelText('Classification')).toHaveValue('')
    expect(screen.getByLabelText('Region')).toHaveAttribute('maxLength', '100')
  })

  it('keeps the form open and shows the error when saving fails', async () => {
    const onSave = vi.fn().mockRejectedValue(
      new Error('Could not save the local data: Pokemon region must have at most 100 characters (HTTP 400)'),
    )
    render(<LocalDataSection pokemonId={25} metadata={null} onSave={onSave} />)

    fireEvent.click(screen.getByRole('button', { name: 'Add local data' }))
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Pokemon region must have at most 100 characters')
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled()
  })

  it('cancels without saving', () => {
    const onSave = vi.fn()
    render(<LocalDataSection pokemonId={25} metadata={null} onSave={onSave} />)

    fireEvent.click(screen.getByRole('button', { name: 'Add local data' }))
    fireEvent.change(screen.getByLabelText('Region'), { target: { value: 'Kanto' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByLabelText('Region')).not.toBeInTheDocument()
    expect(onSave).not.toHaveBeenCalled()
  })

  it('offers to load the latest values when the record changed meanwhile', async () => {
    const conflict = Object.assign(new Error('Could not save the local data (HTTP 412)'), { status: 412 })
    const onReload = vi.fn()
    render(<LocalDataSection pokemonId={25} metadata={{ region: 'Kanto' }} onSave={vi.fn().mockRejectedValue(conflict)} onReload={onReload} />)

    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))
    fireEvent.change(screen.getByLabelText('Region'), { target: { value: 'Johto' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Load latest version' }))

    expect(onReload).toHaveBeenCalled()
    expect(screen.queryByLabelText('Region')).not.toBeInTheDocument()
  })

  it('only shows the error of a conflict when there is no way to reload', async () => {
    const conflict = Object.assign(new Error('Could not save the local data (HTTP 412)'), { status: 412 })
    render(<LocalDataSection pokemonId={25} metadata={{ region: 'Kanto' }} onSave={vi.fn().mockRejectedValue(conflict)} />)

    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('HTTP 412')
    expect(screen.queryByRole('button', { name: 'Load latest version' })).not.toBeInTheDocument()
  })

  it('offers to restore a deleted record from PokeAPI and save', async () => {
    const deleted = Object.assign(new Error('Could not save the local data: was deleted (HTTP 404)'), {
      status: 404,
      deleted: true,
    })
    const onRestore = vi.fn().mockResolvedValue(undefined)
    render(<LocalDataSection pokemonId={25} metadata={null} onSave={vi.fn().mockRejectedValue(deleted)} onRestore={onRestore} />)

    fireEvent.click(screen.getByRole('button', { name: 'Add local data' }))
    fireEvent.change(screen.getByLabelText('Region'), { target: { value: 'Kanto' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Restore from PokeAPI and save' }))

    await waitFor(() => expect(screen.queryByLabelText('Region')).not.toBeInTheDocument())
    expect(onRestore).toHaveBeenCalledWith(25, { localizedName: '', region: 'Kanto', classificationTag: '' })
  })

  it('does not offer a restore for a record that simply does not exist', async () => {
    const missing = Object.assign(new Error('Could not save the local data: not found (HTTP 404)'), { status: 404 })
    render(<LocalDataSection pokemonId={25} metadata={null} onSave={vi.fn().mockRejectedValue(missing)} onRestore={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Add local data' }))
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('HTTP 404')
    expect(screen.queryByRole('button', { name: /Restore/ })).not.toBeInTheDocument()
  })

  it('cancels the edit on Escape without letting the panel close', () => {
    const panelEscape = vi.fn()
    document.addEventListener('keydown', panelEscape)
    render(<LocalDataSection pokemonId={25} metadata={null} onSave={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Add local data' }))
    fireEvent.keyDown(screen.getByLabelText('Region'), { key: 'Escape' })

    expect(screen.queryByLabelText('Region')).not.toBeInTheDocument()
    expect(panelEscape).not.toHaveBeenCalled()
    document.removeEventListener('keydown', panelEscape)
  })
})
