# Roadmap to an "AAA" app

What Contacto still needs to become a polished, store-quality app. The visual
identity, animations and translations are already strong; the gaps are mostly
in release engineering, reliability, testing and accessibility.

Work through the phases in order.

## Phase 1 — Ship-blockers

- [ ] **Application ID**: replace `com.example.recuerdallamar`. Google Play
      rejects `com.example` packages, and the ID can never change after the
      first upload.
- [ ] **Release signing**: add a signing config (keystore via CI secrets).
- [ ] **R8**: enable `isMinifyEnabled` and `isShrinkResources` for release, with
      ProGuard rules for Room and WorkManager.
- [ ] **Versioning**: derive `versionCode`/`versionName` automatically (e.g.
      from git tags or the CI run number).
- [ ] **Release in CI**: build and upload a signed release AAB, not only the
      debug APK.
- [ ] **Hide debug tools** in release builds: the "force notification" button
      in the contact screen and *Settings › Debug › App language*.
- [ ] **Privacy policy** and Play **Data Safety** form (the app reads
      contacts).
- [ ] **Store listing**: localized description, screenshots and feature
      graphic.

## Phase 2 — Quality safety net

- [ ] **Unit tests** (`src/test`): urgency formula, quiet hours crossing
      midnight, snooze/pause logic, contact-method URL building.
- [ ] **Room schema export** (`exportSchema = true`) and **migration tests**
      for 1→2 and 2→3.
- [ ] **Worker tests** with `work-testing`.
- [ ] **UI tests** (`src/androidTest`) for the main flows: onboarding, add
      contact, open contact screen, settings.
- [ ] **CI**: run tests and lint *before* publishing artifacts; fail the build
      on errors.
- [ ] **Crash reporting** (Crashlytics or Sentry) and basic structured
      logging; handle errors beyond `ActivityNotFoundException`.
- [ ] **Performance**: Baseline Profiles, startup and jank benchmarks
      (Macrobenchmark); pause the infinite animations when off-screen or idle
      to save battery.

## Phase 3 — Reminder reliability and user data

- [ ] **Single daily scheduler** instead of one periodic worker per contact,
      aimed at the start of the user's quiet-hours window so reminders don't
      drift by hours.
- [ ] **OEM battery restrictions**: detect aggressive battery optimization
      (Xiaomi, Samsung, Huawei…) and guide the user to exempt the app.
- [ ] **Automatic contact detection** (optional): read the call log, with
      permission, so calls made outside the app count too.
- [ ] **Backup and restore**: Android Auto Backup rules
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
- [ ] **Touch targets and labels**: audit the 12 `contentDescription = null`
      icons and make sure every interactive element is at least 48dp.

## Phase 5 — Platform polish and product depth

- [x] **Splash screen** via the SplashScreen API.
- [x] **Large screens**: tablet/foldable layouts using window size classes.
- [x] **Navigation**: move to Navigation Compose for deep links, predictive
      back and robust process-death restoration.
- [x] **Home-screen widget** ("who's due today") and a launcher shortcut for
      "add person".
- [x] **Notifications**: grouping when several people are due, channels per
      importance.
- [x] **Richer haptics** for grabbing, throwing and colliding bubbles.
- [ ] **Product features**: history and streaks/stats, ~~notes per person~~,
      ~~birthdays from contacts~~, ~~groups/circles~~ (done; history and
      streaks/stats still to do).
- [x] **In-app feedback** and rating prompt.
- [ ] **Optional sync** across devices (keeping local-only as the default and
      privacy selling point).
