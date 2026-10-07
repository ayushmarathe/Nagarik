import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext.jsx';
import { useToast } from './Toasts.jsx';

/**
 * Changing the name your reports are signed with.
 *
 * Separate from the sign-in dialog because it is a different act with a
 * different consequence: this one rewrites the byline on everything you have
 * already posted, since reports store a reference to the account rather than a
 * copy of the name.
 */
export default function AccountDialog({ open, onClose }) {
  const { user, rename } = useAuth();
  const pushToast = useToast();

  const [name, setName] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (open) {
      setName(user?.displayName ?? '');
      setError(null);
    }
  }, [open, user]);

  useEffect(() => {
    if (!open) return undefined;
    function onKeyDown(event) {
      if (event.key === 'Escape' && !busy) onClose();
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, busy, onClose]);

  if (!open) return null;

  async function handleSubmit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const updated = await rename(name.trim());
      pushToast(`You are now posting as ${updated.displayName}.`, 'good');
      onClose();
    } catch (problem) {
      setError(problem.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div
      className="overlay"
      role="presentation"
      onClick={(event) => {
        if (event.target === event.currentTarget && !busy) onClose();
      }}
    >
      <div className="dialog" role="dialog" aria-modal="true" aria-labelledby="account-head">
        <h2 className="dialog-head" id="account-head">
          Change my name
        </h2>
        <p className="dialog-why">
          This is the name on your reports and replies. Changing it updates them all.
        </p>

        <form onSubmit={handleSubmit} noValidate>
          {error && (
            <p className="notice" role="alert">
              {error}
            </p>
          )}

          <div className="field">
            <label htmlFor="account-name">Your name</label>
            <input
              id="account-name"
              className="input"
              value={name}
              maxLength={60}
              autoFocus
              onChange={(event) => setName(event.target.value)}
            />
          </div>

          <div className="btn-row">
            <button type="submit" className="btn btn-primary" disabled={busy}>
              {busy ? 'Saving' : 'Save'}
            </button>
            <button type="button" className="btn btn-quiet" onClick={onClose} disabled={busy}>
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
