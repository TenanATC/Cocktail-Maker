# Cocktail Maker 🍸

An Android app that turns a photo of your home bar into cocktail suggestions.
Snap a picture of your spirits, mixers, fruit, and bitters — the app recognizes
what's in the shot and matches it against an **offline dictionary of 60+
cocktail recipes**, complete with smart ingredient substitutions.

## How it works

1. **Photograph your ingredients** (or pick a photo from the gallery, or select
   ingredients by hand — no camera required).
2. The photo is sent to **Clarifai's free image-recognition API** (the
   `general-image-recognition` and `food-item-recognition` community models),
   and the returned labels are mapped to canonical ingredients through an
   alias dictionary (`"whisky"` → Bourbon, `"limes"` → Lime Juice, …).
3. You can review and edit the detected list — recognition of specific bottles
   is never perfect, so every detection is just a pre-filled suggestion.
4. The **offline matching engine** scores every recipe in the bundled
   dictionary:
   - required ingredients you have count fully;
   - missing ingredients that have a documented substitution on hand count
     partially (e.g. no lime? lemon works — with a note about how the drink
     changes);
   - garnishes and optional ingredients never count against you.
   Results are split into **"Ready to pour"** and **"Almost there"** (missing
   at most two things), ranked by match quality.

Only step 2 needs the network. Recipes, matching, substitutions, and browsing
all work fully offline.

## Getting a (free) API key

Ingredient recognition uses Clarifai's community tier, which is free
(currently 1,000 API calls/month) and needs no credit card:

1. Create an account at [clarifai.com](https://www.clarifai.com/).
2. In your account's **Security** settings, create a **Personal Access Token**.
3. Either paste it into the app's **Settings** dialog (stored only on-device),
   or put `clarifai.pat=YOUR_TOKEN` in `local.properties` before building so it
   ships as the default key in the APK.

Without a key the app still works — use **"Pick ingredients by hand"** and the
offline recipe browser.

## Building

Requirements: JDK 17+, Android SDK (compileSdk 35).

```bash
./gradlew :app:assembleDebug        # build the APK
./gradlew :app:testDebugUnitTest    # run the unit tests
```

The APK lands in `app/build/outputs/apk/debug/`. Minimum Android version: 8.0
(API 26).

## Project layout

```
app/src/main/assets/
  ingredients.json   # canonical ingredients, vision-label aliases, pantry
                     # staples, and substitution rules with tasting notes
  cocktails.json     # the offline recipe book (60+ drinks)
app/src/main/java/com/tenanatc/cocktailmaker/
  data/              # models, JSON parsing, and the RecipeMatcher engine
  vision/            # Clarifai client, image scaling/encoding, label→ingredient mapper
  ui/                # Jetpack Compose screens (home, ingredients, results, detail, browse)
  AppViewModel.kt    # app state + navigation back-stack
  MainActivity.kt    # single-activity Compose host, camera/gallery launchers
app/src/test/        # JVM unit tests: dictionary integrity, matcher, label mapping
```

## Extending the recipe book

Both dictionaries are plain JSON assets — add a recipe or a substitution rule
and the matcher picks it up automatically. The unit tests validate referential
integrity (every recipe ingredient and substitution must exist in the
ingredient dictionary), so `./gradlew test` will catch typos.
