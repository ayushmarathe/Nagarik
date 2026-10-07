import { categoryColor } from '../lib/categories.js';

const SORTS = [
  { value: 'top', label: 'Most backed' },
  { value: 'new', label: 'Newest' },
  { value: 'discussed', label: 'Discussed' },
];

/**
 * The category list is a legend and a filter at the same time. It shows the
 * colour each kind of problem carries through the feed, how many are standing
 * in it, and selects it when pressed - so the key to the feed is also the
 * control for it, rather than two things saying the same names.
 */
export default function FilterBar({
  categories,
  statuses,
  areas,
  stats,
  filters,
  onChange,
  onClear,
}) {
  const counts = new Map((stats?.byCategory ?? []).map((row) => [row.value, row.total]));
  const anyFilter =
    Boolean(filters.category) ||
    Boolean(filters.status) ||
    Boolean(filters.area) ||
    Boolean(filters.search);

  return (
    <div className="filters card">
      <section className="filter-block">
        <h2 className="filter-head">Order</h2>
        <div className="segmented" role="group" aria-label="Order the reports">
          {SORTS.map((sort) => (
            <button
              key={sort.value}
              type="button"
              className="segment"
              aria-pressed={filters.sort === sort.value}
              onClick={() => onChange({ ...filters, sort: sort.value })}
            >
              {sort.label}
            </button>
          ))}
        </div>
      </section>

      <section className="filter-block">
        <h2 className="filter-head">Kind of problem</h2>
        <ul className="legend">
          <li>
            <button
              type="button"
              className="legend-row"
              aria-pressed={!filters.category}
              onClick={() => onChange({ ...filters, category: null })}
            >
              <span className="legend-swatch legend-swatch-all" aria-hidden="true" />
              <span className="legend-label">Everything</span>
              <span className="legend-count">{stats ? stats.total : ''}</span>
            </button>
          </li>

          {categories.map((category) => (
            <li key={category.value}>
              <button
                type="button"
                className="legend-row"
                aria-pressed={filters.category === category.value}
                style={{ '--stripe': categoryColor(category.value) }}
                onClick={() =>
                  onChange({
                    ...filters,
                    category: filters.category === category.value ? null : category.value,
                  })
                }
              >
                <span className="legend-swatch" aria-hidden="true" />
                <span className="legend-label">{category.label}</span>
                <span className="legend-count">{counts.get(category.value) ?? 0}</span>
              </button>
            </li>
          ))}
        </ul>
      </section>

      <section className="filter-block">
        <h2 className="filter-head">Narrow it down</h2>

        <label className="field-inline">
          <span className="field-inline-label">Status</span>
          <select
            className="select"
            value={filters.status ?? ''}
            onChange={(event) => onChange({ ...filters, status: event.target.value || null })}
          >
            <option value="">Any status</option>
            {statuses.map((status) => (
              <option key={status.value} value={status.value}>
                {status.label}
              </option>
            ))}
          </select>
        </label>

        <label className="field-inline">
          <span className="field-inline-label">Locality</span>
          <select
            className="select"
            value={filters.area ?? ''}
            onChange={(event) => onChange({ ...filters, area: event.target.value || null })}
          >
            <option value="">Anywhere</option>
            {areas.map((area) => (
              <option key={area} value={area}>
                {area}
              </option>
            ))}
          </select>
        </label>
      </section>

      {anyFilter && (
        <button type="button" className="btn btn-quiet btn-wide" onClick={onClear}>
          Clear filters
        </button>
      )}
    </div>
  );
}
