# Roomtone

A local music player based on [LatentJam](https://github.com/Nikita-sud/latentjam). Same app, your own install, ready for new features.

Roomtone is the sound already in the room: the records you own, played offline. LatentJam is Apache-2.0. This copy keeps that license, the `LICENSE` file, and the original copyright lines.

## What changed

- Home screen name, in-app copy, Android Auto root, and the iOS display name say **Roomtone**.
- Android application id and iOS bundle id are `io.github.djsecq.roomtone`, so this build can sit beside an installed LatentJam.
- Kotlin packages stay `io.github.nikitasud.latentjam`. Renaming them would make every upstream update a rewrite.
- A FLAC file edited in the app still records the vendor string `LatentJam`. That string is file metadata, not the app name.

## Git

`origin` is https://github.com/dj-secq/roomtone. That repository belongs to `dj-secq` and has no parent repository. `upstream` stays https://github.com/Nikita-sud/latentjam, and push to upstream is disabled.

A local `main` still matches `upstream/main`. Work stays on `roomtone`, which tracks `origin/main`.

Pull upstream later with:

```bash
git fetch upstream
git merge upstream/main
```

Push this line with:

```bash
git push origin roomtone:main
```

## Build

Android debug APK, from this directory, with JDK 17 and the Android SDK:

```bash
./gradlew :androidApp:assembleDebug
```

The first build downloads a large Kotlin toolchain and needs a lot of RAM. The upstream README has the iOS build steps. Release scripts under `tools/release/` and `website/altstore.json` still describe LatentJam's own releases. Do not publish with them as-is.

## License

Apache License 2.0. See `LICENSE` and `NOTICE`.
