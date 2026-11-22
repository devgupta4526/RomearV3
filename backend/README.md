# ROMEAR backend

The FastAPI service stores the latest private snapshot for each Android installation in PostgreSQL. Existing onboarded installs are queued on their first launch with a configured backend. New installs start uploading after onboarding. The app saves changes locally first and retries transient failures while its process is running. It resumes pending uploads when reopened. Reset queues an empty snapshot so older requests cannot restore cleared data. Device photos and videos stay on the phone.

An installation credential is randomly generated on the device and stored in private app preferences. The API stores its SHA-256 digest. Each endpoint gets a separate credential. The Android app currently uploads snapshots for that installation. Clearing app storage or changing phones creates a new installation. The existing sign-in screen is a demo, and catalogue discovery, conversations, bookings and group actions still have prototype semantics.

## Free hosting

ROMEAR runs on [Render Free](https://dashboard.render.com/web/srv-dc18lcnf3r2c73c07amg) with [Supabase Free PostgreSQL](https://supabase.com/dashboard/project/zkzfcscpcwnjusxmqhrx). The Render service connects to Supabase's session pooler over verified TLS. The connection string is stored in Render and the Supabase CA is mounted at `/etc/secrets/supabase-ca.crt`. The application role owns the private `romear` schema.

Build against this deployment from the Android project directory.

```powershell
.\gradlew.bat :app:assembleDebug -PromearBackendUrl=https://romear-api-cgrm.onrender.com -PromearDeviceAuth=true
```

### Fresh deployment with Neon

1. Create a Neon Free project. Copy its direct PostgreSQL connection string with SSL enabled. Store it only in the hosting environment. [Neon Free plan](https://neon.com/docs/introduction/plans)
2. Connect a Git repository containing this workspace to Render and create a Blueprint from the root `render.yaml`. Set `DATABASE_URL` to the Neon connection string and keep the Web Service on the Free plan. The service initializes its version-one table on startup. [Render Free services](https://render.com/docs/free)
3. Request `/health` on the Render origin and confirm that it returns `{"status":"ok"}`. From the Android project directory, build with the public origin.

   ```powershell
   .\gradlew.bat :app:assembleDebug -PromearBackendUrl=https://YOUR-SERVICE.onrender.com
   ```

The API accepts up to 128 KiB per installation snapshot and defaults to 100 installations. Configure `MAX_INSTALLATIONS` in Render if needed. Render's free service can take about a minute to wake after idle time. If a deployment needs to be rolled back, use the previous Render deploy and retain the PostgreSQL table. Keep future schema migrations compatible with the previous API version.

## Email accounts

Set `RESEND_API_KEY` and `AUTH_EMAIL_FROM` in Render using your verified sender domain. The Blueprint generates `AUTH_CODE_SECRET`. Build the Android app with `-PromearEmailAuth=true` and the HTTPS backend URL to activate email sign-in in the existing screens. The default build retains the demo sign-in. Email codes are single-use, expire after ten minutes and permit five verification attempts. Account sessions expire after 30 days. New devices can retrieve a saved profile by signing in again.

Live mail delivery requires a [Resend verified sender domain](https://resend.com/docs/dashboard/domains/introduction). SMS and paid features require separate provider setup. The private installation snapshot remains separate from the account profile.

## Device accounts and community

The free build uses device credentials. **Find my city** creates or restores the account linked to this installation. Clearing Android app storage loses that credential. **Explore the demo** uses the local prototype. To turn off device registration and sign-in, set `ENABLE_DEVICE_ACCOUNTS=false` on Render.

Signed-in accounts can publish their profiles during setup, discover and follow other members, send direct text messages, join the existing ROMEAR clubs, and access member-only club chat. Members can create pools and request spots. Organizers decide in Member approvals, which checks each pool's capacity in a database transaction. My Plans saves and cancels private plans for the existing event and café catalogue. A saved plan does not reserve inventory or charge a payment.

Signed-in members can share text posts and JPEG photos, comment, react, and keep private bookmarks. Account-backed passport stamps and business drafts stay private. Settings supports authenticated account deletion. See the [backend checklist](../docs/BACKEND_CHECKLIST.md) for remaining flows and verification.

Verified club management, event and offer publishing, cloud video, production email delivery, SMS, and payments remain outside this release.

Run the API integration tests with `python -m unittest -v test_community` from this directory. Set `ROMEAR_TEST_DATABASE_URL` to disposable local PostgreSQL before running them.

## Local verification

With a disposable PostgreSQL database and `DATABASE_URL` set in your shell, install `requirements.txt` and run `python -m uvicorn main:app --host 127.0.0.1 --port 8765` from this directory. Connect a phone over ADB, run `adb reverse tcp:8765 tcp:8765`, then build Android debug with `-PromearBackendUrl=http://127.0.0.1:8765`. Loopback builds install as `app.romear.mock.local` with separate app data. Builds with an empty backend URL run locally. Release networking requires HTTPS.

`GET /v1/state` reads an installation snapshot and `PUT /v1/state` accepts a positive revision and JSON state object. Both routes use `Authorization: Bearer <64 hex characters>`. An equal or older revision returns the latest stored revision and preserves its data.
