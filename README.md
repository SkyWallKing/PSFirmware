# PS Firmware

A small Android app that keeps an **ongoing notification** with the current PlayStation firmware status for the region you choose: the **latest** and **minimum** firmware versions, whether the region is **online**, and when the data was last updated.

<p align="center">
  <a href="https://github.com/SkyWallKing/PSFirmware/releases/latest/download/PSFirmware.apk">
    <img src="https://img.shields.io/badge/Download-APK-0050B8?style=for-the-badge&logo=android&logoColor=white" alt="Download APK" height="44">
  </a>
  <br>
  <a href="https://github.com/SkyWallKing/PSFirmware/releases/latest">
    <img src="https://img.shields.io/github/v/release/SkyWallKing/PSFirmware?label=latest%20release&color=0050B8" alt="Latest release">
  </a>
</p>

> [!WARNING]
> **This app was heavily vibe coded.** It does its job, but it hasn't been through a serious code review or much testing on real devices. Use it at your own risk.

## Screenshots

| Notification | Notification (expanded) | App |
|:---:|:---:|:---:|
| <img src="docs/screenshots/notification-collapsed.png" width="260" alt="Collapsed notification"> | <img src="docs/screenshots/notification-expanded.png" width="260" alt="Expanded notification"> | <img src="docs/screenshots/app.png" width="220" alt="App screen"> |

## Features

- **Ongoing notification**: updates the same notification in place and doesn't post new ones. It sits in the main section of the shade, above silent notifications, and makes no sound.
- **Updates every 5 minutes** while the phone is awake, and right away when you turn on or unlock the screen. An alarm acts as a fallback while the phone is asleep.
- **Region picker**: on first launch the app asks which region to check: Brazil, United States, Japan, Europe, United Kingdom, South Korea, Mexico, Australia, Saudi Arabia, Taiwan, Russia, China or Hong Kong.
- **Notification in the region's language** (Portuguese for Brazil, Japanese for Japan, and so on). The app itself is always in English.
- **Automatic updates**: checks GitHub for new releases when you open the app and every 12 hours. When there's a new version you get a notification, and one tap downloads and installs it (Android asks you to confirm).
- Comes back after a reboot, and reappears if you swipe it away.
- No accounts, no ads, no tracking. The app only calls the public firmware API and the GitHub releases API.

## Install

1. Download [`PSFirmware.apk`](https://github.com/SkyWallKing/PSFirmware/releases/latest/download/PSFirmware.apk) from the [latest release](https://github.com/SkyWallKing/PSFirmware/releases/latest) on your phone (Android 8.0 or newer).
2. Open it and allow installing from unknown sources if Android asks.
3. Open **PS Firmware**, pick a region and tap **Enable notification**.

**Tip:** if updates are slow on your phone (common on Samsung, Xiaomi and other brands with aggressive battery saving), tap **Battery optimization** in the app and set PS Firmware to *Unrestricted*.

## Build from source

You need JDK 17+ and the Android SDK (platform 35).

```bash
export ANDROID_HOME=/path/to/Android/Sdk   # or create local.properties with sdk.dir=...
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the debug key so it can be installed directly.

## Credits

- Firmware data provided by **slopcheck** through the API at [psn.etawen.lol](https://psn.etawen.lol/api/v1/firmware). Thank you!

## Disclaimer

This is an unofficial fan project. It is not affiliated with, endorsed by or sponsored by Sony Interactive Entertainment. "PlayStation" and the PlayStation logo are trademarks of Sony Interactive Entertainment Inc.
