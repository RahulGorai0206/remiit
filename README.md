# Remiit

Rule-based reminders for Android. A reminder is paired with the conditions that
should trigger it — a time, a Wi-Fi network, arriving somewhere, opening an app —
and, separately, with how loudly it announces itself.

The same surroundings can also change the phone itself: **automations** put it on
silent, vibrate, ring or Do Not Disturb, and turn adaptive brightness on or off,
when you join a network, connect a Bluetooth device, or arrive somewhere.

Targets Android 13 (API 33) and above. Distributed as sideloaded APKs.

## Icon

A bell with a check mark cut out of it as negative space, on the app's own
primary blue. The check is a hole rather than a second-colour shape, which is
what lets one set of paths serve the launcher icon, the Android 13+ monochrome
themed icon and the white notification silhouette without being redrawn.

`brand/remiit-icon.svg` is the master for anything outside the app; its path
data is identical to `ic_launcher_foreground.xml`, so copy edits across verbatim.

## How a rule works

A rule is one self-contained document: triggers, delivery config and constraints
are stored as JSON columns on a single row. Adding a trigger kind needs no schema
migration, and a rule can be moved or restored as a single value.

**Triggers** — any combination of:

| Kind | Fires on | Mechanism |
|---|---|---|
| Time | daily / weekly / monthly / every-N-within-a-window / one-shot | `AlarmManager`, re-armed per fire |
| Wi-Fi | connecting to or leaving a named SSID | live `ConnectivityManager` callback |
| Place | entering, leaving or dwelling in a radius | Play Services geofences |
| App launch | any app, or specific ones | usage-stats polling |

`Any trigger` fires on the first match. `All triggers` requires every one within
a bounded window, which is what makes "on office Wi-Fi **and** after 3pm" work.

**Delivery** — notification, full-screen banner, or alarm (looping tone on the
alarm stream, so it ignores media volume). Alarm rules pick their own tone
through the system ringtone picker; the other two are notifications, whose sound
belongs to the channel and is set in system settings. Each carries Complete /
Not done, and every firing and response is logged.

**Constraints** — cooldown, max per day, quiet hours, active days, validity
range. These are why "remind me on any app launch" is usable rather than a
firehose.

## How an automation works

An automation is the sibling of a rule, not a kind of rule. A rule asks *you* to
do something and waits for an answer; an automation changes a setting and expects
nothing. There is no delivery mode, no snooze, and no Complete/Not done — folding
the two together would have meant a rule type where most of a rule is meaningless.

One trigger, not a list, because a single edge — this happened, do this — has no
ambiguity about what should happen when half of it stops being true.

| Trigger | Fires on | Mechanism |
|---|---|---|
| Wi-Fi | connecting to or leaving a named SSID | the rules' `ConnectivityManager` callback |
| Bluetooth | a paired device connecting or disconnecting | runtime-registered ACL broadcasts |
| Place | entering or leaving a radius | Play Services geofences, a second set |

**Actions** — ringer (silent / vibrate / ring), Do Not Disturb on or off,
volume levels for the media, ring, notification, alarm and system streams, and
adaptive brightness on or off. Each is optional; leaving one alone is the default.

Volumes are stored as a percentage rather than the index Android actually takes.
Every device has its own scale — ring might top out at 7 where media goes to 25 —
so an index means nothing away from the phone it was chosen on, and an exported
automation has to survive landing on a different handset. The conversion happens
when the action is applied. Explicit levels are applied *after* the ringer mode,
since "silent" and "ring at 50%" can both be configured and the specific
instruction should win.

The environment is shared with rules at the source. The Wi-Fi callback is
registered once and feeds both: rules are matched inside the monitor, which
already holds them, while automations are published as a raw signal for the
automation engine to match against its own table. Geofences are the exception —
Play Services keys a fence set by its `PendingIntent`, and both sides re-register
their whole set on every change, so they need separate intents and receivers or
each edit would wipe the other's fences.

Automations fail more quietly than anything else in the app: the phone simply
stays loud. Every run is therefore recorded on the row — what changed, or why it
could not — and shown on the card.

## App-launch detection

Android has no ordinary API for noticing another app opened. Usage access is the
only route: it polls once a second, so it lags slightly and keeps the foreground
service alive. It is granted through Settings > Usage access rather than a
runtime prompt.

## Backup and restore

Settings > Backup writes every rule and automation to a JSON file, and reads one
back. This is cheap to support because a rule is already a self-contained
document in the database — the export is the rows themselves, not a parallel
serialisation that could drift from what is stored.

Written through the Storage Access Framework, so the app holds no storage
permission: the user picks the destination and the app gets a URI for that one
file. Import matches on id, so re-importing the same file updates rather than
duplicates — which is what someone unsure whether the first attempt worked will
end up doing.

Reminder history is not included; it is a log of what happened on one device,
not configuration. An automation's last-run record is stripped for the same
reason, on the way out and on the way back in.

The file carries a `format` marker and a `version`. The marker is load-bearing:
the decoder ignores unknown keys so that an older build can read a newer file,
and the consequence is that almost any JSON decodes cleanly into an empty
backup. A file from a *newer* schema version is refused outright rather than
half-read, because a partial import that looks successful is worse than none.

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
- **Do Not Disturb access** — lets an automation silence the phone or toggle Do
  Not Disturb. Android treats silencing as a DND change, so this covers both.
- **Modify system settings** — lets an automation change adaptive brightness.
  Nothing else uses it.
- **Nearby devices** — only to list paired Bluetooth devices when writing an
  automation. Saved automations match on the address and keep working without it.

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

> The two ABI APKs are currently byte-identical in content — the app ships no
> native code of its own. The split is configured ahead of any dependency that
> does; until one arrives it costs a second artifact and buys nothing.

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

