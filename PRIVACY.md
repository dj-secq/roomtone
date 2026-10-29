# Roomtone privacy policy

Effective 30 October 2026. Applies to the Roomtone app for Android and iOS, including a build you make from this repository.

## The short version

Roomtone collects no personal data. It has no account, no analytics, no advertising, no crash reporting, and no server. The Android app does not request the internet permission, so it cannot send anything anywhere. Everything the app knows lives on your device and leaves it only if you copy it out yourself. Like every music player, it hands the track that is playing (and, on Android, the queue) to the system's media controls. The operating system, and apps allowed to control media or read notifications, can read that on their own terms.

## What the app reads on your device

- **Your music files and their tags.** Title, artist, album, genre, year, language, lyrics, and cover art, used to build the library, the audio index, the recommendations, the map, search, and the duplicate finder. The audio is analysed on the device with bundled models. No audio or metadata is uploaded.
- **Listening history.** Which tracks you played, skipped, or finished, and when. How each one started. The playlist, album, artist, genre, or folder playback started from. This is used for statistics, For You, and the recommendations. It is recorded only while *Save listening history* is on in Settings, where it can also be cleared.
- **Your playlists, favourites, hidden tracks, duplicate decisions, and settings.**

All of this is stored in the app's private storage on the device. Nothing is shared with the developer or with any third party.

## Permissions and why they are asked for

| Permission | Why |
| --- | --- |
| Read audio files (Android), Media Library (iOS) | To find and play your music |
| Write audio files (Android 9 and older) | To save edited tags or delete a file when you ask. Older Android has no per-file consent. |
| Notifications | Playback controls in the notification shade |
| Foreground service | To keep playing with the screen off and to index files in the background |
| Modify audio settings | Equaliser and volume normalisation |
| Keep the device awake | Uninterrupted playback |

## Things that happen only when you ask

- **Tag editing** rewrites the tags inside your own audio files, on the device.
- **Delete file** removes a file through the system's own confirmation dialog.
- **Local backup** writes a backup file to the location you choose, and restores from it. The file carries your playlists, favourites, listening history, recent searches, hidden tracks, SMART exclusions, the duplicate finder's decisions, and settings.

## Third parties

There are none in the app. It embeds no analytics, advertising, or tracking SDKs. Roomtone itself sends nothing.

## Deleting your data

Uninstalling the app removes everything it stored. Inside the app, Settings offers separate controls to clear the listening history and the audio analysis.

## Children

Roomtone is a general-purpose music player and is not directed at children.

## Changes

Changes to this policy are published in this file with a new effective date.

## Contact

Questions go to the issue tracker:
<https://github.com/dj-secq/roomtone/issues>.
