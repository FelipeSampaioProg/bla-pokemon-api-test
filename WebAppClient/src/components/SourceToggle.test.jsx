import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import SourceToggle from './SourceToggle'

describe('SourceToggle', () => {
  it('marks the selected source and reports changes', () => {
    const onChange = vi.fn()
    render(<SourceToggle value="remote" onChange={onChange} />)

    expect(screen.getByRole('button', { name: 'PokeAPI' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Local database' })).toHaveAttribute('aria-pressed', 'false')

    fireEvent.click(screen.getByRole('button', { name: 'Local database' }))

    expect(onChange).toHaveBeenCalledWith('local')
  })
})
