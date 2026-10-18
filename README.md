# Ramayanam Wallpapers

An Android app that changes your wallpaper every day to the next incident of the Ramayana, with a short story of that incident. It walks through 310 incidents in order, kanda by kanda (Bala 40, Ayodhya 55, Aranya 40, Kishkindha 35, Sundara 40, Yuddha 70, Uttara 30), from Narada's summary to Rama's last journey into the Sarayu, then starts again. Stories are available in English, Malayalam, Tamil, Telugu, Kannada and Hindi. It is the first app in a planned series (Ramayanam, Bhagavatham, Mahabharatham).

## Code shrinking

Release builds run R8 (`app/proguard-rules.pro`; in build-local.sh it comes from build-tools' d8.jar) and write `build-local/mapping.txt`. Upload that mapping with every bundle (tools/play_publish.py does it) so Play can de-obfuscate crash reports.

## Install

`ramayanam-wallpapers.apk` in this folder is a debug-signed APK. Copy it to an Android 8.0+ phone, open it, and allow "install unknown apps" when asked.

## What it does

- **Day one is incident one.** The story starts on the day you install it and moves forward one incident a day. "Start again from the first incident" in Settings resets it.
- **Daily change.** A background job runs every couple of hours and changes the wallpaper on its first run after midnight. It survives reboots. Opening the app also applies today's wallpaper straight away.
- **The wallpaper** shows the painting in a thin gold frame over a softened copy of itself, with the kanda, title and story in a dark band at the bottom. You can turn the story text off, and choose home screen, lock screen or both.
- **Languages.** The stories and screens follow the phone's language when it is one of English, Malayalam, Tamil, Telugu, Kannada or Hindi, and English otherwise. Settings > Language lets you pick one.
- **Browse.** Previous / Next / All incidents let you read ahead or back and set any incident as the wallpaper.
- **Offline.** Each painting is downloaded once and kept on the phone. Tomorrow's painting is fetched a day early. If a painting can't be downloaded, or the incident has none, the app draws its own artwork: a gold lotus mandala with "Sri Rama" in the chosen script, with its own colours for each kanda.

## Images and attribution

Every incident has a painting, bundled in the app at `app/src/main/assets/paintings/<id>.jpg` (1000 px wide), so the app works offline. They come from Wikimedia Commons: Raja Ravi Varma and the Ravi Varma Press, the Mewar Ramayana, Pahari, Kangra and Mughal folios, Gita Press prints and others. 293 are public domain or CC0; 17 are CC BY or CC BY-SA, and the app shows their licence in the credit line.

`tools/find_images.py` picked them. It runs on GitHub Actions (push to the `image-research` branch), searches Commons for each incident by the names in its title and story, keeps only free licences, and downloads the result. Every pick was then checked by eye: photos, sculptures, puppets, blank pages and paintings from other epics were replaced. 25 incidents had no good match and reuse a painting from a nearby incident in the same kanda, so there are 285 different paintings. The match is by characters and scene, so some paintings show a neighbouring moment of the story rather than the exact incident. `tools/out/images.json` records each pick with its Commons page.

To change a painting, replace `paintings/<id>.jpg` and update `image`, `artist` and `license` for that incident in `incidents.json`.

## Project layout

```
app/src/main/
  assets/incidents.json      the 310 incidents in English: kanda, title, story, Commons file, artist
  assets/i18n/<lang>.json    translations (ml, ta, te, kn, hi) keyed by incident id
  java/com/calecutech/ramayanam/
    MainActivity.kt          the screen: preview, story, browse, settings
    WallpaperRenderer.kt     draws the wallpaper bitmap
    WallpaperSetter.kt       applies it, once per day
    DailyWallpaperJob.kt     background job + reboot receiver
    ImageStore.kt            loads bundled paintings (downloads any missing one)
    Incidents.kt, Prefs.kt   data and settings
    Lang.kt                  language choice
  res/                       layout, colours, launcher icon (a bow and arrow)
```

The app uses only the Android framework and the Kotlin standard library, with no other libraries.

## Translations

The English stories were written from Valmiki's Ramayana, and the five translations were machine-written from the English. Please have a native speaker read through each language before publishing. Missing or empty entries in a translation fall back to English, so fixes can be made one entry at a time in `assets/i18n/<lang>.json`. To add a language, add an entry in `Lang.kt`, a `res/values-<code>/strings.xml` and `assets/i18n/<code>.json`.

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

Raise `VERSION_CODE` in `build-local.sh` (and `versionCode` in `app/build.gradle.kts`) for every upload. The upload key is not in this repository; keep it backed up. Store graphics and listing text are in `store/`, the privacy policy in `PRIVACY.md`, and the website pages for calecutech.com in `website/ramayanam/`.

## Next apps in the series

To make the Bhagavatham or Mahabharatham app, copy this project, change `applicationId`/`namespace` and the app name, and replace `incidents.json` and the files in `assets/i18n/`. The rest of the code doesn't depend on the Ramayana, except the "श्री राम" text in the fallback artwork (`WallpaperRenderer.drawFallbackArt`).
