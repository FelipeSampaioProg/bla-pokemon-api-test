import './Pagination.css'

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100]

export default function Pagination({ page, size, totalPages, totalElements, onPageChange, onSizeChange }) {
  const isFirstPage = page <= 0
  const isLastPage = totalPages === 0 || page >= totalPages - 1

  return (
    <div className="pagination">
      <div className="pagination__size">
        <label htmlFor="page-size">Per page:</label>
        <select
          id="page-size"
          value={size}
          onChange={(event) => onSizeChange(Number(event.target.value))}
        >
          {PAGE_SIZE_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </div>

      <div className="pagination__controls">
        <button
          type="button"
          className="pagination__button"
          onClick={() => onPageChange(page - 1)}
          disabled={isFirstPage}
        >
          ← Previous
        </button>
        <span className="pagination__status">
          Page {totalPages === 0 ? 0 : page + 1} of {totalPages} · {totalElements} Pokémon
        </span>
        <button
          type="button"
          className="pagination__button"
          onClick={() => onPageChange(page + 1)}
          disabled={isLastPage}
        >
          Next →
        </button>
      </div>
    </div>
  )
}
