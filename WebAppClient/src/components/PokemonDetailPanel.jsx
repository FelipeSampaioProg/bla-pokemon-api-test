import { rateLimitMessage } from '../api/pokemonApi'
import LocalDataSection from './LocalDataSection'
import LocalRecordEditor from './LocalRecordEditor'
import Modal from './Modal'
import './PokemonDetailPanel.css'

const STAT_LABELS = {
  hp: 'HP',
  attack: 'Attack',
  defense: 'Defense',
  'special-attack': 'Sp. Attack',
  'special-defense': 'Sp. Defense',
  speed: 'Speed',
}

// Highest base stat in the games (Blissey's HP): bars are drawn against it.
const MAX_BASE_STAT = 255

function statLabel(name) {
  return STAT_LABELS[name] ?? name.replace(/-/g, ' ')
}

function EvolutionTree({ node, current, onSelect }) {
  const isCurrent = node.name === current
  return (
    <li className="evolution__item">
      {onSelect && !isCurrent ? (
        <button type="button" className="evolution__name evolution__name--link" onClick={() => onSelect(node.name)}>
          {node.name}
        </button>
      ) : (
        <span
          className={`evolution__name${isCurrent ? ' evolution__name--current' : ''}`}
          aria-current={isCurrent ? 'true' : undefined}
        >
          {node.name}
        </span>
      )}
      {node.evolvesTo.length > 0 && (
        <ul className="evolution__children">
          {node.evolvesTo.map((child) => (
            <EvolutionTree key={child.name} node={child} current={current} onSelect={onSelect} />
          ))}
        </ul>
      )}
    </li>
  )
}

export default function PokemonDetailPanel({
  state,
  onClose,
  onSelectEvolution,
  onSaveLocalData,
  onReloadLocalData,
  onRestoreLocalData,
  onSaveRecord,
  onReloadRecord,
  onDeleteRecord,
}) {
  const { requested, loading, error, detail } = state

  return (
    <Modal labelledBy={detail ? 'detail-title' : undefined} label="Pokémon detail" onClose={onClose}>
      {loading && <p className="detail__status">Loading...</p>}
      {!loading && error && <p className="detail__status detail__status--error">{error}</p>}
      {!loading && !error && !detail && (
        <p className="detail__status">No Pokémon named "{requested}" was found.</p>
      )}

      {!loading && !error && detail && (
        <>
          <header className="detail__header">
            {detail.image ? (
              <img className="detail__image" src={detail.image} alt={detail.name} />
            ) : (
              <div className="detail__image detail__image--placeholder">?</div>
            )}
            <div>
              <span className="detail__id">#{String(detail.id).padStart(3, '0')}</span>
              <h2 id="detail-title" className="detail__name">{detail.name}</h2>
              {detail.category && <p className="detail__category">{detail.category}</p>}
              <p className="detail__meta">
                {typeof detail.height === 'number' && <span>Height: {detail.height} m</span>}
                {typeof detail.mass === 'number' && <span>Weight: {detail.mass} kg</span>}
              </p>
            </div>
          </header>

          {detail.skills?.length > 0 && (
            <section className="detail__section" aria-labelledby="detail-abilities">
              <h3 id="detail-abilities" className="detail__heading">Abilities</h3>
              <ul className="detail__abilities">
                {detail.skills.map((skill) => (
                  <li key={skill} className="detail__ability">
                    {skill}
                  </li>
                ))}
              </ul>
            </section>
          )}

          {/* Only local records can be edited; a new version (ETag) resets the editor. */}
          {onSaveRecord && (
            <LocalRecordEditor
              key={`${detail.id}:${detail.localEtag ?? ''}`}
              detail={detail}
              onSave={(id, changes) => onSaveRecord(id, changes, detail.localEtag)}
              onReload={() => onReloadRecord?.(detail.id)}
              onDelete={onDeleteRecord && ((id) => onDeleteRecord(id, detail.localEtag))}
            />
          )}

          <LocalDataSection
            key={detail.id}
            pokemonId={detail.id}
            metadata={detail.metadata}
            onSave={onSaveLocalData && ((id, values) => onSaveLocalData(id, values, detail.localEtag))}
            onReload={onReloadLocalData && (() => onReloadLocalData(detail.id))}
            onRestore={onRestoreLocalData}
          />

          <section className="detail__section" aria-labelledby="detail-stats">
            <h3 id="detail-stats" className="detail__heading">Stats</h3>
            {detail.stats.length > 0 ? (
              <ul className="stats">
                {detail.stats.map((stat) => (
                  <li key={stat.name} className="stats__row">
                    <span className="stats__label">{statLabel(stat.name)}</span>
                    <span className="stats__value">{stat.value}</span>
                    <span className="stats__bar" aria-hidden="true">
                      <span
                        className="stats__fill"
                        style={{ width: `${Math.min(100, (stat.value / MAX_BASE_STAT) * 100)}%` }}
                      />
                    </span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="detail__empty">No stats available.</p>
            )}
          </section>

          <section className="detail__section" aria-labelledby="detail-description">
            <h3 id="detail-description" className="detail__heading">Description</h3>
            {detail.descriptionAvailable !== false ? (
              <p className="detail__description">{detail.description ?? 'No description available.'}</p>
            ) : (
              <p className="detail__warning" role="status">
                {detail.retryAfterSeconds
                  ? `Could not load the description. ${rateLimitMessage(detail.retryAfterSeconds)}`
                  : 'Could not load the description. Try again later.'}
              </p>
            )}
          </section>

          <section className="detail__section" aria-labelledby="detail-evolution">
            <h3 id="detail-evolution" className="detail__heading">Evolution line</h3>
            {detail.evolutionAvailable ? (
              detail.lineage && (
                <ul className="evolution">
                  <EvolutionTree
                    node={detail.lineage}
                    current={detail.species ?? detail.name}
                    onSelect={onSelectEvolution}
                  />
                </ul>
              )
            ) : (
              <p className="detail__warning" role="status">
                {detail.retryAfterSeconds
                  ? `Could not load the evolution line. ${rateLimitMessage(detail.retryAfterSeconds)}`
                  : 'Could not load the evolution line. Try again later.'}
              </p>
            )}
          </section>
        </>
      )}
    </Modal>
  )
}
