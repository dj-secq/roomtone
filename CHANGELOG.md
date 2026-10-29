# Changelog

Roomtone's own record. The files in `docs/release-*.md` and `fastlane/metadata/android/en-US/changelogs/` are the LatentJam history this tree inherits. They are not Roomtone releases. Issue numbers in those notes point at the LatentJam tracker.

## 0.9.2

First Roomtone release. Android `versionCode` 16. The player is the LatentJam 0.9.2 tree, with Roomtone's name, install id, and radio. This GitHub release is the Android APK set. There is no iPhone build in it.

- The visible name is Roomtone. The Android application id and the iOS bundle id are `io.github.djsecq.roomtone`.
- About leads with the radio and SMART. The privacy policy, the store listing, the website, and the AltStore source describe this repository: <https://github.com/dj-secq/roomtone>.
- A radio can start from a seed and keep choosing the songs that follow. For You can open one of those starts, and **New radio starts** is translated.
- A FLAC file that has no vendor string records `Roomtone` the first time this app edits it.
- The APKs are signed with the debug key, so a sideload can update an earlier Roomtone debug install of the same package.
