# YDeck

**Nothing left open.** · שום דבר לא נשאר פתוח.

YDeck closes the open loops of developers who work with AI coding agents. On Windows it puts a widget for each project on
the desktop and a strip inside the project's folder in File Explorer, follows Claude Code sessions (what each one did,
what's next, whether an agent is working or waiting for you), and shows what's still open: uncommitted changes, commits
not pushed, a dev server left running, keys missing from `.env`. On Android it is the projects' memory in your pocket:
decisions, the last session and the next step, synced from the computer through an end-to-end encrypted relay.

Site: <https://ydeck-app.pages.dev>

## What's here

| Folder | What it is |
|---|---|
| `desktop/` | The Windows app: Kotlin, Compose Desktop, packaged as an MSI with jpackage + WiX. |
| `app/` | The Android app: Kotlin, Jetpack Compose and Glance (home-screen widgets). |
| `shared/` | Code and assets both apps use. |
| `relay/` | The phone ↔ computer relay: a Cloudflare Worker with a Durable Object per pair. |
| `site/` | The website (Cloudflare Pages), built by `site/build.cjs`. |
| `store/` | Brand, ads, store listing and the HTML/JS that render the films and images. |
| `tools/` | Generators, such as the services catalogue from Simple Icons. |

## Privacy and the relay

The phone and the computer only ever make outgoing connections, and only data goes through the relay, never commands.
Each pair has an opaque box encrypted with AES-256-GCM; the key exists only in the QR code the phone scans, so the relay
never sees what's inside. The computer is the single source of the projects. See `store/privacy/index.html`.

## Building

You need JDK 21 (Android Studio's bundled one works) and, for the Android app, the Android SDK.

```bash
# Windows app
./gradlew :desktop:run                 # run it
./gradlew :desktop:test                # tests
./gradlew :desktop:distribute          # dist/YDeck-Windows-<version>.msi (bump desktopVersion first)

# Android app
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

A debug Android build is seeded from `projects.json` at the repo root when you have one (it's personal and kept out of
git), or from the demo projects in `store/play/source/demo-projects.json`. A release build starts empty. Release signing
reads `~/.android-keys/ydeck-upload.properties`; without it the build falls back to the debug key.

The scripts under `store/` and `site/` render with Playwright and deploy with Wrangler; some still point at the paths of
the machine they were written on, so adjust those before running them.

## Downloads

The Windows installer is published on the [Releases](../../releases) page. Each release is built by GitHub Actions on a
clean Windows machine from the tagged commit ([.github/workflows/windows.yml](.github/workflows/windows.yml)); to make
one, bump `desktopVersion` in `desktop/build.gradle.kts`, commit, and push a matching tag (`git tag v1.15.4 && git push --tags`).
The builds are not code-signed yet, so SmartScreen warns on first run (More info → Run anyway).

### Uninstalling

Windows Settings → Apps → YDeck → Uninstall. YDeck changes a few things outside its own folder, each one only when you
turn it on, and each one off again from its Settings: the project logo on folders (a `desktop.ini` in the folder),
"Open in YDeck" on File Explorer's right-click menu, and starting with Windows. To leave nothing behind, turn those off
before uninstalling. Your projects and settings stay in `%APPDATA%\YDeck` and `%LOCALAPPDATA%\YDeck` until you delete them.

## Code signing policy

Free code signing provided by [SignPath.io](https://about.signpath.io), certificate by [SignPath Foundation](https://signpath.org)
(being set up). Only the Windows installer built from this repository by GitHub Actions is signed, and every signing
request is approved by hand. Committer, reviewer and approver: [Yoni (yonidev0101)](https://github.com/yonidev0101).
The full policy: <https://ydeck-app.pages.dev/code-signing>. Privacy: <https://ydeck-app.pages.dev/privacy>.

## License

The code is under the [MIT License](LICENSE). The YDeck name and logo identify this project; please don't use them for a
fork in a way that suggests it is YDeck itself.
