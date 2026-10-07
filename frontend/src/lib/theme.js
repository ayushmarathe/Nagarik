const THEME_KEY = 'nagarik.theme';

/**
 * Light or dark, remembered between visits.
 *
 * The choice is always written onto <html> as data-theme rather than left to a
 * prefers-color-scheme media query. That way there is exactly one source of
 * truth for which theme is showing: the stylesheet only ever has to match an
 * explicit attribute, and "system" is resolved here, once, instead of being
 * duplicated in CSS and in JS.
 */
export function readTheme() {
  try {
    const stored = window.localStorage.getItem(THEME_KEY);
    if (stored === 'light' || stored === 'dark') return stored;
  } catch {
    // Storage blocked; fall through to the system preference.
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  // Tells the browser to paint form controls, scrollbars and the address bar in
  // the matching scheme, so a dark page does not get a white scrollbar.
  document.documentElement.style.colorScheme = theme;
  try {
    window.localStorage.setItem(THEME_KEY, theme);
  } catch {
    // Not remembered; the theme still applies to this page.
  }
}

/** Applies the stored theme before React mounts, so there is no white flash. */
export function initTheme() {
  const theme = readTheme();
  applyTheme(theme);
  return theme;
}
