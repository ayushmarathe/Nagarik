import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext.jsx';

const EMPTY = { displayName: '', email: '', password: '' };

/**
 * Sign in, or make an account. One dialog with two modes rather than two
 * screens, because the fields overlap and the switch between them is a single
 * thought ("I do not have one yet").
 */
export default function AuthDialog() {
  const { prompt, signIn, signUp, dismissPrompt } = useAuth();

  const [mode, setMode] = useState('signin');
  const [values, setValues] = useState(EMPTY);
  const [fields, setFields] = useState({});
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [revealed, setRevealed] = useState(false);

  const open = Boolean(prompt);

  // A dialog you cannot dismiss with Escape feels like a trap.
  useEffect(() => {
    if (!open) return undefined;
    function onKeyDown(event) {
      if (event.key === 'Escape' && !busy) dismissPrompt();
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, busy, dismissPrompt]);

  // Reset each time it opens, so a failed sign-in's error does not reappear
  // when the box is opened again from somewhere else.
  useEffect(() => {
    if (open) {
      setValues(EMPTY);
      setFields({});
      setError(null);
      setRevealed(false);
    }
  }, [open]);

  if (!open) return null;

  function update(key, value) {
    setValues((current) => ({ ...current, [key]: value }));
  }

  function switchMode() {
    setMode((current) => (current === 'signin' ? 'signup' : 'signin'));
    setFields({});
    setError(null);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});

    try {
      if (mode === 'signin') {
        await signIn({ email: values.email, password: values.password });
      } else {
        await signUp(values);
      }
      // The provider closes the dialog and runs whatever action was waiting.
    } catch (problem) {
      setError(problem.message);
      setFields(problem.fields ?? {});
    } finally {
      setBusy(false);
    }
  }

  const signingIn = mode === 'signin';

  return (
    <div
      className="overlay"
      role="presentation"
      onClick={(event) => {
        if (event.target === event.currentTarget && !busy) dismissPrompt();
      }}
    >
      <div className="dialog dialog-auth" role="dialog" aria-modal="true" aria-labelledby="auth-head">
        <h2 className="dialog-head" id="auth-head">
          {signingIn ? 'Sign in' : 'Create an account'}
        </h2>

        <p className="dialog-why">
          {prompt?.reason ??
            'Reports and replies are signed, so people know who is raising what.'}
        </p>

        <form onSubmit={handleSubmit} noValidate>
          {error && (
            <p className="notice" role="alert">
              {error}
            </p>
          )}

          {!signingIn && (
            <div className="field">
              <label htmlFor="auth-name">Your name</label>
              <span className="hint" id="auth-name-hint">
                Shown on everything you post. Your email is never shown.
              </span>
              <input
                id="auth-name"
                className="input"
                value={values.displayName}
                maxLength={60}
                autoComplete="name"
                aria-invalid={Boolean(fields.displayName)}
                aria-describedby="auth-name-hint"
                onChange={(event) => update('displayName', event.target.value)}
              />
              {fields.displayName && <span className="field-error">{fields.displayName}</span>}
            </div>
          )}

          <div className="field">
            <label htmlFor="auth-email">Email</label>
            <input
              id="auth-email"
              className="input"
              type="email"
              value={values.email}
              maxLength={254}
              autoComplete="email"
              // Focused on open, because it is the first thing to type either way.
              autoFocus
              aria-invalid={Boolean(fields.email)}
              onChange={(event) => update('email', event.target.value)}
            />
            {fields.email && <span className="field-error">{fields.email}</span>}
          </div>

          <div className="field">
            <label htmlFor="auth-password">Password</label>
            <div className="input-group">
              <input
                id="auth-password"
                className="input"
                type={revealed ? 'text' : 'password'}
                value={values.password}
                maxLength={72}
                autoComplete={signingIn ? 'current-password' : 'new-password'}
                aria-invalid={Boolean(fields.password)}
                aria-describedby={signingIn ? undefined : 'auth-password-hint'}
                onChange={(event) => update('password', event.target.value)}
              />
              <button
                type="button"
                className="input-affix"
                onClick={() => setRevealed((shown) => !shown)}
                aria-label={revealed ? 'Hide the password' : 'Show the password'}
              >
                {revealed ? 'Hide' : 'Show'}
              </button>
            </div>
            {!signingIn && (
              <span className="hint" id="auth-password-hint">
                At least 8 characters.
              </span>
            )}
            {fields.password && <span className="field-error">{fields.password}</span>}
          </div>

          <div className="btn-row">
            <button type="submit" className="btn btn-primary" disabled={busy}>
              {busy ? 'One moment' : signingIn ? 'Sign in' : 'Create account'}
            </button>
            <button type="button" className="btn btn-quiet" onClick={dismissPrompt} disabled={busy}>
              Not now
            </button>
          </div>
        </form>

        <p className="dialog-switch">
          {signingIn ? 'No account yet?' : 'Already have an account?'}{' '}
          <button type="button" className="linkish" onClick={switchMode} disabled={busy}>
            {signingIn ? 'Create one' : 'Sign in'}
          </button>
        </p>
      </div>
    </div>
  );
}
