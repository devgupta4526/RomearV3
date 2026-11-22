# UI mock and device QA

The app is a dummy prototype for comparing the supplied design references and walking through their flows. Sample identities, verification codes, achievements, counts and reservations are fictional. There are no live purchases, messages, reservations or account verification operations.

## Verified

- Debug application and instrumentation APK builds passed.
- Android lint passed: 0 errors, 52 warnings. Warnings remain; this is not a zero-warning release audit.
- Four instrumentation tests passed on the OnePlus 5T, ONEPLUS A5010, Android 10/API 29, serial 4de841e9.
- The screen catalogue opened all 50 routes, including OTP and landmark details, and captured actual device screenshots.
- Button-driven onboarding passed through welcome, sign-in, mock OTP, city, interests, profile setup and ready state to home.
- Button-driven venue booking produced a mock reservation.
- Save/like/message state, back navigation and activity recreation assertions passed.
- Initial testing found a switch measurement crash; switch labels and text visibility were fixed and the suite subsequently passed.
- Initial compilation and API/resource lint errors were fixed.

## Visual changes

### Visual hierarchy refresh

Pool, club, event, venue and landmark pages use large photographic heroes with legible title scrims. Pool pages include icon-led time/capacity/level tiles, overlapping participant avatars and a capacity indicator. People profiles use a cover image with an overlapping portrait. The shared listing card has larger photography, a category badge and an overlaid bookmark. Shared navigation cards now use host avatars or tinted icon tiles rather than uniformly repeated text boxes.

Long detail paragraphs show a concise preview with an explicit expand/collapse affordance; the full content remains available and expansion state survives screen rerenders. Important join, follow and booking actions live in a fixed bottom action dock on the relevant detail pages. The detail test checks that the pool dock is visible and the activity plan can be expanded and collapsed without losing its text. Shared surfaces, typography, spacing and corner treatment were updated across the app.

The table-tennis thumbnail from the supplied board was replaced by generated 1672×941 mock photography, stored as a local WebP asset. A generated 941×1672 café photograph replaces the soft story crop and provides the chat photo. These do not depict claimed real venues. Original generated PNGs are retained under artifacts/ for reproducible asset preparation.

### Information depth across detail screens

Profiles, pools, clubs, events, venues, landmarks, posts, checkout and reservation details now provide structured context rather than only a title and short description. Depending on the entity, this includes organiser/founder links, founding or membership dates, purpose, activities, schedule, meeting point, capacity, equipment, eligibility, costs, facilities, accessibility questions, cancellation and arrival expectations. These are fictional catalogue fixtures, not verified facts or policies for real businesses.

Created pools now require an activity description and offer a start-time selector. Both fields persist, older saved pools remain readable, and created pools open their own detail pages instead of immediately offering deletion. Creator actions differ from visitor join actions. Catalogue cards show location context. Event host conversations recognise all club types.

The detail-content test checks expected sections on six detail screens, captures each at two scroll positions, and checks a created pool’s description/time after recreation. An initial device run was interrupted when the phone entered doze; test activities now keep the screen awake and the full four-test run subsequently passed. See artifacts/detail-final-tests.txt and artifacts/expanded-details/.

### Chat, stories and shared components

Chat and story now have dedicated native XML layouts instead of reusing the generic scrolling page. Chat has a compact avatar/presence header, video/menu actions, incoming/outgoing sample bubbles, time/read indicators, photo messages, a group conversation, and an anchored composer. Inbox rows use previews, timestamps and unread badges. Story has full-bleed photography, dark scrims, segmented six-second progress, hold-to-pause, previous/next tap zones, swipe navigation/dismissal, overlaid identity/location/caption, likes and reply controls. Leaving a story cancels its timer; backgrounding pauses it. Story replies save a local message and open the matching conversation.

The focused device test covers direct/group chat, sending, the keyboard-visible composer and header position, next-story taps, and story replies. Six additional screenshots document these states. Shared form fields now have cream outline surfaces. Switches have visible thumb/track colours. Embedded text and duplicated profile imagery were removed from photographic crops. A source encoding check found and corrected a non-UTF-8 café label. Explore's extra search card was removed in the final visual cleanup; search remains reachable through the Posts category.

Reference photos, typography hierarchy, titles, category colours, card height, header spacing, profile counters, passport artwork, city stamps and the badge grid were brought closer to the references. Status/navigation contrast and outlined form fields were corrected from real phone screenshots. OTP is a dummy screen using code 472916.

The APK uses package app.romear.mock, separate from other ROMEAR installations. After the user's device restriction, every ADB call explicitly targeted the OnePlus 5T. No device tests were run on an emulator.

## Evidence

- artifacts/ROMEAR-mock.apk
- artifacts/all-screens-final.txt
- artifacts/verified-build-tests.txt
- artifacts/premium-final-tests.txt — final visual-refresh build, four tests passed
- artifacts/instrumentation-results.txt
- artifacts/fifty-screens/ — 50 route screenshots
- artifacts/final-micro/ — six focused chat/story/keyboard screenshots
- artifacts/ui-gallery.html — local screenshot gallery
- artifacts/micro-updates.jpg — chat/story contact sheet
- artifacts/review-sheet-1.jpg through review-sheet-5.jpg — complete catalogue contact sheets
- artifacts/premium-final-details/ — final detail-page captures
- artifacts/premium-final-routes/ — final 50-route captures
- artifacts/premium-final-micro/ — final chat/story/keyboard captures
- artifacts/pool-before-after.jpg — actual-device comparison
- android/app/build/reports/lint-results-debug.html

## Limits

Passing a route smoke test is not exhaustive testing of every tap, dialog or scroll position. Other devices, landscape, large text and accessibility services have not received equivalent validation. Pixel-identical matching across all supplied designs is not established. Several secondary screens retain simplified layouts, particularly the monthly recap, timeline, promotion and business dashboards. Empty states and displayed counts depend on local mock activity. The reference phone frames and operating-system chrome are not part of the app UI.
