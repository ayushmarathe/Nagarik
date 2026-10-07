export default function SearchBox({ value, onChange, busy }) {
  return (
    <div className="search">
      <label className="search-label" htmlFor="feed-search">
        Search reports
      </label>

      <div className="search-field">
        <svg
          className="search-icon"
          width="15"
          height="15"
          viewBox="0 0 15 15"
          aria-hidden="true"
          focusable="false"
        >
          <circle cx="6.2" cy="6.2" r="4.6" fill="none" stroke="currentColor" strokeWidth="1.6" />
          <path d="M9.7 9.7 13.6 13.6" stroke="currentColor" strokeWidth="1.6" fill="none" />
        </svg>

        <input
          id="feed-search"
          className="input search-input"
          type="search"
          value={value}
          placeholder="A street, a kind of problem, a word from the report"
          autoComplete="off"
          onChange={(event) => onChange(event.target.value)}
        />

        {busy && <span className="search-busy" aria-hidden="true" />}

        {value !== '' && (
          <button
            type="button"
            className="search-clear"
            onClick={() => onChange('')}
            aria-label="Clear the search"
          >
            Clear
          </button>
        )}
      </div>
    </div>
  );
}
