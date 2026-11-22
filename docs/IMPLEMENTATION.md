# Implementation notes

## Navigation

Six persistent main destinations mirror the detailed references: Home, Explore, Clubs, Events, Chat and Profile. My Plans, Pools, Saved, Passport, Premium and Business Tools are reachable from the city menu. Detail pages keep the parent destination highlighted. Android Back follows the in-app route stack. Form drafts and route history use SavedStateHandle; local content survives app restarts.

## State and interactions

The repository serializes a versioned local state object to private SharedPreferences. It contains profile fields, interests, likes, saves, memberships, follows, pool requests, reservations, message drafts, posts, pools, city stamps, sample approval decisions, offers and event drafts. No claim of server delivery or verification is made.

The repository is intentionally small and offline. Before implementing shared data, replace its persistence boundary with repositories backed by a durable database and authenticated APIs. SharedPreferences is not a large-content database. Nothing here stores payment credentials or passwords.

Reservation forms validate dates and group sizes and prevent duplicate active local reservations for the same item/date/time. Cancelling preserves the reservation in the cancelled plans list. Removing a local event explicitly removes linked demo reservations. Business approvals operate on three clearly labelled fictional requests. Local points derive from existing local state instead of displaying invented verified experience totals.

Search operates on the bundled catalogue, with category filters and token matching. Followed feed content derives from the chosen people and clubs. Chat saves messages locally and provides Android text sharing without claiming that the recipient received a message. No automatic incoming replies are generated.

## Design and accessibility

- Native XML ConstraintLayout shell and reusable XML photo cards.
- Material buttons, text fields, chips and switches; standard Android date picker.
- Minimum 48dp action targets and text descriptions on interactive icons.
- Scrollable content, wrap-content text and system font scaling.
- Activity handles status/navigation/cutout and keyboard insets.
- Six bottom destinations collapse while the keyboard is visible.
- Decorative photos are separate bundled assets, not complete screen screenshots.
- Passport text reflects the editable profile. The illustrated map remains labelled sample artwork.

## Integration work still required for a live service

Supply a backend contract before adding authentication or network behaviour. Live club verification and organiser/venue roles must be enforced by the server; event publication cannot rely on UI gates. Real reservations require inventory locking and server-issued identifiers. Real purchases require store billing configuration and server verification. Messaging requires authenticated conversations, delivery state and moderation. Public profiles require privacy enforcement, reporting and deletion workflows. These are not represented as completed integrations.

## Validation status

The user subsequently requested UI-focused testing and a dummy app matching the references. Testing is now authorised. Debug builds, lint and physical-device instrumentation have been run. See UI_QA.md for scope and evidence. All ADB commands after the device restriction target OnePlus 5T serial 4de841e9 explicitly.
