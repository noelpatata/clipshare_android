# ClipShare configuration

This document explains every setting in the ClipShare Android app and the behavior behind it.

The app works in one of two roles — **Client** or **Server** — chosen in
**Settings → General → App mode**. Everything else in Settings is shown conditionally based on
that choice.

---

## How devices find each other

Devices advertise themselves and are discovered over your LAN using two mechanisms:

| Mechanism | Defaults |
| --- | --- |
| mDNS | service type `_clipshare._tcp` |
| UDP beacons | broadcast to `255.255.255.255:40404` every ~1 s |
| Server listen port | `40403` (`ws://<host>:40403/ws`) |

Discovery is on by default, so in most cases there is nothing to configure: a client shows servers
under **Devices** on the home screen and you tap one to connect. The beacon advertises the server's
port and whether it uses TLS; the client switches to `wss` automatically when the server announces
TLS.

---

## General

### Device name

The name this device presents when it connects (`hello` message) or when it serves others. Defaults
to the device model (e.g. `Pixel 9`). Other devices see this name in their device list and history.

### App mode

- **Client** — this device connects to a server (desktop daemon or another phone).
- **Server** — this device listens for clients and relays clipboard content between them.

Changing the mode takes effect when you **Save**; if sync is running it is restarted automatically.

---

## Logging

### Max log file size (KB)

Upper bound for the on-disk log file (default `256`, allowed `16`–`4096`). When appending a new log
line would exceed the limit, the oldest lines are dropped to make room. The in-memory view is
additionally capped at 500 lines.

---

## History

### Max history entries

Upper bound for the local clipboard/command history (default `50`, allowed `10`–`1000`). The newest
entries are kept; the oldest are dropped as new ones arrive. History is stored as JSON in the app's
SharedPreferences and is independent of the log file.

---

## Clipboard

### Clipboard poll interval (ms)

How often the foreground clipboard watcher re-checks the clipboard (default `700`, clamped to
`200`–`10000`). Lower is more responsive; higher uses less battery.

---

## Client settings

Shown when **App mode = Client**.

### Server (IP or hostname)

The address of the server to connect to when you are not relying on discovery (or as a fallback). A
hostname works too, as long as it resolves on your network.

### Server port

The TCP port of the server's WebSocket endpoint. Default `40403`. Used both for manual connects and
as the port to dial when a whitelist entry has no port.

### Shared token (optional)

A shared secret, if the server requires one. When set, it is appended to the WebSocket URL as
`?token=...`. The server rejects connections whose token does not match.

### Auto-connect

Default **on**. When on, the client connects automatically to a discovered server (or to the
configured host) as soon as sync starts, and reconnects with backoff while the service runs. When
off, the app stays in "searching" state until you tap a device or enter a host.

### Discovery

Default **on**. Turns mDNS + UDP beacon scanning on/off. Disable it if you only ever connect
manually or via whitelist and do not want the scanning traffic.

### Beacon port

The UDP port the server announces on (`40404` by default) — this is the *listening* port the client
also opens to receive beacons. Only change it if you have changed the port on every server you use.

### Connection mode

- **Discover** (default) — scan via mDNS/beacons and connect to whatever shows up (subject to
  Auto-connect).
- **Whitelist** — do not scan. Try each whitelisted IP in turn until one connects, and verify the
  server's announced name against the whitelist entry.

#### Whitelist entries

Each entry has an optional **Name** and an **IP**. Matching is on name *or* IP:

- The client dials `whitelist-IP : server-port`.
- When the server announces its name after connecting, the client checks it against the entry.
  An entry with a blank name accepts any server at that IP; an entry with a name rejects a server
  that announces something different.
- After a failed attempt the client waits ~1.5 s and advances to the next candidate.

---

## TLS

TLS settings for both roles live in one place.

### Client

#### TLS (wss)

Default **off**. Enables encrypted `wss://` connections to desktop daemons or Android servers.
Requires a client certificate **or** a trusted CA to be present, otherwise the connection is refused
with a clear error.

#### Verify hostname

Default **on** (strict). When enabled, the server certificate's SANs must match the dialed
address. Turn it off to verify the server certificate against the trusted CA only — hostname/IP-
address matching is skipped, keeping connections working when a device moves to another wifi/DHCP
network; there is no need to regenerate or re-import certificates when the LAN address changes.

#### Client certificates

`.p12` bundles (exported by the desktop daemon's `clipshare cert export`). The first imported
certificate is used automatically for mutual TLS, and its CA is trusted automatically — there is no
separate "trusted CA" list to maintain.

Certificates can be imported two ways:

- **Import .p12** — pick a `.p12` file from the device.
- **Scan QR certificate** — scan the QR code shown by another ClipShare device (Android server mode)
  or printed by the desktop daemon's `clipshare cert qr`. The QR encodes a compact gzip-compressed
  certificate bundle (key + leaf, without the CA), a legacy `.p12` bundle, or a CA certificate; all
  are accepted automatically. The app rebuilds the `.p12` locally.

The CA is deliberately not bundled with device certificates so the QR stays small. Trust it once
per server by scanning `clipshare cert qr --type ca` (or the Android server's CA QR), and it is
stored in the trusted CA list.

---

### Server

Shown when **App mode = Server**.

#### Server port

The port the built-in server listens on (`40403` by default). Clients must use the same port unless
they learn it from a beacon.

#### TLS (wss)

Default **off**. When enabled, the app generates a local CA + server certificate pair, stores them
in the app's key store, and serves `wss://` instead of `ws://`. Clients then need the CA
certificate to trust the server. The certificate is not bound to any particular LAN IP, so it
keeps working across wifi/DHCP changes.

- **Regenerate cert** — replaces the stored certificate pair (e.g. to rotate before expiry).
- **Copy CA** — copies the CA certificate (PEM) to the clipboard so you can paste it into a file.
- **Share CA file** — sends the CA certificate to another app/device as a file.
- **Show QR** — shows the CA certificate as a QR code; another ClipShare device in client mode can
  scan it with **Scan QR certificate** to trust this server.

> The server-side certificate password is fixed (`clipshare`) and used only to protect the local
> PKCS#12 store; it is not a connection credential.

---

## Background capture

By default, Android 10+ only lets the *foreground* app read the clipboard, and since Android 11 the
platform no longer exempts accessibility services from that rule (`ClipboardService` only allows
the focused app, the default IME, or apps with privileged permissions). So on Android 10+ a normal
app **cannot read the clipboard in the background** — a background read is refused by the system,
visible in logcat as `ClipboardService: Denying clipboard access to ...`.

Enable **Background capture** in **Settings → Background capture** (opens the system Accessibility
settings) to let the app's accessibility service capture copies made in other apps anyway:

- **Android 10+** — the service reads the text out of `TYPE_VIEW_TEXT_SELECTION_CHANGED`
  accessibility events, which carry the source field's text and the selected range. No clipboard
  read is involved, so this is not blocked. It is *best effort*: it treats "selected" as "copied",
  works reliably in standard text fields, and is less reliable in WebViews/Chrome. Images cannot
  be captured in the background on modern Android at all.
- **Android 9 and below** — background clipboard reads are unrestricted, so the service falls back
  to polling the clipboard periodically (~0.7 s), which also covers images.

In both cases the service only pushes when the sync service is connected, and it skips while the
ClipShare app itself is in the foreground (the built-in foreground watcher already covers that).
Deduplication is shared between the foreground watcher and the accessibility service, so the same
text is never sent twice when the app moves between foreground and background.

Some devices disable accessibility services after a reboot — re-enable it if background capture
stops working.

---

## Diagnostics

- **View logs** opens the log screen: the last log lines, with **Clear** (wipe the log) and
  **Share** (export the log to another app). Logs are useful to troubleshoot connection or
  clipboard issues.
- **Clear history** on the home screen wipes the local clipboard/command history.

---

## Quick Settings tile & notification

- Add the **ClipShare** tile to the notification shade to start/stop sync without opening the app.
  The tile shows **Connected** / **Idle** state.
- The foreground service posts an ongoing notification ("ClipShare sync"). It reflects the current
  state: connecting, connected, reconnecting, server running with N clients, etc. Android shows the
  notification while the service runs; it is required for background operation.

---

## Behavior notes

- **Loopback protection** — content that arrives from the network is written to the local clipboard
  but never sent back out, so there is no echo between devices.
- **Deduplication** — each copy is pushed at most once, regardless of whether it is first observed
  by the foreground watcher or the accessibility service.
- **Images** — images are downscaled and compressed so the resulting base64 JSON payload stays
  within a size budget (default ~10 MB). JPEGs are progressively re-compressed; large PNGs fall
  back to JPEG. On arrival, images are written to the clipboard as file URIs.
- **Reconnect** — the client reconnects with exponential backoff (1 s → 10 s) and sends a keepalive
  ping every ~30 s.
- **Cleartext** — plain `ws://` on the LAN is allowed (`android:usesCleartextTraffic`), which is
  required for unencrypted sync. For anything sensitive, use TLS.

---

## Defaults summary

| Setting | Default |
| --- | --- |
| App mode | Client |
| Server port | 40403 |
| Beacon port | 40404 |
| mDNS service | `_clipshare._tcp` |
| Auto-connect | on |
| Discovery | on |
| Connection mode | Discover |
| TLS (wss) | off |
| Verify hostname | on |
| Token | empty |
| Max log file size | 256 KB (16–4096) |
| Max history entries | 50 (10–1000) |
| Clipboard poll interval | 700 ms (200–10000) |
| Image payload budget | ~10 MB (internal) |
| Background capture | off (opt-in) |