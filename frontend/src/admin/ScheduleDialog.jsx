import { useEffect, useRef, useState } from 'react';
import { shortDate } from '../lib/format.js';

/** Today as the server writes dates - local parts, because toISOString is UTC. */
function todayISO() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

function inDays(days) {
  const date = new Date();
  date.setDate(date.getDate() + days);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

const QUICK = [
  { days: 3, label: 'In 3 days' },
  { days: 7, label: 'In a week' },
  { days: 14, label: 'In 2 weeks' },
  { days: 30, label: 'In a month' },
];

/**
 * Committing to a date, and saying a word about it.
 *
 * The same dialog does the first commitment and every revision after it,
 * because they ask for identical things - the difference is only what the
 * report already has, which is why the wording changes and the fields do not.
 *
 * The date is required by the server and pre-filled here, deliberately. A
 * moderator clearing a queue of twenty reports should be able to accept a
 * sensible default on most of them and only think about the ones that need
 * thinking about.
 */
export default function ScheduleDialog({ issue, mode, onSave, onCancel }) {
  const first = mode === 'acknowledge';

  const [etaDate, setEtaDate] = useState(() => issue.etaDate ?? inDays(7));
  const [note, setNote] = useState(() => issue.workNote ?? '');
  const [error, setError] = useState(null);
  const [fields, setFields] = useState({});
  const [busy, setBusy] = useState(false);

  const dateInput = useRef(null);

  useEffect(() => {
    dateInput.current?.focus();
  }, []);

  useEffect(() => {
    function onKeyDown(event) {
      if (event.key === 'Escape' && !busy) onCancel();
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [busy, onCancel]);

  async function submit(event) {
    event.preventDefault();

    // Checked here as well as on the server, so the common mistake is caught
    // without a round trip. The server's copy is the one that matters.
    if (!etaDate) {
      setError('Give a date the work should be done by.');
      setFields({ etaDate: 'Pick a date' });
      return;
    }
    if (etaDate < todayISO()) {
      setError('That date has already passed. Pick today or later.');
      setFields({ etaDate: 'Today or later' });
      return;
    }

    setBusy(true);
    setError(null);
    setFields({});

    try {
      await onSave({ etaDate, note });
      // The dashboard closes this on success; nothing to do here.
    } catch (problem) {
      setError(problem.message);
      setFields(problem.fields ?? {});
      setBusy(false);
    }
  }

  return (
    <div
      className="overlay"
      role="presentation"
      onClick={(event) => {
        if (event.target === event.currentTarget && !busy) onCancel();
      }}
    >
      <div className="dialog" role="dialog" aria-modal="true" aria-labelledby="sched-head">
        <h2 className="dialog-head" id="sched-head">
          {first ? 'Pick this up' : 'Change the date'}
        </h2>

        <p className="dialog-why">
          {first
            ? 'The date and the note go straight onto the public report, so everyone backing it can see what was promised.'
            : `Currently promised by ${shortDate(issue.etaDate)}. Residents see the new date and note immediately.`}
        </p>

        <p className="adm-dialog-subject">{issue.title}</p>

        <form onSubmit={submit} noValidate>
          {error && (
            <p className="notice" role="alert">
              {error}
            </p>
          )}

          <div className="field">
            <label htmlFor="sched-date">Done by</label>
            <input
              id="sched-date"
              ref={dateInput}
              className="input"
              type="date"
              value={etaDate}
              min={todayISO()}
              aria-invalid={Boolean(fields.etaDate)}
              onChange={(event) => setEtaDate(event.target.value)}
            />
            {fields.etaDate && <span className="field-error">{fields.etaDate}</span>}

            <div className="adm-quick">
              {QUICK.map((option) => {
                const value = inDays(option.days);
                return (
                  <button
                    key={option.days}
                    type="button"
                    className={`adm-quick-btn${etaDate === value ? ' is-on' : ''}`}
                    aria-pressed={etaDate === value}
                    onClick={() => setEtaDate(value)}
                  >
                    {option.label}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="field">
            <label htmlFor="sched-note">Note to residents</label>
            <span className="hint" id="sched-note-hint">
              Optional, and public. What is actually happening — the contractor, the part on
              order, the street it has to be dug up from.
              {!first && issue.workNote ? ' Clearing this removes the note.' : ''}
            </span>
            <textarea
              id="sched-note"
              className="textarea"
              value={note}
              maxLength={500}
              rows={3}
              aria-describedby="sched-note-hint"
              aria-invalid={Boolean(fields.note)}
              onChange={(event) => setNote(event.target.value)}
            />
            {fields.note && <span className="field-error">{fields.note}</span>}
          </div>

          <div className="btn-row">
            <button type="submit" className="btn btn-primary" disabled={busy}>
              {busy ? 'Saving' : first ? 'Pick up and publish the date' : 'Publish the new date'}
            </button>
            <button type="button" className="btn btn-quiet" onClick={onCancel} disabled={busy}>
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
