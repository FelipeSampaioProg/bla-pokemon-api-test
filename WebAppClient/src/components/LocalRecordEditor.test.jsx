import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import LocalRecordEditor from './LocalRecordEditor'
import { changesFrom, emptyCreateForm, toCreateRequest, validate, validateNew } from './localRecordForm'

const detail = {
  id: 9001,
  name: 'localmon',
  image: 'https://img.example/localmon.png',
  category: 'Custom',
  height: 1,
  mass: 12.5,
  description: 'Created locally',
  stats: [
    { name: 'hp', value: 50 },
    { name: 'speed', value: 30 },
  ],
  skillLinks: [
    { name: 'static', url: 'https://pokeapi.co/api/v2/ability/9/' },
    { name: 'splash', url: null },
  ],
}

const form = (overrides = {}) => ({
  name: 'localmon',
  image: 'https://img.example/localmon.png',
  category: 'Custom',
  height: '1',
  mass: '12.5',
  description: 'Created locally',
  stats: [
    { name: 'hp', value: '50' },
    { name: 'speed', value: '30' },
  ],
  skills: ['static', 'splash'],
  ...overrides,
})

function openEditor(props = {}) {
  render(<LocalRecordEditor detail={detail} onSave={vi.fn()} onReload={vi.fn()} {...props} />)
  fireEvent.click(screen.getByRole('button', { name: 'Edit Pokémon' }))
}

describe('validate', () => {
  it('accepts the current record', () => {
    expect(validate(form(), detail)).toBeNull()
  })

  it.each([
    [{ name: ' ' }, 'Name is required.'],
    [{ name: 'mr mime' }, 'Name may only contain letters, digits and single hyphens between them (like mr-mime).'],
    [{ name: '123' }, 'Name must contain a letter; a numeric name would be read as an id.'],
    [{ name: 'x'.repeat(101) }, 'Name must have at most 100 characters.'],
    [{ image: 'local.png' }, 'Image must be an absolute http or https URL.'],
    [{ height: '1001' }, 'Height must be between 0 and 1000 m.'],
    [{ mass: '-1' }, 'Weight must be between 0 and 10000 kg.'],
    [{ mass: '' }, 'Weight cannot be removed; enter a value.'],
    [{ stats: [{ name: 'hp', value: '1.5' }] }, 'Stat values must be whole numbers between 0 and 255.'],
    [{ stats: [{ name: 'hp', value: '256' }] }, 'Stat values must be whole numbers between 0 and 255.'],
    [{ stats: [{ name: 'hp', value: '' }] }, 'Stat values must be whole numbers between 0 and 255.'],
    [{ stats: [{ name: '', value: '1' }] }, 'Every stat needs a name.'],
    [{ stats: [{ name: 'hp', value: '1' }, { name: 'HP', value: '2' }] }, 'Each stat may appear only once.'],
    [{ skills: ['static', ' Static '] }, 'Each ability may appear only once.'],
    [{ skills: [''] }, 'Every ability needs a name.'],
  ])('rejects %j', (overrides, message) => {
    expect(validate(form(overrides), detail)).toBe(message)
  })

  it('checks only the fields being changed, like the API', () => {
    const legacy = { ...detail, image: 'local.png' }

    expect(validate(form({ image: 'local.png', mass: '13' }), legacy)).toBeNull()
  })
})

describe('changesFrom', () => {
  it('sends nothing when nothing changed', () => {
    expect(changesFrom(detail, form())).toEqual({})
  })

  it('sends only the changed fields, normalized', () => {
    expect(changesFrom(detail, form({ name: ' LocalMon-X ', mass: '13', category: '' }))).toEqual({
      name: 'localmon-x',
      category: '',
      mass: 13,
    })
  })

  it('sends the whole stat list when one stat changed', () => {
    expect(changesFrom(detail, form({ stats: [{ name: 'hp', value: '60' }, { name: 'speed', value: '30' }] }))).toEqual({
      stats: [
        { name: 'hp', value: 60 },
        { name: 'speed', value: 30 },
      ],
    })
  })

  it('keeps the links of the abilities that stay', () => {
    expect(changesFrom(detail, form({ skills: ['static', 'levitate'] }))).toEqual({
      skills: [
        { name: 'static', url: 'https://pokeapi.co/api/v2/ability/9/' },
        { name: 'levitate', url: null },
      ],
    })
  })
})

describe('LocalRecordEditor', () => {
  it('saves only what was changed', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined)
    openEditor({ onSave })

    expect(screen.getByLabelText('Name')).toHaveFocus()
    fireEvent.change(screen.getByLabelText('Stat 1 value'), { target: { value: '60' } })
    fireEvent.click(screen.getByRole('button', { name: 'Remove ability splash' }))
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument())
    expect(onSave).toHaveBeenCalledWith(9001, {
      stats: [
        { name: 'hp', value: 60 },
        { name: 'speed', value: 30 },
      ],
      skills: [{ name: 'static', url: 'https://pokeapi.co/api/v2/ability/9/' }],
    })
  })

  it('adds a stat and an ability', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined)
    openEditor({ onSave })

    fireEvent.click(screen.getByRole('button', { name: 'Add stat' }))
    fireEvent.change(screen.getByLabelText('Stat 3 name'), { target: { value: 'attack' } })
    fireEvent.change(screen.getByLabelText('Stat 3 value'), { target: { value: '70' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add ability' }))
    fireEvent.change(screen.getByLabelText('Ability 3'), { target: { value: 'Levitate' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(onSave).toHaveBeenCalled())
    expect(onSave.mock.calls[0][1].stats).toContainEqual({ name: 'attack', value: 70 })
    expect(onSave.mock.calls[0][1].skills).toContainEqual({ name: 'levitate', url: null })
  })

  it('shows the problem and does not save an invalid form', () => {
    const onSave = vi.fn()
    openEditor({ onSave })

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'mr mime' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Name may only contain letters')
    expect(onSave).not.toHaveBeenCalled()
  })

  it('closes without a request when nothing changed', () => {
    const onSave = vi.fn()
    openEditor({ onSave })

    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(onSave).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Edit Pokémon' })).toBeInTheDocument()
  })

  it('keeps the form open with the API error', async () => {
    const onSave = vi.fn().mockRejectedValue(new Error('Could not save the Pokémon: name already exists (HTTP 400)'))
    openEditor({ onSave })

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'pikachu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('name already exists')
    expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument()
  })

  it('offers to load the latest version when the record changed meanwhile (412)', async () => {
    const conflict = Object.assign(new Error('Could not save the Pokémon: Pokemon 9001 was changed (HTTP 412)'), {
      status: 412,
    })
    const onReload = vi.fn()
    openEditor({ onSave: vi.fn().mockRejectedValue(conflict), onReload })

    fireEvent.change(screen.getByLabelText('Weight (kg)'), { target: { value: '20' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Load latest version' }))

    expect(onReload).toHaveBeenCalled()
  })

  it('offers the same reload when another write won the race (409 concurrent modification)', async () => {
    const race = Object.assign(new Error('Could not save the Pokémon: please retry (HTTP 409)'), {
      status: 409,
      title: 'Concurrent modification',
    })
    openEditor({ onSave: vi.fn().mockRejectedValue(race) })

    fireEvent.change(screen.getByLabelText('Weight (kg)'), { target: { value: '20' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('button', { name: 'Load latest version' })).toBeInTheDocument()
  })

  it('does not offer a reload for a duplicate name (409 conflict)', async () => {
    const duplicate = Object.assign(new Error('Could not save the Pokémon: duplicate id or name (HTTP 409)'), {
      status: 409,
      title: 'Conflict',
    })
    openEditor({ onSave: vi.fn().mockRejectedValue(duplicate) })

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'pikachu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('duplicate id or name')
    expect(screen.queryByRole('button', { name: 'Load latest version' })).not.toBeInTheDocument()
  })

  it('cancels the edit with Escape without letting the panel close', () => {
    const outside = vi.fn()
    document.addEventListener('keydown', outside)
    openEditor()

    fireEvent.keyDown(screen.getByLabelText('Name'), { key: 'Escape' })

    expect(screen.getByRole('button', { name: 'Edit Pokémon' })).toBeInTheDocument()
    expect(outside).not.toHaveBeenCalled()
    document.removeEventListener('keydown', outside)
  })

  it('deletes the record after a confirmation', async () => {
    const onDelete = vi.fn().mockResolvedValue(undefined)
    render(<LocalRecordEditor detail={detail} onSave={vi.fn()} onReload={vi.fn()} onDelete={onDelete} />)

    fireEvent.click(screen.getByRole('button', { name: 'Delete Pokémon' }))
    expect(screen.getByRole('group', { name: 'Confirm deletion' })).toHaveTextContent('Delete localmon from the local database?')
    expect(onDelete).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Yes, delete' }))

    await waitFor(() => expect(onDelete).toHaveBeenCalledWith(9001))
  })

  it('keeps the record when the deletion is not confirmed', () => {
    const onDelete = vi.fn()
    const outside = vi.fn()
    document.addEventListener('keydown', outside)
    render(<LocalRecordEditor detail={detail} onSave={vi.fn()} onReload={vi.fn()} onDelete={onDelete} />)

    fireEvent.click(screen.getByRole('button', { name: 'Delete Pokémon' }))
    fireEvent.click(screen.getByRole('button', { name: 'Keep it' }))
    fireEvent.click(screen.getByRole('button', { name: 'Delete Pokémon' }))
    fireEvent.keyDown(screen.getByRole('button', { name: 'Yes, delete' }), { key: 'Escape' })

    expect(screen.getByRole('button', { name: 'Delete Pokémon' })).toBeInTheDocument()
    expect(onDelete).not.toHaveBeenCalled()
    expect(outside).not.toHaveBeenCalled()
    document.removeEventListener('keydown', outside)
  })

  it('offers to load the latest version when the record changed before the deletion', async () => {
    const conflict = Object.assign(new Error('Could not delete the Pokémon (HTTP 412)'), { status: 412 })
    const onReload = vi.fn()
    render(<LocalRecordEditor detail={detail} onSave={vi.fn()} onReload={onReload} onDelete={vi.fn().mockRejectedValue(conflict)} />)

    fireEvent.click(screen.getByRole('button', { name: 'Delete Pokémon' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes, delete' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Load latest version' }))

    expect(onReload).toHaveBeenCalled()
  })

  it('offers no deletion without a handler', () => {
    render(<LocalRecordEditor detail={detail} onSave={vi.fn()} onReload={vi.fn()} />)

    expect(screen.queryByRole('button', { name: 'Delete Pokémon' })).not.toBeInTheDocument()
  })
})

describe('creation helpers', () => {
  const filled = (overrides) => ({ ...emptyCreateForm(), id: '9001', name: 'localmon', ...overrides })

  it('requires a positive whole id and a name, then the usual rules', () => {
    expect(validateNew(filled({ id: '' }))).toBe('Id must be a positive whole number.')
    expect(validateNew(filled({ id: '0' }))).toBe('Id must be a positive whole number.')
    expect(validateNew(filled({ id: '1.5' }))).toBe('Id must be a positive whole number.')
    expect(validateNew(filled({ name: '  ' }))).toBe('Name is required.')
    expect(validateNew(filled({ image: 'local.png' }))).toBe('Image must be an absolute http or https URL.')
    expect(validateNew(filled({}))).toBeNull()
  })

  it('sends only what was filled in', () => {
    expect(toCreateRequest(filled({ name: ' LocalMon ', description: ' Made here ', skills: ['Splash'], classificationTag: 'CUSTOM' })))
      .toEqual({ id: 9001, name: 'localmon', description: 'Made here', skills: [{ name: 'splash', url: null }], classificationTag: 'CUSTOM' })
  })
})

