# Roomtone website

Static pages for this repository. They are not published to a custom domain.
The page uses local CSS, JavaScript, fonts, and selected public app demo media.

## Preview locally

From the repository root:

```sh
python3 tools/build_site.py
python3 -m http.server 4173 --bind 127.0.0.1 --directory build/site
```

Open http://127.0.0.1:4173/. Rebuild after changing the source.

## Publish

In GitHub, set Settings → Pages → Source to GitHub Actions, then run Actions → Publish website on `main`.
Publication is manual. Only the generated `build/site` folder is uploaded. Media are selected in `tools/build_site.py` from `docs/media` and `branding/logo.svg`.

The live project page until Pages is turned on is <https://github.com/dj-secq/roomtone>.

## Assets and content

- Alsina Ultrajada is bundled with its original font license in `fonts/`.
- Screenshots show the Android player with a fictional demo library.
- Install links point at the build instructions in this repository. Roomtone has no F-Droid listing and no published IPA yet.
- `altstore.json` is the SideStore/AltStore source template. It lists no downloads until a Roomtone release exists. `tools/release/altstore-source.py` fills it from this repository's GitHub releases.
- The privacy statement describes the app.
- Contact: <https://github.com/dj-secq/roomtone/issues>.
