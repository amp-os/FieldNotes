# Google Cloud & OAuth Setup — Human Instructions

These steps require your Google account and are done once before Claude Code needs the Drive credential. Estimated time: 15 minutes.

---

## Step 1: Create a Google Cloud project

1. Go to [https://console.cloud.google.com](https://console.cloud.google.com)
2. Click the project selector dropdown at the top → **New Project**
3. Name: `FieldNotes` → **Create**
4. Wait ~30 seconds for the project to be created, then select it

---

## Step 2: Enable the Google Drive API

1. In the left menu: **APIs & Services → Library**
2. Search for `Google Drive API`
3. Click **Google Drive API** → **Enable**

---

## Step 3: Configure the OAuth consent screen

1. **APIs & Services → OAuth consent screen**
2. User Type: **External** → **Create**
3. Fill in:
   - App name: `FieldNotes`
   - User support email: your Gmail address
   - Developer contact: your Gmail address
4. Click **Save and Continue** through the Scopes screen (leave scopes empty — the app adds them at runtime)
5. On the **Test users** screen: click **+ Add Users** and add your own Gmail address
6. Click **Save and Continue** → **Back to Dashboard**

> You're in "Testing" mode which allows up to 100 test users. This is fine for personal use and development.

---

## Step 4: Create an OAuth 2.0 Client ID

1. **APIs & Services → Credentials** → **+ Create Credentials → OAuth client ID**
2. Application type: **Android**
3. Name: `FieldNotes Android`
4. Package name: `com.fieldnotes.app`
5. SHA-1 certificate fingerprint: you need to generate this from your signing key

### Getting your debug SHA-1 fingerprint

Run this in your terminal (macOS/Linux):
```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android
```

On Windows:
```cmd
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

Copy the **SHA1** value (looks like: `AA:BB:CC:DD:...`). Paste it into the SHA-1 field in Google Cloud.

6. Click **Create**
7. You'll see a dialog with your **Client ID** — it ends in `.apps.googleusercontent.com`

---

## Step 5: Add the Client ID to your project

Open (or create) `local.properties` in the root of the FieldNotes project:

```properties
sdk.dir=/Users/yourname/Library/Android/sdk
drive.client.id=YOUR_CLIENT_ID_HERE.apps.googleusercontent.com
```

Replace `YOUR_CLIENT_ID_HERE` with the Client ID from Step 4.

> **Important:** `local.properties` is listed in `.gitignore` by default. Do not commit this file — it contains your credential.

---

## Step 6: Verify the setup

After Claude Code has built the app and installed it on your Pixel 8:

1. Open FieldNotes
2. Go to **Settings → Connect Google Drive**
3. A browser tab should open asking you to sign in with your Google account
4. Sign in with the Gmail you added as a test user in Step 3
5. You'll see a warning "This app hasn't been verified by Google" — this is expected for apps in testing mode. Click **Continue**
6. Grant the Drive permission
7. You should be redirected back to the app and see your email shown in Settings

---

## Troubleshooting

**"The redirect URI in the request did not match"**
→ Ensure the package name in Google Cloud exactly matches `com.fieldnotes.app`

**"Error 400: redirect_uri_mismatch"**
→ The SHA-1 fingerprint doesn't match. Re-run the keytool command and update Google Cloud.

**"Access blocked: This app's request is invalid"**
→ You haven't added yourself as a test user (Step 3). Go back to the OAuth consent screen → Test users → add your email.

**Drive sync says "Not authenticated" after signing in**
→ Check `local.properties` has the correct `drive.client.id` value and rebuild the app.

---

## For production release (future)

When you want to publish FieldNotes publicly:
- Generate a production signing keystore (not the debug one)
- Add the production SHA-1 fingerprint as a second Android OAuth client
- Submit the OAuth consent screen for Google verification (takes 1-2 weeks)
- The "unverified app" warning will disappear for users

---

## What data Google can access

FieldNotes uses the `drive.file` scope, which means:
- Google can only see files **created by FieldNotes**
- It cannot access any of your other Drive files, photos, or documents
- Revoking access in your [Google Account settings](https://myaccount.google.com/permissions) removes all access instantly
