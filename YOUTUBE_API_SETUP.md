# YouTube Data API and OAuth setup

The YouTube playlist import code is complete, but Google must recognize each
signed Android application before it will issue OAuth access tokens. This is an
external Google Cloud configuration; no client secret should be committed to
this repository.

## 1. Create or select a Google Cloud project

Open the [Google Cloud Console](https://console.cloud.google.com/) and create or
select the project that will own this application.

In **APIs & Services → Library**, enable **YouTube Data API v3**.

## 2. Configure Google Auth Platform

Open **Google Auth Platform** and complete:

- **Branding**: application name and developer contact details,
- **Audience**: normally External, unless use is limited to one Workspace
  organization,
- **Data Access**: add
  `https://www.googleapis.com/auth/youtube.readonly`.

During development, keep the application in testing mode and add every Google
account that will test the import under **Test users**. For production use with
external users, Google may require OAuth app verification, a privacy policy and
verified application domains.

The requested scope is read-only. The application cannot create, modify or
delete anything in the user's YouTube account.

## 3. Obtain the Android signing SHA-1

For a local debug build run:

```bash
./gradlew signingReport
```

Find the `debug` variant and copy its SHA-1 certificate fingerprint.

Release builds and Google Play App Signing use different certificates. Create a
separate OAuth client for every signing certificate that will be used:

- local debug keystore SHA-1,
- release keystore SHA-1, if self-managed,
- Google Play App Signing SHA-1 for Play-distributed builds.

## 4. Create the Android OAuth client

In **Google Auth Platform → Clients**:

1. choose **Create client**,
2. select **Android**,
3. use package name `com.example.shazamytdl`,
4. enter the matching SHA-1 fingerprint,
5. save the client.

The Android `AuthorizationClient` identifies the client from the installed
package and signing certificate. Do not add a client secret to Kotlin code,
`local.properties` or the repository. A `google-services.json` file is not
required by the current implementation.

## 5. Run and verify

Build and install the same variant whose SHA-1 was registered:

```bash
./gradlew :app:assembleDebug
```

Then:

1. open the application,
2. tap **Poveži YouTube in izberi sezname**,
3. select a Google account and grant read-only YouTube access,
4. select one, multiple or all playlists,
5. tap **Uvozi izbrane**.

The app calls:

- `playlists.list(part=snippet,contentDetails,status, mine=true)`,
- `playlistItems.list(part=snippet,contentDetails, playlistId=...)`.

Both methods are paginated with 50 results per page. Imported entries use the
video title, video owner's channel title and a direct YouTube watch URL.

## Troubleshooting

### Developer error / status code 10

The installed package name or signing SHA-1 does not match an Android OAuth
client. Run `./gradlew signingReport`, verify the installed build variant and
check the client in Google Cloud.

### Access blocked or user not authorized

If the OAuth application is in testing mode, add the Google account under Test
users. Also verify that the `youtube.readonly` scope is present on the Data
Access page.

### YouTube API has not been used or is disabled

Enable **YouTube Data API v3** in the same Cloud project that owns the Android
OAuth client. API enablement can take several minutes to propagate.

### Empty playlist list

`playlists.list(mine=true)` returns playlists owned by the authorized
YouTube channel. Confirm that the selected Google account has a YouTube channel
and owns at least one playlist.

### HTTP 401

Access tokens are intentionally short-lived. Close the selection dialog and tap
the YouTube connect button again to obtain a fresh token.

## Official references

- [Authorize access to Google user data on Android](https://developer.android.com/identity/authorization)
- [YouTube Data API overview](https://developers.google.com/youtube/v3/getting-started)
- [Playlists implementation guide](https://developers.google.com/youtube/v3/guides/implementation/playlists)
- [playlistItems.list reference](https://developers.google.com/youtube/v3/docs/playlistItems/list)
