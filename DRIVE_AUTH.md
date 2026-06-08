# FieldNotes — Google Drive Auth: current state & long-term recommendation

## Current implementation (as of this writing)
Drive OAuth uses **AppAuth** (`net.openid:appauth`) — Custom Tabs + PKCE, with Google's
**reverse-client-ID custom URI scheme** as the redirect (`DriveAuthManager`). Tokens are persisted as
an AppAuth `AuthState`, which also handles refresh.

Two requirements make it work (both documented in `CLAUDE.md` / `BUILD_NOTES.md`):
1. The app's XML theme descends from `Theme.AppCompat` (AppAuth's redirect activity is an AppCompat activity).
2. The Android OAuth client in Google Cloud has **"Custom URI scheme" enabled** in *Advanced Settings* —
   otherwise Google blocks the request with *"Access blocked: …request is invalid."*

## Recommendation: treat the custom URI scheme as a stopgap, not the long-term solution

**It works and is acceptable for personal / single-user use today**, but it should not be the basis of a
public release.

### Why it's not a good long-term foundation
- **It's an explicit escape hatch.** Google now disables custom URI schemes by default for new Android
  OAuth clients and frames the Advanced-Settings toggle as a temporary measure "if the recommended
  alternative doesn't work for your needs." There is no published hard cutoff, but the direction is
  one-way and the toggle could be removed in future.
- **Security direction.** The restriction exists because another app can register the same custom scheme
  and intercept the redirect (app impersonation). PKCE closes code interception, so it's *safe enough*
  for personal use, but it runs against the platform's direction.
- **Invisible deploy footgun.** The toggle is a per-client console setting, not in the repo. It must be
  re-enabled for every OAuth client — notably when registering a **release** SHA-1 for publishing — and
  is easy to forget (it cost real debugging time during development).

### Recommended long-term path
Migrate to **Google Identity Services: Credential Manager + `AuthorizationClient`** (requesting the
`drive.file` scope). It uses the existing **Android** client type natively — no custom scheme, no
redirect URI, no Advanced-Settings toggle, and no AppCompat-theme dependency — and is the path Google
actively supports.

**Trade-off (the reason we didn't start here):** on a **serverless** app, `AuthorizationClient` yields
only short-lived (~1 hour) **access tokens**. A durable on-device **refresh token** requires a backend +
a Web OAuth client to exchange a server auth code (`requestOfflineAccess` → `serverAuthCode`). In
practice you re-call `authorize()` (silent once the scope is granted, incl. from the sync worker) to get
a fresh access token. If durable, no-re-consent background sync matters, that's the point where a small
token-exchange backend earns its keep.

> Note: the legacy Google Sign-In API (`GoogleSignInClient`) is deprecated; the **Authorization API**
> (`AuthorizationClient`) is the current, supported piece — don't confuse the two.

### Decision
- **Now / personal use:** keep AppAuth + the custom URI scheme toggle (documented tech debt).
- **Before a public release**, or to stop depending on a phased-out mechanism: migrate to Credential
  Manager + `AuthorizationClient`, adding a minimal backend only if durable refresh tokens are required.

## References
- [Improving user safety in OAuth flows (custom URI scheme restrictions)](https://developers.googleblog.com/improving-user-safety-in-oauth-flows-through-new-oauth-custom-uri-scheme-restrictions/)
- [Authorize access to Google user data on Android (AuthorizationClient)](https://developer.android.com/identity/authorization)
- [OAuth 2.0 for Mobile & Desktop Apps](https://developers.google.com/identity/protocols/oauth2/native-app)
