# Ramayanam Wallpapers

An Android app that changes your wallpaper every day to the next incident of the Ramayana, with a short story of that incident. It walks through 310 incidents in order, kanda by kanda (Bala 40, Ayodhya 55, Aranya 40, Kishkindha 35, Sundara 40, Yuddha 70, Uttara 30), from Narada's summary to Rama's last journey into the Sarayu, then starts again. Stories are available in English, Malayalam, Tamil, Telugu, Kannada and Hindi. It is the first app in a planned series (Ramayanam, Bhagavatham, Mahabharatham).

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

Paintings come from Wikimedia Commons and are loaded on the phone the first time they are needed (`Special:FilePath`, resized to 1600 px wide). Raja Ravi Varma died in 1906, so his paintings and the Ravi Varma Press prints are in the public domain. Every entry in `app/src/main/assets/incidents.json` names its Commons file, and the app links to the file page under each story.

26 of the 310 incidents have an image. The rest use the built-in artwork until a suitable public-domain image is added: set `"image"` to the Commons file name (without `File:`) and `"artist"` if known. The Mewar (Jagat Singh) Ramayana at the British Library and other pre-1900 Pahari paintings on Commons are good sources.

The file names were found through web search, because Wikimedia was not reachable from the build machine. Before publishing on the Play Store, open each Commons link once to confirm the image loads and its licence page says public domain. Images to check first: `Sita_in_the_Garden_of_Lanka.jpg` (thought to be the Cleveland Museum of Art Pahari painting) and the LACMA Ramayana folio were added from search results without seeing their Commons pages. Several images have no confirmed artist, so the app credits them only as "Wikimedia Commons (public domain)".

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
    ImageStore.kt            downloads and caches paintings
    Incidents.kt, Prefs.kt   data and settings
    Lang.kt                  language choice
  res/                       layout, colours, launcher icon (a bow and arrow)
```

The app uses only the Android framework and the Kotlin standard library, with no other libraries.

## Translations

The English stories were written from Valmiki's Ramayana, and the five translations were machine-written from the English. Please have a native speaker read through each language before publishing. Missing or empty entries in a translation fall back to English, so fixes can be made one entry at a time in `assets/i18n/<lang>.json`. To add a language, add an entry in `Lang.kt`, a `res/values-<code>/strings.xml` and `assets/i18n/<code>.json`.

## Building

**Android Studio:** open this folder and run it. It uses the Gradle wrapper (Gradle 8.14.3, Android Gradle Plugin 8.11.1, Kotlin 2.2.21, compile/target SDK 35, min SDK 26). This Gradle setup was not run on the build machine (Google's Maven repository was blocked there), so if Android Studio suggests a different AGP version, accept it.

**Without Gradle:** `build-local.sh` builds the APK with just the SDK build tools, a JDK and the Kotlin compiler jars. This is how the attached APK was built:

```
ANDROID_HOME=/path/to/sdk KOTLIN_LIB=/path/to/kotlin-compiler-jars ./build-local.sh
```

For a Play Store release, create your own signing key and build a signed release bundle in Android Studio.

## Next apps in the series

To make the Bhagavatham or Mahabharatham app, copy this project, change `applicationId`/`namespace` and the app name, and replace `incidents.json` and the files in `assets/i18n/`. The rest of the code doesn't depend on the Ramayana, except the "श्री राम" text in the fallback artwork (`WallpaperRenderer.drawFallbackArt`).
