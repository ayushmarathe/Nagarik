import { useEffect, useRef } from 'react';
import { AuthProvider, useAuth } from '../auth/AuthContext.jsx';
import { ToastProvider, useToast } from '../components/Toasts.jsx';
import AuthDialog from '../components/AuthDialog.jsx';
import Dashboard from './Dashboard.jsx';

/**
 * The ward dashboard, as its own document.
 *
 * Everything above Dashboard is the door: a session, and a check that the
 * person behind it is allowed in. The check here is a courtesy rather than a
 * defence - every /api/admin call is refused by the server without a moderator
 * role, so what this decides is whether somebody sees a worklist or an
 * explanation, not whether they can change anything.
 */
export default function AdminApp() {
  return (
    <ToastProvider>
      <Session />
    </ToastProvider>
  );
}

function Session() {
  const pushToast = useToast();
  return (
    <AuthProvider onNotice={pushToast}>
      <Door />
    </AuthProvider>
  );
}

function Door() {
  const { restoring, signedIn, canModerate, user, requireUser } = useAuth();

  // The sign-in box opens by itself here, unlike on the board. A resident lands
  // on the board to read; nobody opens the dashboard except to work, so making
  // them click "sign in" first is a step with no decision in it.
  //
  // Once only: reopening a box the person just dismissed is a trap, not a
  // prompt.
  const asked = useRef(false);
  useEffect(() => {
    if (restoring || signedIn || asked.current) return;
    asked.current = true;
    requireUser('This is the ward dashboard. Sign in with a moderator account.');
  }, [restoring, signedIn, requireUser]);

  if (restoring) {
    return (
      <>
        <Frame>
          <p className="adm-gate-line">Checking your session</p>
        </Frame>
        <AuthDialog />
      </>
    );
  }

  if (!signedIn) {
    return (
      <>
        <Frame>
          <h1 className="adm-gate-head">Ward dashboard</h1>
          <p className="adm-gate-line">
            Reports are picked up, scheduled and handed back for checking from here.
          </p>
          <div className="btn-row btn-row-center">
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => requireUser('Sign in with a moderator account.')}
            >
              Sign in
            </button>
            <a className="btn btn-quiet" href="/">
              Back to the board
            </a>
          </div>
        </Frame>
        <AuthDialog />
      </>
    );
  }

  if (!canModerate) {
    return (
      <Frame>
        <h1 className="adm-gate-head">This one is not for you</h1>
        <p className="adm-gate-line">
          You are signed in as {user.displayName}, which is a resident account. The dashboard is
          for the people who schedule the work — everything you can do lives on the board.
        </p>
        <div className="btn-row btn-row-center">
          <a className="btn btn-primary" href="/">
            Back to the board
          </a>
        </div>
      </Frame>
    );
  }

  return (
    <>
      <Dashboard />
      {/* Still mounted: a token can expire mid-shift, and the prompt has to
          have somewhere to render when it does. */}
      <AuthDialog />
    </>
  );
}

/** The centred card every one of the closed doors above is written on. */
function Frame({ children }) {
  return (
    <main className="adm-gate">
      <div className="adm-gate-card card">{children}</div>
    </main>
  );
}
