# Roomtone

A local music player based on [LatentJam](https://github.com/Nikita-sud/latentjam). Same app, your own install, ready for new features.

Roomtone is the sound already in the room: the records you own, played offline. LatentJam is Apache-2.0. This copy keeps that license, the `LICENSE` file, and the original copyright lines.

## What changed

- Home screen name, in-app copy, Android Auto root, and the iOS display name say **Roomtone**.
- Android application id and iOS bundle id are `io.github.djsecq.roomtone`, so this build can sit beside an installed LatentJam.
- Kotlin packages stay `io.github.nikitasud.latentjam`. Renaming them would make every upstream update a rewrite.
- A FLAC file edited in the app still records the vendor string `LatentJam`. That string is file metadata, not the app name.

## Git

`upstream` is https://github.com/Nikita-sud/latentjam. There is no `origin` yet. `main` matches upstream. Work happens on `roomtone`.

Pull upstream later with:

```bash
git fetch upstream
git merge upstream/main
```

This folder is not a GitHub fork and nothing has been pushed. A fork is only useful when you want a public repo or a pull request back to LatentJam. Say so and one can be created under `dj-secq`.

## Build

Android debug APK, from this directory, with JDK 17 and the Android SDK:

```bash
./gradlew :androidApp:assembleDebug
```

The first build downloads a large Kotlin toolchain and needs a lot of RAM. The upstream README has the iOS build steps. Release scripts under `tools/release/` and `website/altstore.json` still describe LatentJam's own releases. Do not publish with them as-is.

## License

Apache License 2.0. See `LICENSE` and `NOTICE`.
