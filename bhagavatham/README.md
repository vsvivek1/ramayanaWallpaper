# Bhagavatham Wallpapers

An Android app that changes your wallpaper every day to the next incident of the Srimad Bhagavatam, with a short story of that incident. It walks through 318 incidents in order, skandha by skandha (First 22, Second 10, Third 30, Fourth 30, Fifth 14, Sixth 18, Seventh 18, Eighth 24, Ninth 30, Tenth 92, Eleventh 20, Twelfth 10), from the sages of Naimisharanya to Markandeya's vision and the glory of the Bhagavatam, then starts again. Stories are available in English, Malayalam, Tamil, Telugu, Kannada and Hindi. It is the second app in the series, after Ramayanam Wallpapers (com.calecutech.ramayanam); package `com.calecutech.bhagavatham`.

## What it does

- **Day one is incident one.** The story starts on the day you install it and moves forward one incident a day. "Start again from the first incident" in Settings resets it.
- **Daily change.** A background job runs every couple of hours and changes the wallpaper on its first run after midnight. It survives reboots. Opening the app also applies today's wallpaper straight away.
- **The wallpaper** shows the painting in a thin gold frame over a softened copy of itself, with the skandha, title and story in a dark band at the bottom. You can turn the story text off, and choose home screen, lock screen or both.
- **Languages.** The stories and screens follow the phone's language when it is one of English, Malayalam, Tamil, Telugu, Kannada or Hindi, and English otherwise. Settings > Language lets you pick one.
- **Browse.** Swipe, Previous / Next and All incidents let you read ahead or back and set any incident as the wallpaper.
- **Links.** The bottom of the main screen links to https://calecutech.com and the privacy policy at https://calecutech.com/bhagavatham/privacy.html.
- **No location or personal data.** Permissions: set wallpaper, run at startup, internet (only for a missing painting).

## Images and attribution

Every incident has a painting bundled at `app/src/main/assets/paintings/<id>.jpg` (1000 px wide), so the app works offline. They come from Wikimedia Commons: Bhagavata Purana folios (Pahari, Kangra, Guler, Rajasthani and others), Raja Ravi Varma and the Ravi Varma Press, and other public-domain or freely licensed paintings; `image`, `artist` and `license` in `incidents.json` record each one and the app shows the credit and licence under the story.

`tools/find_images.py` collected them. It runs on GitHub Actions (`.github/workflows/bhagavatham-images.yml`; the build machine can't reach Wikimedia). `MODE=candidates` searches Commons for each incident by the `names` and `scene` fields and saves up to four candidates plus contact sheets; each pick was then checked by eye (no photos, statues, temple images or other epics) and listed in `tools/picks.json`; `MODE=fetch` downloads the chosen files. The match is by characters and scene, so some paintings show a neighbouring moment of the story rather than the exact incident.

## Project layout

```
app/src/main/
  assets/incidents.json      the 318 incidents in English: skandha (key "kanda"), title, story, names, scene, Commons file, artist, licence
  assets/i18n/<lang>.json    translations (ml, ta, te, kn, hi) keyed by incident id
  assets/paintings/<id>.jpg  the bundled paintings
  java/com/calecutech/bhagavatham/
    MainActivity.kt          the screen: preview, story, browse, settings
    WallpaperRenderer.kt     draws the wallpaper bitmap (12 skandha colour sets for the drawn fallback art)
    WallpaperSetter.kt       applies it, once per day
    DailyWallpaperJob.kt     background job + reboot receiver
    ImageStore.kt            loads bundled paintings (downloads any missing one)
    Incidents.kt, Prefs.kt   data and settings
    Lang.kt                  language choice
  res/                       layout, colours, launcher icon (a flute with a peacock feather)
```

The app uses only the Android framework and the Kotlin standard library, with no other libraries.

## Translations

The English stories were written from the Srimad Bhagavatam, and the five translations were machine-written from the English. Please have a native speaker read through each language before relying on them. Missing or empty entries fall back to English, so fixes can be made one entry at a time in `assets/i18n/<lang>.json`.

## Building

**Android Studio:** open this folder and run it. It uses the Gradle wrapper (Gradle 8.14.3, Android Gradle Plugin 8.11.1, Kotlin 2.2.21, compile/target SDK 36, min SDK 26). This Gradle setup was not run on the build machine (Google's Maven repository was blocked there), so if Android Studio suggests a different AGP version, accept it.

**Without Gradle:** `build-local.sh` builds the APK with just the SDK build tools, a JDK and the Kotlin compiler jars. This is how the attached APK was built:

```
ANDROID_HOME=/path/to/sdk KOTLIN_LIB=/path/to/kotlin-compiler-jars ./build-local.sh
```

**Release bundle for Google Play:** `build-local.sh` also builds a signed `.aab` when given bundletool and the upload key:

```
BUNDLETOOL=bundletool.jar UPLOAD_KEYSTORE=upload.jks UPLOAD_ALIAS=upload UPLOAD_PASS=... ./build-local.sh
```

Raise `VERSION_CODE` in `build-local.sh` (and `versionCode` in `app/build.gradle.kts`) for every upload. The upload key is not in this repository; keep it backed up. Store graphics and listing text are in `store/`, the privacy policy in `PRIVACY.md`, and the website pages for calecutech.com in `website/bhagavatham/`.

## Next app in the series

To make the Mahabharatham app, copy this project, change `applicationId`/`namespace`, the app name and `about` strings, the palettes in `WallpaperRenderer.kt` and the centre name in `Lang.kt`, and replace `incidents.json`, `assets/i18n/` and `assets/paintings/`.
