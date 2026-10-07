import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../auth/AuthContext.jsx';
import { useToast } from './Toasts.jsx';

/**
 * The top strip: who this board belongs to, how it is standing, and the two
 * things a person does here - find their name, or raise a problem.
 */
export default function Header({ stats, theme, onToggleTheme, onReport, onOpenAccount }) {
  const { user, restoring, signedIn, canModerate, signOut, requireUser } = useAuth();
  const pushToast = useToast();

  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);

  // Clicking anywhere else closes the menu. Without this it stays open behind
  // whatever you do next, which reads as the page being stuck.
  useEffect(() => {
    if (!menuOpen) return undefined;
    function onPointerDown(event) {
      if (menuRef.current && !menuRef.current.contains(event.target)) setMenuOpen(false);
    }
    function onKeyDown(event) {
      if (event.key === 'Escape') setMenuOpen(false);
    }
    window.addEventListener('pointerdown', onPointerDown);
    window.addEventListener('keydown', onKeyDown);
    return () => {
      window.removeEventListener('pointerdown', onPointerDown);
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [menuOpen]);

  function handleSignOut() {
    setMenuOpen(false);
    signOut();
    pushToast('Signed out on this device.');
  }

  return (
    <header className="topbar">
      <div className="topbar-inner">
        <h1 className="brand">
          <span className="brand-mark" aria-hidden="true">
            <svg width="18" height="18" viewBox="0 0 18 18" focusable="false">
              <path
                d="M9 1.5c3.3 0 6 2.6 6 5.8 0 4-6 9.2-6 9.2S3 11.3 3 7.3c0-3.2 2.7-5.8 6-5.8Z"
                fill="currentColor"
              />
              <circle cx="9" cy="7.2" r="2.2" fill="var(--brand-mark-dot)" />
            </svg>
          </span>
          <span className="brand-name">Nagarik</span>
        </h1>

        <p className="standing-line">
          {stats ? (
            <>
              <strong>{stats.open}</strong> open
              <span className="standing-sep" aria-hidden="true" />
              <strong>{stats.resolved}</strong> fixed
              <span className="standing-sep" aria-hidden="true" />
              <strong>{stats.residents}</strong> residents
            </>
          ) : (
            'Problems your neighbours have reported'
          )}
        </p>

        <div className="topbar-actions">
          <button
            type="button"
            className="icon-btn"
            onClick={onToggleTheme}
            aria-label={theme === 'dark' ? 'Switch to the light theme' : 'Switch to the dark theme'}
          >
            {theme === 'dark' ? (
              <svg width="17" height="17" viewBox="0 0 17 17" aria-hidden="true" focusable="false">
                <circle cx="8.5" cy="8.5" r="3.4" fill="currentColor" />
                <g stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
                  <path d="M8.5 .8v2M8.5 14.2v2M.8 8.5h2M14.2 8.5h2M3 3l1.4 1.4M12.6 12.6 14 14M14 3l-1.4 1.4M4.4 12.6 3 14" />
                </g>
              </svg>
            ) : (
              <svg width="17" height="17" viewBox="0 0 17 17" aria-hidden="true" focusable="false">
                <path
                  d="M14.5 10.4A6.4 6.4 0 0 1 6.6 2.5a6.5 6.5 0 1 0 7.9 7.9Z"
                  fill="currentColor"
                />
              </svg>
            )}
          </button>

          {restoring ? (
            // A stored token is still being checked. Showing "sign in" here and
            // then swapping it for a name is the flicker this avoids.
            <span className="who-skeleton" aria-hidden="true" />
          ) : signedIn ? (
            <div className="menu" ref={menuRef}>
              <button
                type="button"
                className="who-btn"
                aria-expanded={menuOpen}
                aria-haspopup="menu"
                onClick={() => setMenuOpen((open) => !open)}
              >
                <span className="avatar" aria-hidden="true">
                  {user.displayName.slice(0, 1).toUpperCase()}
                </span>
                <span className="who-name">{user.displayName}</span>
                {canModerate && <span className="role-chip">Moderator</span>}
              </button>

              {menuOpen && (
                <div className="menu-sheet" role="menu">
                  <p className="menu-head">
                    <span className="menu-head-name">{user.displayName}</span>
                    <span className="menu-head-mail">{user.email}</span>
                  </p>
                  {/*
                    A real navigation rather than a button, because the dashboard
                    is a separate document. Middle-clicking it should open a tab,
                    which is what a moderator working through a queue will want.
                  */}
                  {canModerate && (
                    <a className="menu-item" role="menuitem" href="/admin.html">
                      Moderator dashboard
                    </a>
                  )}
                  <button
                    type="button"
                    className="menu-item"
                    role="menuitem"
                    onClick={() => {
                      setMenuOpen(false);
                      onOpenAccount();
                    }}
                  >
                    Change my name
                  </button>
                  <button type="button" className="menu-item" role="menuitem" onClick={handleSignOut}>
                    Sign out
                  </button>
                </div>
              )}
            </div>
          ) : (
            <button
              type="button"
              className="btn btn-quiet"
              onClick={() => requireUser('Sign in to post, back and reply.')}
            >
              Sign in
            </button>
          )}

          <button type="button" className="btn btn-primary" onClick={onReport}>
            Report a problem
          </button>
        </div>
      </div>
    </header>
  );
}
