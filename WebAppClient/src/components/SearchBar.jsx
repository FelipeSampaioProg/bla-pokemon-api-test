import './SearchBar.css'

export default function SearchBar({ value, onChange, onSubmit }) {
  function handleSubmit(event) {
    event.preventDefault()
    onSubmit(value.trim())
  }

  return (
    <form className="search-bar" onSubmit={handleSubmit}>
      <input
        type="text"
        className="search-bar__input"
        placeholder="Search Pokémon by name or id..."
        aria-label="Search Pokémon by name or id"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        autoFocus
      />
      <button type="submit" className="search-bar__button">
        Search
      </button>
    </form>
  )
}
