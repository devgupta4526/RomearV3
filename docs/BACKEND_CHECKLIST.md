# ROMEAR backend checklist

Track each flow separately from its verification. Android build and lint are required for this pass. Device and emulator checks are deferred until a test device is available.

| Area | Status | Completion check |
| --- | --- | --- |
| Render and PostgreSQL | Live | Render reports the intended commit live. Verify HTTPS and persistence on the hosted database. |
| Device sign-in and session renewal | Live | Create an account, reopen it on the same installation, and reject expired or revoked sessions. |
| Public profile, discovery and following | Live | Only opted-in profiles appear. Follow and unfollow persist under the authenticated account. |
| Account recovery and cross-device access | Pending | Add a verified login method that can safely link an existing device account, then test recovery on another device. |
| Email OTP | Provider blocked | Configure a verified sender, deliver a real code, and verify expiry, replay prevention, and registration limits. |
| SMS OTP | Provider blocked | Configure delivery and test real messages plus abuse controls. |
| Direct text and club messages | Live | Verify message isolation, idempotent sending, membership gates and revocation when leaving. |
| Chat attachments, receipts and push notifications | Pending | Add attachment access checks, durable delivery status, device registration and notification tests. |
| Club membership | Live | Joining and leaving update membership and active chat access. |
| Club ownership, moderation and verification | Pending | Add organizer role assignment, application review, audit history and member moderation. |
| Pools and join approvals | Live | Verify organizer-only decisions, withdrawal, and concurrent approvals at capacity. |
| Personal plans | Live | Verify account isolation, duplicate prevention, save and cancel. |
| Live inventory, admission and bookings | Outside current scope | The approved free flow saves personal plans. Real reservations require authorized venue or event inventory. |
| Community feed and member-visible posts | Implemented | Publish, list, filter by author or following, and delete under owner authorization. Android UI flow awaits device QA. |
| Comments, likes and saved posts | Implemented | Verify the account making the action, idempotent retries, shared counts and private bookmarks. Android UI flow awaits device QA. |
| Shared photos | Implemented | Validate JPEG dimensions and byte limits, store the image, and enforce account access on read. Android photo rendering awaits device QA. |
| Video, reels and stories | Pending | Add bounded media uploads, video playback, story expiry and lifecycle cleanup. |
| Events, venues and places catalogue | Bundled | Move catalogue ownership to the server when real listings are supplied. Verify search, filters and detail routes against server data. |
| Private event and offer drafts | Implemented | Create, edit and remove under the owner account. Test cross-account isolation. |
| Business publishing | Pending | Add organizer ownership, publication approval, authorized offers and cancellation. |
| Promotions and premium payments | Provider blocked | Configure products and providers, verify server-side entitlements and webhook replay protections. |
| Passport stamps | Implemented | Add and remove private, self-reported stamps, then reload the account library. |
| Badges, timeline and recap | Partly local | Add event-derived activity, achievement rules and private recap reads. |
| Account deletion | Implemented | Confirm in Settings, delete the authenticated account and cascading records, revoke sessions, and clear this installation’s local data. |
| Reporting and blocking | Pending | Provide reports, block enforcement across discovery and messaging, and a moderation queue. |
| Local and hosted regression tests | Required | Exercise multiple accounts, authorization, retries, concurrent requests and cleanup of temporary hosted fixtures. |
| Android device verification | Deferred | Run instrumented and visual checks when a test device or emulator is available. |
