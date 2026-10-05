import { SOURCES } from '../api/pokemonApi'
import './SourceToggle.css'

const OPTIONS = [
  { value: SOURCES.REMOTE, label: 'PokeAPI' },
  { value: SOURCES.LOCAL, label: 'Local database' },
]

export default function SourceToggle({ value, onChange }) {
  return (
    <div className="source-toggle" role="group" aria-label="Data source">
      {OPTIONS.map((option) => (
        <button
          key={option.value}
          type="button"
          className={`source-toggle__option${value === option.value ? ' source-toggle__option--active' : ''}`}
          aria-pressed={value === option.value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
