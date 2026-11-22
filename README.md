# ROMEAR

A native Android city social app built in this workspace from the supplied design references. Kotlin, XML Views, Material Components, ConstraintLayout, ViewBinding, ViewModel and StateFlow. Cream surfaces, forest green navigation, terracotta accents and photographic cards follow the reference direction.

## Open the project

Open `android` in Android Studio. The project specifies JDK 17, Android SDK 36, Android Gradle Plugin 8.13.2, Kotlin 2.2.10 and Gradle 8.13. Configure your local SDK using Android Studio or an untracked `android/local.properties` file. The Gradle wrapper downloads its distribution when first invoked.

This is now a UI-first mock app. Debug builds and Android lint pass. Device tests run exclusively on the OnePlus 5T (ONEPLUS A5010, Android 10). See artifacts and docs/UI_QA.md for the recorded scope; visual similarity is not a claim of pixel equality.

## Included flows

- Welcome, local profile setup, city and interest selection.
- Feed tabs, stories, reference photo posts, comments, likes, bookmarks and sharing through Android.
- Search, club discovery, club profiles and membership, people discovery, public profiles, following and shared interests.
- Events, cafés and spaces, local reservation forms, saved reservation details, cancellation and plans.
- Community pools, local join requests and creating pools.
- Conversation lists, persistent local message drafts and local activity notifications.
- Editable profile, drawn personal passport cover, self-reported city stamps, illustrated map, badges, progress and activity.
- Club and venue workspaces, editable event drafts, sample approval queue, offer drafts and promotion briefs.
- Premium membership preview and local privacy/reset settings.

## Backend and local data

The app persists its state on the device and can upload a private snapshot for each installation to the FastAPI backend. Follow the [backend deployment guide](backend/README.md) to deploy the API and PostgreSQL on free tiers and configure the Android build.

The Rome discovery catalogue remains bundled, user-selected media stays on the device, and the existing OTP, messaging, reservations, verification and payment flows retain their demo behavior. Selecting another city updates the profile.

Nearby uses online OpenStreetMap tiles and asks for approximate device location when you select **Use my location**. The app displays the location in the current session and shows sample catalogue activity at fixed Rome coordinates.

## Source organisation

`MainActivity` owns the XML shell, insets and six destination navigation. `CityViewModel` retains routes, back history and form drafts through `SavedStateHandle`. `CityRepository` owns the observable persisted local state. Screen files are grouped by feature; `CityUi` inflates reusable XML photo cards and creates native form controls. `PassportCoverView` draws a passport for the current user instead of displaying a whole screenshot.

Chat and story use dedicated `screen_chat.xml` and `screen_story.xml` layouts, hosted independently of the main scrolling shell. `MicroScreens.kt` handles message bubbles, the fixed composer, story progress/touch controls and local replies. The screenshot catalogue covers 50 routes, with six additional micro-screen and keyboard captures in the UI gallery.

`scripts/prepare_assets.py` extracts photographs only from image paths explicitly supplied in this chat and writes native vector icons. Assets are already included; this script is optional and requires Pillow and the original reference files. It does not build or test the app.

See `docs/IMPLEMENTATION.md` for interaction details and service integration boundaries.
