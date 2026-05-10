# Simple Music Player

Simple Music Player, also known as SMP, is a Kotlin-based Android music player focused on local music library management, playlist editing, and lightweight streaming support.

> This project is provided for learning and research purposes only.

## Current Features

- Scan local music from the Android media library.
- Manually import audio files, CUE files, and LRC lyric files.
- Scan a user-selected folder through Android storage access permissions.
- Read common audio metadata, including title, artist, album, duration, track number, year, and embedded artwork.
- Support common Android audio formats such as MP3, FLAC, WAV, M4A, AAC, OGG, OPUS, WMA, and MIDI where supported by the device.
- Read CUE files and create playable split-track entries.
- Detect paired LRC lyric files and show timed lyrics during playback.
- Create, save, edit, and delete playlists.
- Automatically create album playlists during music scanning.
- Keep a playback history playlist with the latest 100 records.
- Provide a pinned "My Favorite Music" playlist.
- Search and filter the local music library by title, artist, album, alias, comment, tag, and rating.
- Add per-track alias, comment, tags, and star rating.
- Support normal playback, true random playback, pseudo-random playback, and single-track repeat.
- Support playback speed control, volume control, current queue viewing, and track details.
- Show local artwork thumbnails in the library, playlists, mini player, and now-playing page.
- Support Bluetooth headset and system media controls.
- Support configuration import/export for settings, local profile, stream account cookies, and user playlists.
- Support DizzyLab streaming login, purchased album loading, album detail loading, streaming playback, and stream downloading.
- Support NCM conversion based on code adapted from [NCMConverter4a](https://github.com/cdb96/NCMConverter4a).

## DizzyLab Streaming

SMP currently supports DizzyLab as its first streaming source. After logging in through the built-in WebView, the app can store the user cookie locally, load purchased albums, read album details, and play streamed tracks.

More streaming platforms are planned for future versions.

## Permissions

SMP uses Android media permissions by default, which is the recommended mode for a normal music player. The app can also request folder access through Android's Storage Access Framework for downloads and manual scanning.

For users who need more persistent download and delete permissions, SMP also provides an optional entry for Android's "All files access" permission. This permission is not required for normal local playback.

## Build

Open this project in Android Studio and run the `app` module.

Current build settings:

- Android Gradle Plugin 8.11.1
- Kotlin Android Plugin 2.0.21
- Gradle 8.14.5
- compileSdk 35
- minSdk 26
- JDK 17 for the Gradle daemon

If your Android SDK is not installed at `D:\Android`, update `sdk.dir` in `local.properties`.

## Release

Current release version: `1.4`

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## TODO

- More platform support
- Dynamic lyrics
- Sound effect settings
- Known issue fixes

## License

This project is licensed under the MIT License.

This project is for learning and research purposes only. Please respect the terms of service of all streaming platforms and the copyright of all music content.
