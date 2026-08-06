# Liberty Chat

*Powered by Torlando-Tech's Columba.*

**A Liberty Communication Systems, Inc. (LCS) distribution of Columba** — native
Android messaging & voice over LXMF / Reticulum (Bluetooth LE, TCP, or RNode LoRa).

Built on [Columba](https://github.com/torlando-tech/columba) by torlando-tech
(MPL 2.0). Original design and code are theirs; LCS branding and the changes
below are Liberty Communication Systems, Inc.

**Current release: v1.2.1** (forked from Columba 2.0.9). The release workflow
derives the version from the tag, so tagging the build commit `v1.2.1` is what
makes the in-app version and APK filenames read `1.2.1`.

Full history is in [CHANGELOG.md](CHANGELOG.md).

## What LCS changes

### Messaging

- **Push-to-talk voice messages**, interoperable with
  [Sideband](https://github.com/markqvist/Sideband) in both directions.
  Clips are sent as LXMF `FIELD_AUDIO` (0x07) rather than as file attachments,
  which is what makes them arrive as voice messages rather than inert files.
  Received clips autoplay when the conversation is open, and the bubble can be
  tapped to replay.
  - **Low bandwidth** (default) — Codec2 1200, ~150 B/s. For LoRa/RNode links.
  - **High bandwidth** — Opus, ~3 KB/s. For WiFi or TCP.
  - The toggle selects the codec, not just the bitrate — the same choice
    Sideband's own high-quality PTT option makes.
- Announce button on the Chats screen, left of the search icon.

### Radio

- RNode default frequency: US **slot 51 = 914.875 MHz** (LCS standard).
- RNode default TX power **22 dBm** (the RAK / LILYGO ceiling), default
  interface mode **Full**. Per-region defaults are clamped to each band's
  regulatory maximum, so EU 868 still defaults to 14 dBm and EU 433 to 12.
- TX-power hint under the RNode power field: *Heltec V4 max 28 dBm; RAK /
  LILYGO max 22 dBm*.
- **Long Fast** badged *LCS Recommended* for general use; **Short Fast** badged
  *Best for voice over LoRa* — at ~10.9 kbps it is the slowest preset that can
  actually carry a live call, against Long Fast's ~1.07 kbps.
- **Codec2 3200** badged *Voice/PTT* in the call quality picker.
- **Mid-call codec switching.** When the pre-dial link probe says the link is
  too slow for the codec in use, the call screen offers a picker covering both
  Codec2 and Opus, plus a push-to-talk suggestion. Switching signals the peer,
  so both ends move — including Sideband and MeshChat peers.
- *Display Logo on RNode* option removed from the wizard.
- Built-in RNode flasher entry removed from Settings — LCS ships pre-flashed
  hardware.

### Network

- No TCP bootstrap interface is seeded. A fresh install comes up on
  AutoInterface and Bluetooth LE only — nothing reaches the internet until the
  user adds a server or attaches an RNode. Upstream's Beleth RNS Hub seed is
  removed and deleted from existing installs on upgrade.
- When adding a TCP server, the list offers LCS infrastructure:
  **public.lcs.network:4245** and **iprnode.local:4545**, plus Custom.

### Identity

- Renamed to **Liberty Chat** throughout, with the LCS logo across launcher
  densities and a "Liberty Chat — powered by Columba" splash wordmark that
  renders correctly on every supported Android version.
- Liberty theme: navy / gold / silver.
- **Buy RNode Radios** call-to-action in Settings → About, linking to
  `www.lcs.network`.
- Share-APK serves `liberty-chat-<version>.apk`.
- Update checks target `daylight-hub/columba`, so "Check for Updates" and
  "View Release" track LCS builds.
- Removed: *Report Bug*, the GitHub / Report an Issue / About Reticulum links,
  and the crash-reporting opt-in popup.
- About credits torlando-tech (original) and LCS (distribution); MPL 2.0
  retained.

### Build

- **Build Liberty Chat APKs** Actions workflow — one-click build of the four
  no-Sentry `official-rns-py` APKs.

## Upstream stack

| Component | Version |
|---|---|
| Columba | 2.0.9 (fork base) |
| RNS (Python) | 1.1.9 — `torlando-tech/Reticulum`, pinned commit |
| LXMF (Python) | 0.9.2 — `torlando-tech/LXMF`, pinned commit |
| LXST | LXST-kt `v0.0.4` (Kotlin; no Python LXST) |

The `kotlinBackend` flavor uses reticulum-kt `v0.0.21` and LXMF-kt `v0.0.13`
instead. LCS ships the `pythonBackend` flavor.

See `APPLY.md` for how this branch was produced and the items that need your input.

---

<sub>Original Columba README follows.</sub>

<p align="center">
  <img src="./columba-icon.svg" width="200" height="200" alt="alt text" />
</p>

# Columba

Columba is a simple messaging and voice app for the [Reticulum](https://github.com/markqvist/Reticulum) network on Android. Send [LXMF](https://github.com/markqvist/LXMF) messages and make [LXST](https://github.com/markqvist/LXST/tree/master/LXST) voice calls without relying on the internet, cell towers, or any central servers.

Built with a native Android interface and Material Design 3, Columba brings mesh networking to your pocket in a familiar, easy-to-use package.

<img src="https://github.com/user-attachments/assets/77532689-3568-4224-a75d-62fb08a4ad33" width="33%"></img> <img src="https://github.com/user-attachments/assets/70804d27-6307-441f-b29f-b32ecfe72098" width="33%"></img> <img src="https://github.com/user-attachments/assets/c2d2c9ad-63da-42e0-a045-bc30ec3bb128" width="33%"></img>

## What You Can Do

- **Message without infrastructure** - Send messages even when the internet is down or unavailable
- **Connect multiple ways** - Use Bluetooth LE to connect to those around you anywhere, Wifi for those at home, LoRa radio via [RNode](https://github.com/markqvist/RNode_Firmware) for those at a distance, or use TCP to connect to any Reticulum server around the world 
- **Stay private** - End-to-end encryption with no accounts, no tracking, and no central servers
- **Share location** - Share your location securely with others, viewable from a dedicated map
- **Download maps for offline use** - Download or import map files. Supports vector and raster, in MBTiles format
- **Browse NomadNetwork** - Access nomadnet pages over Reticulum networks
- **Build your network** - Help relay traffic for others and expand the mesh
- **Keep your identity** - Generate your messaging identity right on your device
- **Manage multiple identities** - Easily swap between multiple identities
- **Export and import identities** - Keep an external backup of your keys, or migrate to a new device. Import from other Reticulum clients like [Sideband](https://github.com/markqvist/Sideband)
- **Share your identity via QR Code** - Built in QR code scanner and generator for sharing your identity with others
- **Custom Color Themes** - Don't like the default colors? Rice to your heart's content! 

## Getting Started

Download the latest release from [Releases](https://github.com/torlando-tech/columba/releases) and install on your Android device. See [SECURITY.md](./SECURITY.md) for APK verification instructions.

<a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/torlando-tech/columba"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" height="60" alt="Get it on Obtainium"></a>

Alternatively, you can download the apk via [NomadNet](https://github.com/markqvist/NomadNet) at `8d1788fdb4e9f85303cfdf7481e721c7:/page/index.mu`

## About Reticulum

[Reticulum](https://github.com/markqvist/Reticulum) is a networking stack that lets devices communicate directly with each other, forming resilient mesh networks. It is optimized for low bandwidth, high latency connections, and can communicate over nearly any medium. Columba uses [LXMF](https://github.com/markqvist/LXMF) (Lightweight Extensible Message Format) to send messages across the Reticulum network, and uses a native Android implementation of [ble-reticulum](https://github.com/torlando-tech/ble-reticulum) to enable messaging over BLE with other Android and Linux devices.

Want to learn more? Visit [Reticulum's documentation](https://reticulum.network/).

## Why "Columba"

Columba, latin for "dove," is a [constellation](https://en.wikipedia.org/wiki/Columba_(constellation)) in the southern sky depicting a dove. Doves are commonly a symbol of peace and hope, and have been used as messengers throughout history. 
