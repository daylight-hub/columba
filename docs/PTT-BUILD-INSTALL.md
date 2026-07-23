# Liberty Chat — PTT voice messages: build & install

Covers building and installing the `FIELD_AUDIO` PTT work: Opus and Codec2, send
and receive, interoperable with Sideband in both directions.

---

## 0. Prerequisites

| Need | Why |
|---|---|
| JDK 21 (25 on CI) | Gradle toolchain floor for the `pythonBackend` flavor |
| Android SDK, `compileSdk 36` | Project baseline |
| `adb` | Installing to the device |

No NDK or native toolchain needed. Codec2 comes from LXST-kt, which already
ships a prebuilt `libcodec2.so` for the voice-call feature.

---

## 1. Apply the patch

```bash
cd /path/to/columba
git checkout -b ptt-field-audio
git apply --check liberty-chat-field-audio.patch   # dry run first
git apply liberty-chat-field-audio.patch
```

If `--check` complains, you're not on `v1.1.0`. `git apply -3` will try a
three-way merge against the blobs it knows.

---

## 2. Codec2 — nothing to do

Codec2 is already in the app. LXST-kt ships `libcodec2.so` plus a JNI bridge at
`tech.torlando.lxst.codec.NativeCodec2` for the voice-call codec profiles
(Ultra Low Bandwidth is Codec2 700C). It reaches `:app` through `:rns-host`'s
`api(libs.lxst.kt)` edge, and LXST-kt's own consumer ProGuard rules already keep
the native bindings.

`Codec2Codec` binds `NativeCodec2` directly rather than LXST's higher-level
`Codec2` class, because that class prepends a mode header byte for LXST's
realtime wire format and LXMF `FIELD_AUDIO` has no header.

**One gap:** LXST-kt ships the `.so` for `arm64-v8a` and `armeabi-v7a` only,
while this app also builds `x86_64`. On an x86_64 emulator Codec2 is
unavailable — PTT falls back to Opus on send, and inbound Codec2 clips render
as unsupported. Test Codec2 interop on real hardware.

---

## 3. Build

Flavor axes are `telemetry` × `rnsImpl`. Flavor names are `sentry`/`noSentry` and
`kotlinBackend`/`pythonBackend`. The shipping combination is the Python RNS
backend with Sentry stripped:

```bash
./gradlew :app:assembleNoSentryPythonBackendRelease
```

Run `./gradlew :app:tasks --all | grep assemble` if you want the full list.

Debug build for iterating:

```bash
./gradlew :app:installNoSentryPythonBackendDebug
```

### Release signing

Unsigned release APKs land in
`app/build/outputs/apk/noSentryPythonBackend/release/`. Per-ABI splits plus a
universal fallback are enabled, so you'll see four files. Sign whichever you're
distributing:

```bash
apksigner sign --ks lcs-release.jks \
  --out liberty-chat-1.2.0-arm64-v8a.apk \
  app/build/outputs/apk/noSentryPythonBackend/release/app-arm64-v8a-release-unsigned.apk

apksigner verify --print-certs liberty-chat-1.2.0-arm64-v8a.apk
```

The universal APK is the one to publish if you're unsure what your users carry;
`arm64-v8a` covers essentially every phone made in the last several years and is
about a third the size.

---

## 4. Install

```bash
adb install -r liberty-chat-1.2.0-arm64-v8a.apk
```

`-r` reinstalls over an existing copy and keeps app data. If you get
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the signing key changed — uninstall first
(`adb uninstall network.columba.app`), which **wipes identities and message
history**, so export anything you care about beforehand.

Sideloading without adb: copy the APK to the device and open it from a file
manager, with "install unknown apps" allowed for that file manager. Obtainium
users can point at the release URL — `obtainium.json` is already in the repo.

---

## 5. Enable PTT

Settings → Advanced → Push-to-Talk.

- **Push-to-Talk** on → mic button appears in every conversation, and inbound
  voice messages autoplay when that conversation is on screen.
- **Low Bandwidth (Codec2, for LoRa)** — default, off. Codec2 1200, ~150 B/s.
- **High Bandwidth (Opus)** — on. Opus 24 kbps, ~3 KB/s.

The toggle now selects the *codec*, not just the bitrate, which is the same
choice Sideband's `hq_ptt` makes. That's what makes the two apps symmetric.

Grant the microphone permission on first hold.

---

## 6. Verify interop

### Against Sideband, by hand

Two devices, one running Liberty Chat and one Sideband, on a shared interface.

| Test | Liberty Chat setting | Sideband setting | Expect |
|---|---|---|---|
| Codec2 out | Low bandwidth | default (`hq_ptt` off) | Voice bubble in Sideband, plays on tap |
| Codec2 in | Low bandwidth | default | Autoplays in Liberty Chat if the thread is open |
| Opus out | High bandwidth | either | Voice bubble in Sideband, plays on tap |
| Opus in | High bandwidth | `hq_ptt` on | Autoplays in Liberty Chat |

Enable PTT on the Sideband side for its conversation too, or it will render the
clip but not autoplay it — the same gate this patch implements.

### Automated

```bash
pytest tests/interop/test_audio.py -v
```

Now covers Opus and Codec2 at 1200 and 2400, both directions, with the real
LXMF `AM_*` values pinned. The previous `0x01` in there was
`AM_CODEC2_450PWB` — a mode Sideband refuses outright — and only passed because
both ends round-tripped the tag opaquely.

### Useful logcat filters

```bash
adb logcat -s PttRecorder:V Codec2Codec:V VoiceMessagePlayer:V MessageCollector:I
```

`Codec2Codec` logs one line the first time it is touched telling you whether
the native codec bound. `libcodec2 unavailable on this ABI` means you're on
x86_64 — use a real device.

---

## 7. Known rough edges

**Vendor Opus encoders.** `MediaRecorder` Ogg/Opus is API 29+, and several
vendor encoders only accept 48 kHz input regardless of what the docs say. Both
Opus profiles now request 48 kHz and let the bitrate do the bandwidth work. If
`prepare()` still fails on a specific device, `PttRecorder` logs it and returns
null, so the button reports "Couldn't start recording" rather than failing
silently.

**Codec2 mode choice.** The low profile sends 1200. Sideband decodes the whole
700C–3200 range, so this is free on interop grounds and halves the airtime
versus Sideband's own 2400 default. 700C is cheaper again — 4 bytes per 40 ms
frame — if you want it, change `CODEC2_MODE` in `PttRecorder`; nothing else
needs to move.

**Airtime is still the binding constraint.** On SF11 / BW250 / CR5 a 10-second
Codec2 1200 clip is ~1.5 KB, roughly 15 seconds of air after RNS framing.
`MAX_DURATION_MS` is 120 s, which is 18 KB and about three minutes of air. That
is legal but antisocial on a shared channel; consider capping the low profile
lower.

**`FIELD_AUDIO` bytes are hex in `fieldsJson`.** Inbound audio doubles in size
in the Room `TEXT` column. Fine for PTT-length clips; the 500 KB `_file_ref`
offload catches anything pathological, and the parser handles both shapes.
