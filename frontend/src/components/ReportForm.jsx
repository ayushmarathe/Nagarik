import { useState } from 'react';

const EMPTY = { title: '', description: '', category: '', area: '' };

export default function ReportForm({ categories, onSubmit, onCancel }) {
  const [values, setValues] = useState(EMPTY);
  const [fields, setFields] = useState({});
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  function update(key, value) {
    setValues((current) => ({ ...current, [key]: value }));
  }

  /**
   * Point a field at its own hint and error text. Without this the error is
   * visible but unannounced - a screen reader lands on an input marked invalid
   * and gets no explanation of why.
   */
  function describedBy(key, hasHint) {
    const ids = [];
    if (hasHint) ids.push(key + '-hint');
    if (fields[key]) ids.push(key + '-error');
    return ids.length > 0 ? ids.join(' ') : undefined;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setFields({});

    try {
      await onSubmit(values);
      setValues(EMPTY);
    } catch (problem) {
      setError(problem.message);
      setFields(problem.fields ?? {});
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="panel">
      <h2 className="panel-head">Report a problem</h2>

      <form onSubmit={handleSubmit} noValidate>
        {error && (
          <p className="notice" role="alert">
            {error}
          </p>
        )}

        <div className="field">
          <label htmlFor="title">What is wrong?</label>
          <span className="hint" id="title-hint">
            One line your neighbours would recognise.
          </span>
          <input
            id="title"
            className="input"
            value={values.title}
            maxLength={140}
            aria-invalid={Boolean(fields.title)}
            aria-describedby={describedBy('title', true)}
            onChange={(event) => update('title', event.target.value)}
          />
          {fields.title && (
            <span className="field-error" id="title-error">
              {fields.title}
            </span>
          )}
        </div>

        <div className="field">
          <label htmlFor="area">Where is it?</label>
          <span className="hint" id="area-hint">
            A landmark, lane or ward works better than an address.
          </span>
          <input
            id="area"
            className="input"
            value={values.area}
            maxLength={120}
            aria-invalid={Boolean(fields.area)}
            aria-describedby={describedBy('area', true)}
            onChange={(event) => update('area', event.target.value)}
          />
          {fields.area && (
            <span className="field-error" id="area-error">
              {fields.area}
            </span>
          )}
        </div>

        <div className="field">
          <label htmlFor="category">What kind of problem?</label>
          <select
            id="category"
            className="select"
            value={values.category}
            aria-invalid={Boolean(fields.category)}
            aria-describedby={describedBy('category', false)}
            onChange={(event) => update('category', event.target.value)}
          >
            <option value="">Choose one</option>
            {categories.map((category) => (
              <option key={category.value} value={category.value}>
                {category.label}
              </option>
            ))}
          </select>
          {fields.category && (
            <span className="field-error" id="category-error">
              {fields.category}
            </span>
          )}
        </div>

        <div className="field">
          <label htmlFor="description">What should someone know about it?</label>
          <span className="hint" id="description-hint">
            How long it has been like this, who it affects, what has already been tried.
          </span>
          <textarea
            id="description"
            className="textarea"
            value={values.description}
            maxLength={4000}
            aria-invalid={Boolean(fields.description)}
            aria-describedby={describedBy('description', true)}
            onChange={(event) => update('description', event.target.value)}
          />
          {fields.description && (
            <span className="field-error" id="description-error">
              {fields.description}
            </span>
          )}
        </div>

        <div className="btn-row">
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Posting' : 'Post report'}
          </button>
          <button type="button" className="btn btn-quiet" onClick={onCancel} disabled={submitting}>
            Cancel
          </button>
        </div>
      </form>
    </section>
  );
}
