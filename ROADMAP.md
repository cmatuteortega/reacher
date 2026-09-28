# Roadmap to an "AAA" app

What Contacto still needs to become a polished, store-quality app. The visual
identity, animations and translations are already strong; the gaps are mostly
in release engineering, reliability, testing and accessibility.

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
- [ ] **Placeholder contact email**: replace `CORREO_DE_CONTACTO` in
      `docs/privacy.html` and `docs/privacidad.html`.

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
- [ ] **Performance**: Baseline Profile and startup/frame benchmarks are set
      up in `:baselineprofile` (manual *Rendimiento* workflow); **still to
      run it once and commit the generated profile**. Infinite animations use
      `withInfiniteAnimationFrameNanos` and already stop in the background
      (Compose pauses the frame clock on `ON_STOP`); pausing the bubbles when
      they are at rest is still to do.
- [ ] **Release build smoke test**: R8 is only checked by hand today
      (`PUBLICAR.md`). Run the main flows against the minified build in CI,
      or at least keep a written checklist before each upload.
- [ ] **Translation coverage**: 5 languages; fail lint on
      `MissingTranslation` so no string ships only in English.

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

## Phase 4 — Accessibility

- [ ] **TalkBack in the bubble view**: expose each person as an accessible
      node with name, urgency and actions (open, mark as contacted).
- [ ] **Reduced motion**: respect the system "Remove animations" setting and
      calm the ambient animations (faces, halos, sun, physics).
- [ ] **Font scaling**: verify layouts at 200% text size.
- [ ] **Contrast**: check the palette (especially teja on crema) against
      WCAG AA in light and dark themes.
- [ ] **Touch targets and labels**: audit the 16 `contentDescription = null`
      icons and make sure every interactive element is at least 48dp.

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
- [ ] **Release notes**: `fastlane/metadata/android/<lang>/changelogs/`, one
      file per `versionCode`, in every language.
- [ ] **Dependency updates**: Dependabot or Renovate for Gradle and GitHub
      Actions, so SDKs and the target API don't fall behind again.

## Later

Moved out of phase 5 so it could be closed; not scheduled yet.

- [ ] **History and streaks/stats** per person.
- [ ] **Optional sync** across devices (keeping local-only as the default and
      privacy selling point).
