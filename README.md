<p align="center">
  <img src="branding/logo.svg" width="120" alt="Roomtone logo">
</p>

<h1 align="center">Roomtone</h1>

<p align="center">
  <b>A radio for the music you already own.</b><br>
  Start a song and SMART keeps choosing what plays next. Android and iOS. No account, no streaming, no network.
</p>

<p align="center">
  <img alt="Platforms" src="https://img.shields.io/badge/platform-Android%207%2B%20%7C%20iOS%2015.1%2B-1450A8?style=flat-square">
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-Apache%202.0-8E24AA.svg?style=flat-square"></a>
</p>

<p align="center">
  <a href="PRIVACY.md">Privacy</a> ·
  <a href="CHANGELOG.md">Changelog</a> ·
  <a href="https://github.com/dj-secq/roomtone/issues">Issues</a>
</p>

---

Roomtone is a radio for the music already on your phone. Start a song and **SMART** keeps choosing what plays next, on the device. **For You** can open a radio from a seed and also brings back records you own and forgot. Edit tags, follow synced lyrics, and see the library as a map. Nothing about your listening leaves the phone.

Roomtone is based on [LatentJam](https://github.com/Nikita-sud/latentjam) 0.9.2, which is Apache-2.0. This tree keeps that license, the `NOTICE`, and the copyright lines already in the source. Kotlin packages stay `io.github.nikitasud.latentjam` so a later merge from that project can still apply. The install id is `io.github.djsecq.roomtone`, so this build can sit beside LatentJam.

Android 0.9.2 is on [GitHub Releases](https://github.com/dj-secq/roomtone/releases/latest). A normal phone wants the arm64 APK. There is no iPhone build in that release, no F-Droid listing, and no App Store build.

<p align="center">
  <img src="docs/media/walkthrough.gif" width="270" alt="Opening the player and following synchronised lyrics">
</p>

<p align="center"><a href="docs/media/player-demo.mp4">Player and lyrics · 25 seconds</a> · <a href="docs/media/walkthrough.mp4">A walk through the app · 46 seconds</a></p>

<p align="center">
  <img src="docs/media/player.png" width="170" alt="Now playing in SMART mode">&nbsp;&nbsp;
  <img src="docs/media/for-you.png" width="170" alt="For You page">&nbsp;&nbsp;
  <img src="docs/media/lyrics.png" width="170" alt="Synchronised lyrics with tap-to-seek">&nbsp;&nbsp;
  <img src="docs/media/playlists.png" width="170" alt="Favourite, recent and personal playlists">
</p>

<p align="center"><sub>Screens from the Android player, with a fictional demo library. More of them are in <a href="docs/media/README.md">docs/media</a>.</sub></p>

## Install

> **Beta.** Screens and settings can still change. Tag editing writes to your real files, so back up the music you care about before a big edit. SMART gets better once the first analysis has finished. Found a bug? [Open an issue](https://github.com/dj-secq/roomtone/issues).

Install the arm64 APK from [GitHub Releases](https://github.com/dj-secq/roomtone/releases/latest), or build it with the commands in [Building](#building). Android needs **7.0+**. iOS needs **15.1+** and a signing identity. The published APKs are signed with the debug key. There is no F-Droid listing and no App Store build.

**Where music comes from on Android.** Roomtone plays what Android's media index knows about. There is no manual "add folder" yet. If songs are missing, use Settings → Library → Refresh library. A hidden `.nomedia` file tells Android to skip a folder. Settings → Library → Music sources picks which folders appear.

**On iPhone.** Music comes from files you import through the Files app and from songs in your Music library. Protected Apple Music downloads play and can be searched, but they cannot be analysed by sound, so SMART, the map, and the mixes work best with imported files or DRM-free purchases.

**First launch.** The app asks for access to your music. Notifications are optional, for analysis progress. Saving edited tags asks for permission to change those files. See [PRIVACY.md](PRIVACY.md). Analysis runs on the phone and shows a progress count. You can play music while it runs. On iPhone, analysis runs while the app is open, plus a short grace period after you leave it.

## Features

- **SMART shuffle.** Tap the shuffle button until it shows SMART, then pick any song. The queue stays close to that song's sound and moves from track to track. It notices what you skip. A track or artist can be marked *Don't recommend*. A playlist can be *kept together in SMART*.
- **Radio.** Start from a seed and let the player keep choosing what follows. For You can open a radio from one of those starts.
- **For You.** Rediscovery, not a streaming catalogue. Rows for songs you left unfinished, favourites gone quiet, what you play at this time of day, mixes grouped by sound, and a journey from one track to a distant one.
- **Tags.** Albums sort by their original release year. Names garbled by a wrong charset show correctly without retagging. Every genre and credited artist is kept.
- **Edit tags.** One song, a whole album, an artist, or a selection, in MP3, FLAC, M4A, Ogg, and Opus. The audio itself is not rewritten. On iOS, imported songs can be edited. Music-library songs are read-only. A FLAC file with no vendor string gains `Roomtone` on the first edit.
- **Lyrics.** Embedded lyrics, or a `.lrc` file named like the song. Timed lyrics follow the song, and a tap on a line seeks there.
- **Search.** Titles first, then artist, album, genre, lyrics, and meaning. Word order does not matter. A year or a decade narrows the results. The search runs on the phone.
- **A map of the library.** Songs that sound alike sit together. Add the page under Settings → Pages.
- **The player.** A queue that survives restarts. Playlists with M3U import and export. Album, artist, genre, and folder browsing. Fades, optional transitions, volume normalisation, an equaliser, and a sleep timer. A duplicate finder. Local backup and restore.
- **Make it yours.** Settings → Pages shows, hides, and reorders pages. Statistics covers listening time, habits, first listens, and top tracks.
- **Android extras.** Home-screen widgets, a Quick Settings tile, Android Auto, and media buttons that resume the last queue.
- **18 languages**, including Russian, Romanian, Arabic, Hebrew, Chinese, Japanese, and Korean.

iOS has the shared player except the Android extras, smooth transitions, and volume normalisation. See [the platform table](#one-engine-two-platforms).

## How SMART works

Every track is described by its sound, its tags, and what is known about its artist. SMART picks the next song from what you have just played and from the track you started with. All of it is computed on the device.

| Signal | Dimensions | Where it comes from |
|---|---:|---|
| **Audio embedding** | 960 | A pruned MobileNetV4-Conv-M encoder over the raw waveform. Its 4-bit weights run in an ONNX Runtime operator shipped with the app. |
| **Metadata embedding** | 384 | A 4 MB text encoder trained to reproduce MiniLM's sentence vectors, over trusted `genre; artist; original year; language` tags. |
| **Artist knowledge** | 384 | What an offline teacher wrote about the MusicBrainz and Wikidata artists the name index knows. A small adapter guesses the rest. |

Two rankings are interleaved into one candidate pool: audio close to the track you picked, and what is known about the seed's artist. Songs from a playlist marked *Keep together in SMART* are added if retrieval missed them.

A scoring chain balances closeness to the previous pick against a pull back toward the track you chose. Metadata rules nudge repeated artists down, suppress duplicate titles, and keep one very dense cluster from crowding the queue. Track titles are excluded from the embedding, so a filename cannot inject a genre claim.

Listening history is runtime context. It is not uploaded, and it is not the training set for the models. Until the audio index covers enough of the library, SMART ranks from the trusted metadata vectors instead of from audio. It does not fall back to a random shuffle.

Three ONNX graphs ship in the tree. The model bundle is about 20 MB on Android and on iOS. An imported track takes the same local path as everything else.

Further reading: [docs/model-selection.md](docs/model-selection.md) and [docs/for-you-ux.md](docs/for-you-ux.md).

## One engine, two platforms

The shared Kotlin code holds the player and SMART. Each platform supplies the native seams.

| | Android | iOS |
|---|:---:|:---:|
| On-device ONNX inference | ORT (JNI) | ORT (native C API, Swift host) |
| Audio decode for indexing | MediaCodec | AVAudioFile / AVAudioConverter |
| Library source | MediaStore | Files import · Music library |
| Playback | Media3 (ExoPlayer) | AVAudioEngine (imported files) · MPMusicPlayerController (Music library) |
| Tag editing (MP3 · FLAC · M4A · Ogg · Opus) | Yes | Imported files. The Music library is read-only. |
| Equaliser | The app's DSP | The app's AVAudioEngine graph, for imported files |
| Smooth transitions · volume normalisation | Yes | Not yet |
| Widgets · Quick Settings tile · Android Auto | Yes | n/a |

## Building

```bash
# Android (debug)
./gradlew :androidApp:assembleDebug

# Android (release: R8-minified, per-ABI splits under androidApp/build/outputs/apk/release/)
./gradlew :androidApp:assembleRelease

# iOS (from the Xcode workspace, after one pod install)
cd iosApp && pod install
xcodebuild -workspace iosApp.xcworkspace -scheme iosApp -sdk iphonesimulator build
```

- **JDK 17+** and the Android SDK. The first build downloads a large Kotlin/Native toolchain.
- **16 GB of RAM.** Gradle and Kotlin/Native get 8 GiB heaps.
- The Gradle daemon is disabled in `gradle.properties`, so every run starts cold.
- iOS brings ONNX Runtime in through CocoaPods (`onnxruntime-c`) and reaches it from Kotlin through the Swift host.
- A release build fails when a method of its DEX needs more than 255 registers. See [tools/README.md](tools/README.md).

## Project layout

```
core/smart      similarity engine, SMART chain, ONNX runtimes, tokenizer, MusicBrainz index
core/library    library scanning, tag reading and crash-safe writing, lyrics, playlists
core/playback   Media3 / AVAudioEngine playback, queue, equaliser
core/history    listening events, aggregates, For You impressions
composeApp      shared UI
androidApp      Android packaging shell
iosApp          Xcode project and the Swift host
docs            design notes and the inherited LatentJam release records
fastlane        store listing text
branding        logo
tools           scripts, research harnesses, and the release helpers
website         static site and the AltStore source template
```

`androidApp` holds no Kotlin. The app code, including `MainActivity`, lives in `composeApp`.

## Contributing

Issues and pull requests are welcome on this repository. Run the host tests before opening a pull request:

```bash
./gradlew testAndroidHostTest
```

That run starts with `checkNativeIdentifiers`, which catches test names Kotlin/Native rejects.

## Credits

Roomtone builds on [LatentJam](https://github.com/Nikita-sud/latentjam) by the LatentJam Project, and on:

- [ONNX Runtime](https://onnxruntime.ai/)
- [MobileNetV4](https://arxiv.org/abs/2404.10518)
- [EfficientAT](https://github.com/fschmid56/EfficientAT) (MIT)
- [MiniLM](https://arxiv.org/abs/2002.10957)
- [MusicBrainz](https://musicbrainz.org/) and [Wikidata](https://www.wikidata.org/)
- [DeepSeek](https://www.deepseek.com/), the offline teacher behind the artist descriptions in the knowledge pack
- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/), [Koin](https://insert-koin.io/), [Coil](https://coil-kt.github.io/coil/), and [Media3](https://developer.android.com/media/media3)

## Licence

**Apache-2.0.** See [LICENSE](LICENSE) and [NOTICE](NOTICE).

Bundled models under `androidApp/src/main/assets/ml/` (mirrored in `iosApp/iosApp/ml/`) are covered by the permissive licences in [LICENSE-MODEL.txt](androidApp/src/main/assets/ml/LICENSE-MODEL.txt). Read that file before repackaging the bundle. It records third-party attributions and two rights reviews that a public release of those weights still needs.

<p align="center"><sub>Everything runs on your device. Your taste stays there.</sub></p>
