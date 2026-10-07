import { useEffect, useState } from 'react';

/**
 * Returns `value` only once it has stopped changing for `delay` milliseconds.
 *
 * Typing into the search box would otherwise fire a request per keystroke, and
 * those requests would land in whatever order the network felt like - which is
 * how a results list ends up showing matches for a half-typed word.
 */
export function useDebounced(value, delay = 300) {
  const [settled, setSettled] = useState(value);

  useEffect(() => {
    const timer = window.setTimeout(() => setSettled(value), delay);
    return () => window.clearTimeout(timer);
  }, [value, delay]);

  return settled;
}
