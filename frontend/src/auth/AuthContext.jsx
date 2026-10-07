import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { api, getToken, setSessionLostHandler, setToken } from '../api.js';

/**
 * Who is signed in, and the machinery for asking people to sign in.
 *
 * The prompt lives here rather than in the component that needed it for one
 * reason: a click that opens a sign-in box should still do what it was going to
 * do once the person signs in. Backing a report on the first tap should back
 * it, not just sign you in and leave you to tap again. Callers pass a
 * continuation, and the provider runs it after the token arrives.
 */
const AuthContext = createContext(null);

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}

export function AuthProvider({ children, onNotice }) {
  const [user, setUser] = useState(null);

  // True until a stored token has been checked. The interface waits on this
  // before showing anything that depends on being signed in, otherwise a
  // returning visitor sees the signed-out header for a moment and then it
  // changes under them.
  const [restoring, setRestoring] = useState(() => Boolean(getToken()));

  const [prompt, setPrompt] = useState(null);

  // The action waiting on a successful sign-in. In a ref, not state, because it
  // may be a function that closing the prompt has to hand along, and storing a
  // function in state makes every setState a reducer-shaped problem.
  const pending = useRef(null);

  useEffect(() => {
    setSessionLostHandler(() => {
      setUser(null);
      onNotice('Your session expired. Sign in again to carry on.', 'bad');
    });
    return () => setSessionLostHandler(null);
  }, [onNotice]);

  // A stored token is checked against the server once, on load. Anything cached
  // in storage could be a week old, or belong to an account that has since been
  // deleted, so the server's answer is the only one worth trusting.
  useEffect(() => {
    if (!getToken()) {
      setRestoring(false);
      return undefined;
    }

    let active = true;
    api
      .me()
      .then((me) => {
        if (active) setUser(me);
      })
      .catch(() => {
        // api.js has already dropped the token if the server refused it.
        if (active) setUser(null);
      })
      .finally(() => {
        if (active) setRestoring(false);
      });

    return () => {
      active = false;
    };
  }, []);

  const settle = useCallback(
    (session) => {
      setToken(session.token);
      setUser(session.user);
      setPrompt(null);

      const next = pending.current;
      pending.current = null;
      // Hand the fresh person to the waiting action: the closure that opened
      // the prompt was built when there was no user, so it cannot read one.
      if (next) next(session.user);
    },
    []
  );

  const signIn = useCallback(async (values) => settle(await api.login(values)), [settle]);

  const signUp = useCallback(async (values) => settle(await api.register(values)), [settle]);

  const signOut = useCallback(() => {
    setToken(null);
    setUser(null);
    setPrompt(null);
    pending.current = null;
  }, []);

  const rename = useCallback(async (displayName) => {
    const updated = await api.rename(displayName);
    setUser(updated);
    return updated;
  }, []);

  /**
   * requireUser is handed down and called from inside long-lived closures, so it
   * reads the current person through a ref rather than depending on `user`. A
   * callback that changed identity on every sign-in would re-run every effect
   * holding it, and the feed would reload for no reason.
   */
  const userRef = useRef(null);
  userRef.current = user;

  /**
   * Returns the signed-in person, or opens the sign-in box and returns null.
   *
   * Pass `then` and it runs once the person is signed in, so the click that
   * opened the prompt still happens. Without it the first tap on any action is
   * swallowed and the button looks broken.
   */
  const requireUser = useCallback((reason, then) => {
    if (userRef.current) return userRef.current;
    pending.current = then ?? null;
    setPrompt({ reason: reason ?? null });
    return null;
  }, []);

  const value = useMemo(
    () => ({
      user,
      restoring,
      signedIn: Boolean(user),
      canModerate: Boolean(user?.canModerate),
      prompt,
      signIn,
      signUp,
      signOut,
      rename,
      requireUser,
      dismissPrompt: () => {
        pending.current = null;
        setPrompt(null);
      },
    }),
    [user, restoring, prompt, signIn, signUp, signOut, rename, requireUser]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
