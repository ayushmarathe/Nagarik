import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';

/**
 * Short-lived messages for things that happen away from where the person is
 * looking - a failed vote, a status change, a report posted.
 *
 * A context rather than props, because the components that need to raise one
 * (a dialog, a row, the session handler) sit at different depths and none of
 * them is a natural owner of the list.
 */
const ToastContext = createContext(() => {});

export function useToast() {
  return useContext(ToastContext);
}

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const seq = useRef(0);
  const timers = useRef([]);

  useEffect(() => () => timers.current.forEach((timer) => window.clearTimeout(timer)), []);

  const push = useCallback((text, tone = 'plain') => {
    const id = ++seq.current;
    setToasts((current) => [...current, { id, text, tone }]);
    const timer = window.setTimeout(() => {
      setToasts((current) => current.filter((toast) => toast.id !== id));
      timers.current = timers.current.filter((held) => held !== timer);
    }, 5200);
    timers.current.push(timer);
  }, []);

  const dismiss = useCallback((id) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const value = useMemo(() => push, [push]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      {toasts.length > 0 && (
        <div className="toasts" role="status" aria-live="polite">
          {toasts.map((toast) => (
            <div className="toast" key={toast.id} data-tone={toast.tone ?? 'plain'}>
              <p className="toast-text">{toast.text}</p>
              <button
                type="button"
                className="toast-close"
                onClick={() => dismiss(toast.id)}
                aria-label="Dismiss this message"
              >
                Close
              </button>
            </div>
          ))}
        </div>
      )}
    </ToastContext.Provider>
  );
}
