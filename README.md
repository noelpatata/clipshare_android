# ClipShare (Android client)

ClipShare is an Android client for syncing the clipboard with a desktop daemon over your local network. It pairs with a Go daemon running on a desktop machine using a simple JSON-over-WebSocket protocol.

## Features

- **Two-way clipboard sync** — copy on one device, paste on the other
- **Zero-config setup** — discovers the desktop daemon automatically on your LAN
- **Auto-discovery** — mDNS (`_clipshare._tcp`) and UDP beacon announcements (port `40404`)
- **Foreground service** — keeps the connection alive and writes incoming clipboard content in the background
- **Quick Settings tile** — toggle sync from the notification shade
- **Manual connect** — fall back to entering a host and port by hand
- **Clipboard history** — recent items are stored locally and shown in the app

## Requirements

- Android 8.0 (API 26) or newer
- A desktop daemon running the ClipShare protocol somewhere on your network

## Building

Requires JDK 17 and the Android SDK (compileSdk 36).

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

## Running

Install the APK on your device and open the app. On first launch it will ask for notification permission (needed for the background sync service). Then either:

1. Let it auto-connect — tap a discovered device from the list, or
2. Enter the daemon's address manually in **Settings**.

## Protocol

The app talks to the daemon via WebSocket at `ws://<host>:40403/ws` (the port is discoverable from mDNS/beacons). Messages are JSON objects with a `type` and `data` field:

- `hello` — client announces itself on connect (`name`, `platform`, `version`)
- `clipboard` — a clipboard text payload with `text`, `ts`, and `from`
- `ping` / `pong` — keepalive
- `error` — server-side error (`code`, `msg`)

An optional `?token=` query parameter is appended when a token is configured.

## Notes

- Android 10+ restricts reading the clipboard to the foreground app, so local clips are pushed while the app is open; incoming clips from the network are written by the service and always work.
- `android:usesCleartextTraffic` is enabled for plain `ws://` traffic on the LAN.

## License

Not specified yet.
