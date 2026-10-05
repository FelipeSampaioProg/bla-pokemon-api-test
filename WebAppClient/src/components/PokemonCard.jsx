import './PokemonCard.css'

export default function PokemonCard({ pokemon, onSelect }) {
  const { id, name, image, subtitle, mass, tags, available = true, categoryAvailable = true } = pokemon

  // Covers the whole card, so a click anywhere opens the detail view.
  const selectButton = onSelect && (
    <button
      type="button"
      className="pokemon-card__select"
      onClick={onSelect}
      aria-label={`View details of ${name}`}
    />
  )

  if (!available) {
    return (
      <article className="pokemon-card pokemon-card--unavailable">
        <span className="pokemon-card__id">#{String(id).padStart(3, '0')}</span>
        <div className="pokemon-card__image-wrapper">
          <div className="pokemon-card__image pokemon-card__image--placeholder">!</div>
        </div>
        <h3 className="pokemon-card__name">{name}</h3>
        <p className="pokemon-card__unavailable" role="status">
          Data not available right now. Try again later.
        </p>
        {selectButton}
      </article>
    )
  }

  return (
    <article className="pokemon-card">
      <span className="pokemon-card__id">#{String(id).padStart(3, '0')}</span>
      <div className="pokemon-card__image-wrapper">
        {image ? (
          <img className="pokemon-card__image" src={image} alt={name} loading="lazy" />
        ) : (
          <div className="pokemon-card__image pokemon-card__image--placeholder">?</div>
        )}
      </div>
      <h3 className="pokemon-card__name">{name}</h3>
      {subtitle && <p className="pokemon-card__subtitle">{subtitle}</p>}
      {!categoryAvailable && (
        <p className="pokemon-card__subtitle pokemon-card__subtitle--missing">Category not available right now</p>
      )}
      {typeof mass === 'number' && (
        <p className="pokemon-card__mass">Weight: {mass} kg</p>
      )}
      {tags?.length > 0 && (
        <ul className="pokemon-card__tags">
          {tags.slice(0, 4).map((tag) => (
            <li key={tag} className="pokemon-card__tag">
              {tag}
            </li>
          ))}
        </ul>
      )}
      {selectButton}
    </article>
  )
}
