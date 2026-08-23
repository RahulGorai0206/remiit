# Remiit

Rule-based reminders for Android. A reminder is paired with the conditions that
should trigger it — a time, a Wi-Fi network, arriving somewhere, opening an app —
and, separately, with how loudly it announces itself.

Targets Android 13 (API 33) and above. Distributed as sideloaded APKs.

## How a rule works

A rule is one self-contained document: triggers, delivery config and constraints
are stored as JSON columns on a single row. Adding a trigger kind needs no schema
migration, and a future on-device model can emit a whole rule in one step.

**Triggers** — any combination of:

| Kind | Fires on | Mechanism |
|---|---|---|
| Time | daily / weekly / monthly / every-N-within-a-window / one-shot | `AlarmManager`, re-armed per fire |
| Wi-Fi | connecting to or leaving a named SSID | live `ConnectivityManager` callback |
| Place | entering, leaving or dwelling in a radius | Play Services geofences |
| App launch | any app, or specific ones | accessibility service *or* usage-stats polling |

`Any trigger` fires on the first match. `All triggers` requires every one within
a bounded window, which is what makes "on office Wi-Fi **and** after 3pm" work.

**Delivery** — notification, full-screen banner, or alarm (looping tone on the
alarm stream, so it ignores media volume). Each carries Complete / Not done, and
every firing and response is logged.

**Constraints** — cooldown, max per day, quiet hours, active days, validity
range. These are why "remind me on any app launch" is usable rather than a
firehose.

## App-launch detection: pick one

Android has no ordinary API for noticing another app opened. Both routes work and
trade off differently; the choice is in Settings.

- **Accessibility service** — instant, almost no battery cost. Reads only which
  package came to the foreground (the service config grants no content
  retrieval). Needs an accessibility grant, and Google Play prohibits this use,
  which is fine for sideloaded builds.
- **Usage access** — Play-policy-safe, but polls once a second, so it lags and
  keeps a foreground service alive.

## Why so many permissions

Every one of these fails *silently* — the rule looks saved and enabled and simply
never fires. The in-app Permissions screen shows live grant state for each with
the consequence spelled out.

- **Notifications** — nothing can be shown without it.
- **Exact alarms** — without it, time reminders may be delayed minutes.
- **Full-screen intent** — Android 14+ denies this by default for non-calling
  apps; without it, banner and alarm modes collapse into ordinary notifications.
- **Location (fine + background)** — needed for place rules, *and* to read a
  Wi-Fi SSID at all. Without it the platform returns `<unknown ssid>` and Wi-Fi
  rules never match.
- **Location services on** — a device-wide switch that breaks both regardless of
  permissions.
- **Battery optimisation exemption** — the usual reason reminders work for a day
  and then stop.

## Building

JDK 17. `local.properties` needs `sdk.dir`.

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

Release builds are minified with R8 and split per ABI, producing
`app-arm64-v8a-release.apk` and `app-armeabi-v7a-release.apk`:

```bash
./gradlew :app:assembleRelease
```

Without signing material this produces `*-unsigned.apk` rather than failing, so
R8 and the splits stay verifiable locally. To sign locally, add to
`local.properties`:

```
RELEASE_STORE_FILE=/path/to/keystore.jks
RELEASE_STORE_PASSWORD=…
RELEASE_KEY_ALIAS=…
RELEASE_KEY_PASSWORD=…
```

> The two ABI APKs are currently byte-identical in content — there is no native
> code yet. The split is configured because the planned on-device AI runtime
> ships `.so` libraries.

## Releasing

Push a `v*` tag. `.github/workflows/release.yml` builds, signs, renames the APKs
to `app-v<version>-<abi>.apk` and attaches them to a GitHub release. `versionName`
comes from the tag; `versionCode` is computed from it.

Required repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`, and a `release` environment.

`cache-warmup.yml` runs on `main` to populate the Gradle cache that the
tag-triggered release build reads — a cache saved under one tag ref is
unreachable from the next.

## Verifying on a device

Unit tests cover the logic whose failures are silent and delayed: next-fire for
every recurrence (day-31 clamping into February, both DST directions), quiet
hours spanning midnight, match-window expiry, cooldown and daily-cap gating, and
JSON round-trips pinning the discriminator strings saved rows depend on.

The rest needs hardware:

```bash
adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

1. Walk the Permissions screen until every card is green.
2. One rule per delivery mode, one minute out — confirm a shade notification, a
   screen-waking banner, and a looping alarm with media volume at zero.
3. Toggle Wi-Fi off and on for a Wi-Fi rule.
4. Walk in and out of a small radius for a place rule.
5. Open another app for an app-launch rule — test **both** detectors.
6. Check Complete / Not done land in History.
7. Reboot and confirm alarms re-arm.

**Run this against the release APK, not just debug.** The failure mode for a
missing R8 keep is an app that installs and runs fine but silently stops firing.

## Not built yet

On-device AI rule creation from chat or voice. The seam is in place:
`ai/RuleIntentParser` with the JSON schema a model must emit documented beside
it, and a regex stub keeping the call sites exercised. Wiring MediaPipe
`tasks-genai` should touch nothing outside `ai/`.
