# Liberty Chat

*Powered by Torlando-Tech's Columba.*

**A Liberty Communication Systems, Inc. (LCS) distribution of Columba** — native
Android messaging & voice over LXMF / Reticulum (Bluetooth LE, TCP, or RNode LoRa).

Built on [Columba](https://github.com/torlando-tech/columba) by torlando-tech
(MPL 2.0). Original design and code are theirs; LCS branding and the changes
below are Liberty Communication Systems, Inc.

**First release: v1.0.0** (based on Columba 2.0.9). Tag the release commit `v1.0.0`
to build it — the in-app version and APK filenames read `1.0.0`.

## LCS changes in this branch (`liberty-chat`)

1. App renamed to **Liberty Chat** (launcher label + About + offline banner).
2. LCS logo across launcher densities + About screen (run `scripts/lcs-branding/apply-lcs-logo.py`).
3. Announce button added left of the search icon on the Chats screen (manual announce).
4. RNode default frequency set to US **slot 51 = 914.875 MHz** (LCS standard).
5. TX-power hint under the RNode power field: *Heltec V4 max 28 dBm; RAK / LILYGO max 22 dBm*.
6. "Long Fast" preset badge now reads **LCS Recommended**.
7. Removed the *Display Logo on RNode* option in the RNode wizard.
8. TCP client server list reduced to a single option: **public.lcs.network:4245** (+ Custom).
9. Share-APK feature rebranded and serves `liberty-chat-<version>.apk` (the installed LCS build).
10. Built-in RNode flasher entry removed from Settings.
11. "Check for Updates" now targets the LCS GitHub repo (`daylight-hub/columba`).
12. Removed the *Report Bug* button.
13. About license section credits torlando-tech (original) and LCS (fork); MPL 2.0 retained.
14. Removed the GitHub Repository / Report an Issue / About Reticulum links.
15. New **Build Liberty Chat APKs** Actions workflow — one-click build of the four
    no-Sentry `official-rns-py` APKs.

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
