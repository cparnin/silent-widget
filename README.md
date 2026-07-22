# Silent Widget

A 1x1 Android home-screen widget with three buttons: Ring, Vibrate, Silent.
Tap one and the phone switches instantly. The active mode lights up green.
The widget stays in sync with the volume rocker.

Plain `AppWidgetProvider` + `RemoteViews`, about 400 lines of Kotlin.

## Security

- No `INTERNET` permission. The app cannot send data anywhere, period.
- Button taps are handled by a non-exported receiver, so other apps cannot
  forge a broadcast to silence your phone.
- All `PendingIntent`s are immutable.
- The only sensitive permission is Do Not Disturb access, which Android
  requires from any app that changes ringer modes.

## How it works

| File | Job |
|---|---|
| `RingerWidgetProvider` | Renders the widget, re-syncs on unlock |
| `SetModeReceiver` | Handles button taps (not exported) |
| `RingerSyncService` | Keeps the widget live-synced with the volume rocker |
| `BootReceiver` | Restarts the sync service after reboot |
| `MainActivity` | One-screen setup for the two permissions |

Two Android quirks:

1. On Pixels, Silent works through Do Not Disturb (alarms only). The DND
   icon in the status bar while silent is normal and means it is working.
2. Live rocker sync requires a small foreground service, because Android 8+
   blocks `RINGER_MODE_CHANGED` for manifest receivers. Without the service
   the widget still re-syncs at every unlock. On low-RAM phones with
   aggressive battery managers (Samsung A-series), make a lite build by
   turning `RingerSyncService.start()` into a no-op.

## Build

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open in Android Studio and hit Run.

## Setup on the phone

1. Open the Silent Widget app.
2. Grant DND access.
3. Allow notifications (Android 13+).
4. Long-press the home screen, Widgets, drag Silent Widget on.

## License

[MIT](LICENSE)
