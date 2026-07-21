# Cocktail Maker 🍸

An Android app that turns a photo of your home bar into cocktail suggestions.
Snap a picture of your spirits, mixers, fruit, and bitters — the app recognizes
what's in the shot and matches it against an **offline dictionary of 60+
cocktail recipes**, complete with smart ingredient substitutions.

## How it works

1. **Photograph your ingredients** (or pick a photo from the gallery, or select
   ingredients by hand — no camera required).
2. Recognition (primary path — **Google Gemini vision**):
   - The photo is sent to Google's **Gemini** multimodal model, which reads
     stylized bottle labels and *reasons* about what each item is — returning
     the canonical ingredient (e.g. a Tapatío 110 Blanco bottle → **Tequila**),
     plus the brand name and a quality tier where the label is legible. This is
     far more robust than OCR keyword-matching, which stumbles on script fonts
     and curved glass. Get a free key at
     [aistudio.google.com](https://aistudio.google.com/) and paste it in
     Settings.
   - **Offline fallback** (used only when no Gemini key is set): **ML Kit OCR**
     reads label text on-device and matches it against a bundled **brand catalog
     with quality tiers**, alongside the optional legacy Clarifai models. This
     path is brittle on decorative labels — Gemini is strongly recommended.
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

Only the recognition step (step 2) needs the network. Recipes, matching,
substitutions, browsing, and manual ingredient picking all work fully offline.

When Gemini reads a brand it doesn't just name it — it assigns a quality tier,
so the same "don't mask the good stuff" guidance (below) works for any bottle,
not only the ones in the bundled catalog.

## Bottle quality — "don't mask the good stuff"

Every recipe is classified by how much it exposes its base spirit:

- **Showcase** — stirred and boozy (Old Fashioned, Negroni, Martini): the
  spirit *is* the drink.
- **Balanced** — citrus sours and highballs (Margarita, Daiquiri, G&T):
  quality still reads.
- **Masked** — big juice, cola, cream, or coffee (Tequila Sunrise, White
  Russian): nobody can taste your top shelf in there.

The brand catalog (`brands.json`, 100+ bottles) tiers each brand as
**premium / mid / value**. When OCR identifies your bottles, the results get
advice and a gentle re-ranking:

- premium bottle + showcase drink → *"A perfect stage for your G4."* (boosted)
- premium bottle + masked drink → *"Big mixers will bury your G4 — pour the
  Jose Cuervo here instead."* (demoted; names your value bottle if one was
  in the photo)
- value bottle + showcase drink → *"This drink puts the tequila front and
  center — Jose Cuervo Especial will show its edges."*
- value bottle + masked drink → *"A great spot for the Jose Cuervo."*

The nudge only reorders suggestions — availability scores never change, and
mid-tier bottles are left in peace.

## Matching styles, riffs, and the shopping list

**Matching style** (chosen on the ingredients screen, remembered between runs):

- **Strict** — curated substitutions only, by the book.
- **Flexible** — adds same-family fallbacks at a lower match weight: any
  whiskey can stand in for any other whiskey (likewise rum, agave, citrus,
  syrup, and bitters families), clearly labeled as a bigger flavor gamble.
- **Adventurous** — family fallbacks plus looser gates (up to 3 missing
  ingredients, one-third coverage), for when you want ideas rather than rules.

**Off-menu riffs**: when your shelf fits a time-tested formula but no recipe
in the book uses exactly those bottles, the app invents one — sour
(2 : ¾ : ¾), highball, old-fashioned (spirit/sugar/bitters), and spritz
(2 : 3 : 1) templates. Riffs are clearly labeled, ranked below real recipes,
never duplicate the book (or a classic you can already make), respect a
pairing veto list (no gin & cola), refuse unaged spirits in the
old-fashioned template, and showcase your premium bottle when one was
recognized.

**One bottle away**: pure shopping-list math — for every ingredient you don't
have, the app counts which recipes it would unlock right now and suggests the
highest-impact bottles ("Sweet Vermouth — unlocks Manhattan, Negroni…"), with
a one-tap "Have it" to add it and refresh.

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
  brands.json        # bottle brands with quality tiers and OCR keywords
app/src/main/java/com/tenanatc/cocktailmaker/
  data/              # models, JSON parsing, RecipeMatcher engine, BrandDetector
  vision/            # Clarifai client, ML Kit OCR, image utils, label→ingredient mapper
  ui/                # Jetpack Compose screens (home, ingredients, results, detail, browse)
  AppViewModel.kt    # app state + navigation back-stack
  MainActivity.kt    # single-activity Compose host, camera/gallery launchers
app/src/test/        # JVM unit tests: dictionary integrity, matcher, brands, label mapping
```

## Extending the recipe book

Both dictionaries are plain JSON assets — add a recipe or a substitution rule
and the matcher picks it up automatically. The unit tests validate referential
integrity (every recipe ingredient and substitution must exist in the
ingredient dictionary), so `./gradlew test` will catch typos.
