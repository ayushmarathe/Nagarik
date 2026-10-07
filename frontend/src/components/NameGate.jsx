/*
  Removed. Nothing imports this file.

  This was the old way in: before there were accounts, the first action that
  needed a name (backing a report, replying, reporting something) opened a
  sheet asking for one, and that name was then sent as a header on every
  write. Anybody could type any name, including somebody else's.

  Real sign-in replaced it. The dialog that asks for an email and a password is
  components/AuthDialog.jsx, and the continuation trick this component
  introduced - remembering the action you were part-way through and finishing
  it once you have identified yourself - now lives in auth/AuthContext.jsx as
  requireUser.

  The file is empty rather than gone only because it could not be deleted from
  here. It is safe to delete.
*/

export {};
