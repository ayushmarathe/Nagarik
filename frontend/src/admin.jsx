import React from 'react';
import { createRoot } from 'react-dom/client';
import AdminApp from './admin/AdminApp.jsx';

/*
  Two stylesheets on purpose.

  styles.css carries the design system - the colour tokens, the reset, and the
  primitives both documents share (buttons, fields, dialogs, toasts). Copying
  those into a second file would guarantee the two drift apart, and the point of
  a token layer is that there is one of it.

  admin.css carries only what the dashboard adds: its own layout, its own
  density, its own table. It is imported second so its overrides win.
*/
import './styles.css';
import './admin.css';

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <AdminApp />
  </React.StrictMode>
);
