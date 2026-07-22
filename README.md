# Silent Widget

A tiny, fast, 1×1 Android home-screen widget with three buttons:

- 🔔 **Ring**
- 📳 **Vibrate**
- 🔇 **Silent**

Tap one; the phone switches instantly and the active button lights up green.
The widget stays in sync with the volume rocker and the system volume panel.

Built with classic `AppWidgetProvider` + `RemoteViews` — no frameworks, no
network access, ~400 lines of Kotlin.

## Why

Modern Android buried "make my phone silent" behind volume-panel taps.
This puts it back on the home screen, one tap away, like phones used to be.

## Security posture

- **Zero network permission.** The app cannot phone home, show ads, or
  exfiltrate anything — it has no `INTERNET` permission at all.
- Widget tap handling lives in a **non-exported receiver**, so other apps
  can't forge a broadcast to silence your phone.
- All `PendingIntent`s are `FLAG_IMMUTABLE`.
- The only sensitive permission is **Do Not Disturb access**, which Android
  requires for any app that switches ringer modes (see below).

## How it works

| Piece | Job |
|---|---|
| `RingerWidgetProvider` | Renders the widget; re-syncs on unlock |
| `SetModeReceiver` | Handles button taps (not exported) |
| `RingerSyncService` | Foreground service that listens for `RINGER_MODE_CHANGED` / DND changes so volume-rocker changes update the widget in real time |
| `BootReceiver` | Restarts the sync service after reboot |
| `MainActivity` | One-screen setup: grant DND access + notifications |

Two Android quirks worth knowing:

1. **Silent = DND on Pixels.** Pixels ignore `RINGER_MODE_SILENT` from apps,
   so the Silent button uses Do Not Disturb (alarms-only). You'll see the DND
   icon in the status bar when silent — that means it's working. On most
   other phones plain silent works and DND is the fallback.
2. **The foreground service is not optional** for live sync.
   `RINGER_MODE_CHANGED` can't reach manifest receivers since Android 8, so a
   runtime-registered receiver (kept alive by a minimal, silent foreground
   notification) is the only way the widget can follow the volume rocker.
   Everything else works without the service; the widget also re-syncs every
   time you unlock.

### Low-RAM / aggressive-battery phones (Samsung A-series etc.)

Battery managers on these phones kill the sync service anyway, so the
service costs RAM for little benefit. Make a "lite" build by turning
`RingerSyncService.start()` into a no-op — taps still work perfectly and the
widget re-syncs on every unlock. On Samsung, also set the app's battery mode
to **Unrestricted** if you keep the service.

## Build & install

Open the project in Android Studio and hit Run, or:

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Install on a connected phone (USB debugging on):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Setup on the phone

1. Open the **Silent Widget** app.
2. Tap **Grant DND access** → enable Silent Widget in the list.
3. Tap **Allow notifications** (Android 13+; needed for the sync service).
4. Long-press the home screen → **Widgets** → drag **Silent Widget** on.

## Sharing with friends (sideload)

Send them the APK (Drive, email, Signal). On their phone: tap the file →
allow installs from that source when prompted → Install → do the setup
steps above. The debug APK works fine for personal sharing; for anything
bigger, sign a release build.

## License

MIT — see [LICENSE](LICENSE).
