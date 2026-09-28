# Roadmap to an "AAA" app

What Contacto still needs to become a polished, store-quality app. The visual
identity, animations, translations and accessibility are already strong; the
gaps left are mostly in release engineering (store listing, closed testing,
upload from CI) and a few quality checks.

Work through the phases in order.

## Phase 1 — Ship-blockers

Done in code; the manual steps (keystore, CI secrets, GitHub Pages, contact
email, screenshots) are in [PUBLICAR.md](PUBLICAR.md).

- [x] **Application ID**: replace `com.example.recuerdallamar`. Google Play
      rejects `com.example` packages, and the ID can never change after the
      first upload.
- [x] **Release signing**: add a signing config (keystore via CI secrets).
- [x] **R8**: enable `isMinifyEnabled` and `isShrinkResources` for release, with
      ProGuard rules for Room and WorkManager.
- [x] **Versioning**: derive `versionCode`/`versionName` automatically (e.g.
      from git tags or the CI run number).
- [x] **Release in CI**: build and upload a signed release AAB, not only the
      debug APK.
- [x] **Hide debug tools** in release builds: the "force notification" button
      in the contact screen and *Settings › Debug › App language*.
- [x] **Privacy policy** and Play **Data Safety** form (the app reads
      contacts).
- [ ] **Store listing**: localized description, screenshots and feature
      graphic. Texts are done (`fastlane/metadata`); screenshots and the
      feature graphic are still to do.
- [x] **Target API 36**: Play requires new apps and updates to target the
      Android release from the year before (API 36 from 31 August 2026).
      `compileSdk`/`targetSdk` 36 with AGP 8.9. Check the insets on every
      screen on an Android 16 phone: edge-to-edge can no longer be turned off.
- [ ] **Closed testing**: personal developer accounts need a closed test
      with at least 12 testers for 14 days before Play allows production.
- [x] **Contact email** in `docs/privacy.html` and `docs/privacidad.html`:
      `cmatuteortega@gmail.com`.

## Phase 2 — Quality safety net

Tests run on the JVM with Robolectric (no emulator): `./gradlew testDebugUnitTest`.

- [x] **Unit tests** (`src/test`): urgency formula, quiet hours crossing
      midnight, snooze/pause logic, contact-method URL building.
- [x] **Room schema export** (`exportSchema = true`) and **migration tests**
      for every step, 1→5, against the real schemas of each version.
- [x] **Worker tests** with `work-testing`: when a reminder shows and when it
      stays silent, quiet hours, "Later" and dismissals.
- [x] **UI tests** for the main flows: onboarding, open a contact and log a
      call, settings, the statistics prompt; adding someone at ViewModel
      level (the contact picker is system UI). Robolectric instead of
      `src/androidTest`, so CI needs no emulator.
- [x] **CI**: run tests and lint *before* publishing artifacts; fail the build
      on errors.
- [x] **Crash reporting** (Sentry, on by default with an opt-out) and basic
      structured logging (`Registro`); errors beyond `ActivityNotFoundException`
      (backup, worker, `SecurityException` when calling) are reported.
- [x] **Product analytics** (PostHog EU, opt-in, anonymous events).
- [ ] **Performance**: Baseline Profile and startup/frame benchmarks in
      `:baselineprofile` (manual *Rendimiento* workflow, which now commits
      the generated profile to the branch it runs on); **still to check the
      first run and that the profile is in `app/src/release/generated/`**.
      Infinite animations use `withInfiniteAnimationFrameNanos` and stop in
      the background (Compose pauses the frame clock on `ON_STOP`). The
      bubble physics stops asking for frames once everything is still
      (`Simulacion.reposo()`) and wakes on a new layout, a drag or a throw;
      with the normal gentle float that never happens by design, so today it
      only saves work under reduced motion.
- [x] **Release build smoke test**: the *Humo* workflow runs the main flows
      (welcome, the three views, settings, reopen) on the R8-minified build
      in an emulator on every push to `main` and every `v*` tag, and fails on
      any crash. What an emulator can't do (contact picker, real reminders)
      is a written checklist in `PUBLICAR.md`.
- [x] **Translation coverage**: 5 languages; lint fails on
      `MissingTranslation` (explicit in `app/build.gradle.kts`), so no
      string ships only in English.

## Phase 3 — Reminder reliability and user data

- [x] **OEM battery restrictions**: detect aggressive battery optimization
      (Xiaomi, Samsung, Huawei…) and guide the user to exempt the app.
- [ ] ~~**Automatic contact detection** (optional): read the call log, with
      permission, so calls made outside the app count too.~~ Skipped for now:
      Google Play only grants `READ_CALL_LOG` to default phone/SMS apps and a
      few exceptions, so it would block the Play release.
- [x] **Backup and restore**: Android Auto Backup rules
      (`dataExtractionRules` / `fullBackupContent`), plus JSON export/import
      for switching phones.

## Phase 4 — Accessibility ✅

Checked by `AccesibilidadTest`, `ContrasteTest` and `MovimientoReducidoTest`.

- [x] **TalkBack in the bubble view**: each person (bubbles and orbits) is one
      node with name and urgency, read in urgency order even while floating,
      with "open" and an "I called today" custom action.
- [x] **Reduced motion**: with the system "Remove animations" setting, faces
      stop blinking and swaying, the sun stops breathing and turning, the
      bubbles float still and the orbits stop turning. Dragging and throwing
      still move; Compose animations follow the system scale on their own.
- [x] **Font scaling**: every screen checked at 200% (list, bubbles, orbits,
      contact, settings, welcome); every tap target stays at least 48dp.
      Bubble names under the circle are cut with "…" by design; TalkBack
      reads the full name.
- [x] **Contrast**: every text/background pair passes WCAG AA in both themes.
      Teja stays for the sun and halos, with a darker `acentoLegible`
      (#A4562E) for text and icons in light; dark `secondaryContainer`
      darkened a little; the widget uses the card colour as background.
      Still below 3:1: the teja "until" handle of the reminder-hours dial
      against the light background (2.8:1), kept for the brand; it has its
      hour inside and a TalkBack alternative.
- [x] **Touch targets and labels**: the 16 `contentDescription = null` icons
      are all decorative (next to a text label or inside a node that already
      has one). A test walks list, contact, settings and welcome and fails on
      any tappable element without a name or under 48dp.

## Phase 5 — Platform polish and product depth ✅

- [x] **Splash screen** via the SplashScreen API.
- [x] **Large screens**: tablet/foldable layouts using window size classes.
- [x] **Navigation**: move to Navigation Compose for deep links, predictive
      back and robust process-death restoration.
- [x] **Home-screen widget** ("who's due today") and a launcher shortcut for
      "add person".
- [x] **Notifications**: grouping when several people are due, channels per
      importance.
- [x] **Richer haptics** for grabbing, throwing and colliding bubbles.
- [x] **Product features**: notes per person, birthdays from contacts,
      groups/circles.
- [x] **In-app feedback** and rating prompt.

## Phase 6 — Release process

- [ ] **Upload from CI**: publish the signed AAB to an internal/closed track
      with fastlane `supply` or the Play Developer API, instead of by hand.
- [ ] **Staged rollouts** for production (e.g. 10% → 50% → 100%), watching
      Sentry before each step.
- [x] **Release notes**: `fastlane/metadata/android/<lang>/changelogs/`
      (`default.txt`, or `<versionCode>.txt` for a specific build) in every
      language; CI checks every language has the same files and none is
      over Play's 500 characters.
- [x] **Dependency updates**: Dependabot for Gradle (grouped: Kotlin+KSP,
      AndroidX, AGP, telemetry, tests) and GitHub Actions, weekly.

## Later

Moved out of phase 5 so it could be closed; not scheduled yet.

- [ ] **History and streaks/stats** per person.
- [ ] **Optional sync** across devices (keeping local-only as the default and
      privacy selling point).
