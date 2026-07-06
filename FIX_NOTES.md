# Fix notes

## 2026-07-03

- Playback positions are now retained separately per track when pausing or
  switching tracks; only an explicit Stop resets the active track to zero.
- Added Google `AuthorizationClient` integration with the read-only YouTube
  OAuth scope.
- Added paginated YouTube Data API requests for the current user's private and
  public playlists and their playlist items.
- Added a multi-select dialog that can import one, several or all returned
  playlists.
- The app keeps OAuth access-token references in memory only and discards them
  after import, cancellation or an API failure.
- CSV imports now merge with the existing SQLite library instead of replacing it.
- Track IDs normalize Unicode, letter case and repeated whitespace to prevent
  duplicate imports.
- Google Takeout YouTube playlist CSV files with a `Video ID` column are
  supported.
- Added a confirmed destructive action that clears the track database and all
  MP3 files stored under the app's internal `music` directory.
- Stale queued downloads are invalidated during a library clear; output from an
  already-running stale download is deleted when that operation returns.
- Updated the README to match the current UI, supported CSV columns, storage
  behavior and actual build configuration.
- Verified with `./gradlew :app:compileDebugKotlin`.

## 2026-07-01

- Removed `org.jetbrains.kotlin.android` from the root and app Gradle build files.
- AGP 9.x has built-in Kotlin support, so applying the Kotlin Android plugin separately causes the reported build failure.
- Kept `org.jetbrains.kotlin.plugin.compose`, because Compose compiler plugin is still needed for Compose/Kotlin 2.x projects.
- Kept package/namespace as `com.example.shazamytdl`.
