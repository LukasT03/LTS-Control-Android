# ANDROID_FINISH.md — Bringing the Android port to parity with iOS

**Audience:** an AI agent implementing this change. Follow `AGENTS.md` (all file I/O through OurMCP hashline tools; never commit or stage).

**Scope:** everything needed to bring `app/` up to the feature level of `LTS-Control-iOS/`.

**Explicitly out of scope:** localisation and string extraction — that is `LANG_UPDATE.md`, a separate in-flight workstream. Every new user-facing string this document introduces must be added as a `values/strings.xml` key (English) and a `values-de/strings.xml` key (German), but do **not** re-litigate the existing string plan and do **not** hardcode literals in new code. Where this document shows German or English text inline, it is specifying *content*, not asking for a literal.

---

## 0. Verified current state

Read before planning. All facts below were verified by reading the files, not inferred.
**The firmware source is in-repo at [LTS-Respooler-Firmware/Firmware/](LTS-Respooler-Firmware/Firmware/)** — `(.txt) Control Board Code` (1853 ln, V4/Pro) and `(.txt) Driver Board Code` (ESP32). It is the **authority** on the wire protocol; §1 is derived from it, not from the iOS client. Where iOS and the firmware disagree, the firmware wins and the iOS behaviour is a bug or dead code. Read it before changing anything in `core/ble/`. `Firmware/README.md` is also the live changelog feed consumed by §7.3, and `latest_board_firmware.txt` (currently `1.2.1`) the version feed consumed by §7.2 — useful as local test fixtures.

### 0.1 What exists on Android

| Area | File | State |
|---|---|---|
| BLE transport | [BleManager.kt](app/src/main/java/com/lts/control/core/ble/BleManager.kt) (282 ln) | scan → connect → notify → JSON. No queue, no permissions, no persistence |
| Wire constants | [BleConstants.kt](app/src/main/java/com/lts/control/core/ble/BleConstants.kt) | service + char UUID correct, matches iOS |
| Outgoing cmds | [Commands.kt](app/src/main/java/com/lts/control/core/ble/model/Commands.kt) | 6 commands + 12 settings. Missing servo, variant; Wi-Fi flow wrong |
| Incoming model | [StatusPayload.kt](app/src/main/java/com/lts/control/core/ble/model/StatusPayload.kt) | 22 fields, all correctly typed. Missing `VAR` and all `SV_*` |
| ViewModel | [BleViewModel.kt](app/src/main/java/com/lts/control/core/ble/BleViewModel.kt) (71 ln) | thin passthrough, no echo suppression, no persistence |
| Shell | [LtsControlApp.kt](app/src/main/java/com/lts/control/ui/LtsControlApp.kt) | 4 tabs; "More" is a literal placeholder |
| Home | [ContentScreen.kt](app/src/main/java/com/lts/control/ui/screens/ContentScreen.kt) (617 ln) | pills + spool + progress + controls. Icons are empty stubs |
| Settings | [SettingsScreen.kt](app/src/main/java/com/lts/control/ui/screens/SettingsScreen.kt) (633 ln) | 5 sections. Respool sub-screen exists but is unreachable |
| Connection | [ConnectionScreen.kt](app/src/main/java/com/lts/control/ui/screens/ConnectionScreen.kt) (341 ln) | Wi-Fi form + BT buttons |
| Theme | [Theme.kt](app/src/main/java/com/lts/control/ui/theme/Theme.kt) | package is still `com.example.ltscontrol.ui.theme` |
| Assets | [app/src/main/res/drawable/](app/src/main/res/drawable/) | only `bambu_spool.png` + launcher vectors |

### 0.2 What exists on iOS and has **no** Android counterpart at all

| iOS file | Feature |
|---|---|
| `AboutView.swift` (558 ln) | "More" tab: version info, OTA update card, firmware polling, OTA result alert |
| `ChangelogEntry.swift` (238 ln) | remote firmware changelog fetch + Markdown parse + cache |
| `InstructionsView.swift` | 6-row help screen |
| `UpgradeCodeView.swift` | V3-board discount-code screen |
| `SplashView.swift` | first-run onboarding + pairing CTA |
| `BoardVariantSelectionView.swift` | forced modal variant picker (STD vs Pro) |
| `BoardVariantSettingsView.swift` | variant change from Settings |
| `SettingsView.swift` → `ServoCalibrationView` | Pro-only servo end-position calibration |
| `LiveActivityManager.swift` + `Widget/LiveActivities.swift` | Live Activity / Dynamic Island progress |
| `Widget/ScreenWidget.swift` | home-screen Start/Pause widget |
| `SharedCode/LTSAppShortcuts.swift`, `SharedIntents/*Intent.swift` | Siri / voice / widget intents |
| `BLEManager.swift` → `LocalNotificationManager` | "done" and "auto-stop" notifications with a Start action |
| `TipsView.swift` | StoreKit tip jar |

### 0.3 Board / firmware facts encoded in the iOS app

These are business rules, not incidental code. Preserve them exactly.

| Rule | Source |
|---|---|
| BLE device *name* is the board identity (`"CtrBoard V3"`, `"CtrBoard V4"`, `"LTS CB"`, `"LTS DB"`, `"esp32 PCB"`), persisted as `boardVersion` | `BLEManager.swift` `didConnect` |
| Board **variant** (`STD`/`PRO`) is stored **on the board**, reported via `VAR`, and is settable | `BLEManager.setBoardVariant` |
| Firmware ≤ 1.1.2 never sends `VAR` → variant must be forced to unknown and any cached value cleared | `BLEManager.isFirmwareVersionAtMost` |
| `VAR == unknown` **and** `VAR` was received → app must force a variant-selection modal | `needsBoardVariantSelection` |
| Servo settings/section exist **only** when variant == Pro | `SettingsView.swift` |
| Fan is unsupported on `CtrBoard V3` — pill shows "not supported", greyed | `ContentView.LuefterPill` |
| `CtrBoard V3` on firmware `1.0.0` gets no OTA: button blocked, help icon opens the upgrade-code screen instead of the changelog | `AboutView.swift` |
| Advertised manufacturer data distinguishes variants: `0x01` = standard, `0x02` = Pro | `AccessorySessionManager.showAccessoryPicker` |
| Latest firmware version: `https://download.lts-design.com/Firmware/latest_board_firmware.txt`, polled every 5 s, cache-busted with `?t=<unixtime/15>` | `AboutView.fetchLatestFirmwareVersion` |
| Changelog: `https://download.lts-design.com/Firmware/README.md`, `### <version>` headings + `-`/`*`/`•`/`–`/`—` bullets | `ChangelogEntry.parseChangelog` |

---

## 1. The wire protocol — single source of truth

Derive everything from this table. It is reconstructed from `BLEManager.swift` (`updateStatus` for RX, the `set*` extension for TX). Where the Android model disagrees, **Android is wrong**.

Service `9E05D06D-68A7-4E1F-A503-AE26713AC101`, characteristic `7CB2F1B4-7E3F-43D2-8C92-DF58C9A7B1A8` (write + notify, JSON UTF-8).

### 1.1 Incoming (notify)

| Key | JSON type | Meaning | Android today |
|---|---|---|---|
| `STAT` | String `I R P U A D` | device state | ok |
| `HAS_FIL` | Bool | filament present | ok |
| `USE_FIL` | Bool | sensor enabled | ok |
| `PROG` | Double 0–100 | progress | ok (`Float`) |
| `REM` | Int | remaining seconds | ok |
| `TEMP` | Int | chip °C | ok |
| `WIFI_SSID` | String | connected SSID | ok |
| `WIFI_OK` | Bool | Wi-Fi up | ok |
| `WIFI_CONN_RESULT` | Bool | connect attempt result (**standalone event packet**) | ok |
| `SSID_LIST` | [String] | scan result (**standalone event packet**) | ok |
| `OTA_OK` | `null` in every status packet; `true`/`false` in a **standalone event packet** | OTA finished | ok |
| `FW` | String | firmware version | ok |
| `VAR` | String `STD`/`PRO`/`UNK` — always present on fw ≥ 1.2.0 | board variant | **missing** |
| `SPD` | Int 50–100 | speed % | ok |
| `JIN` | Int 0–3 | jingle | ok |
| `LED` | Int 0–100 | LED brightness | ok |
| `DIR` | Int `0`/`1` | direction reversed | ok |
| `POW` | Int 80–120 | motor strength | ok |
| `TRQ` | Int 0–3 | auto-stop sensitivity | ok |
| `WGT` | Int 0–3 | respool amount | ok |
| `DUR` | Int 5–2000 | seconds for 1 kg @ 80 % | ok |
| `HS` | Bool | high-speed mode | ok |
| `FAN_SPD` | Int 10–100 | fan speed | ok |
| `FAN_ON` | Bool | fan currently running | ok (unused in UI) |
| `FAN_ALW` | Bool | fan always on | ok |
| `SV_R` | Int 0–180 | servo right end angle | **missing** |
| `SV_L` | Int 0–180 | servo left end angle | **missing** |
| `SV_STP` | Double 0.05–20 | servo step mm | **missing** |
| `SV_HOME` | String `L`/`R` | servo home side | **missing** |

**Framing.** Every BLE write and every notify is exactly one complete JSON document — the firmware's `onWrite` passes the value straight to `handleCommand` with no buffering or reassembly (`(.txt) Control Board Code` line 1083). There is no chunking protocol: a packet must fit the negotiated MTU, which is why the 512-byte `requestMtu` matters.

**`STAT` is always a plain string.** iOS also handles a wrapped `{"STAT":{...}}` form; the firmware never sends it (`doc["STAT"] = String(currentState)`). That branch is dead defensive code — Android does not need to reproduce it.

### 1.2 Outgoing

Commands: `{"CMD":"<X>"}` where X ∈ `START STOP PAUSE OTA WIFI_CONNECT WIFI_SCAN`.

Settings: `{"SET":{"<KEY>":<value>}}`. iOS sends one key per packet; the firmware iterates **all** keys in a `SET` block independently, so batching several keys into one packet is safe and is the better choice on Android.

**Ordering trap:** the firmware processes `CMD` **before** `SET` within the same document (`if (doc.containsKey("CMD")) {...} if (doc.containsKey("SET")) {...}`). A command that depends on settings in the same packet will run against the *previous* values. This is the root cause of §2.1.

| TX key | Value | Android today |
|---|---|---|
| `SPD` `JIN` `LED` `POW` `TRQ` `WGT` `DUR` `FAN_SPD` | Int | ok |
| `USE_FIL` `DIR` `HS` `FAN_ALW` | Int `1`/`0` | ok |
| `WIFI_SSID` `WIFI_PASS` | String, must land **before** the `WIFI_CONNECT` command | **wrong — see §2.1** |
| `VAR` | String `STD`/`PRO`/`UNK`, or Int `0`/`1`/`2` — firmware accepts both | **missing** |
| `SV_R` `SV_L` | Int, clamped 0–180 | **missing** |
| `SV_STP` | Double, clamped 0.05–20 | **missing** |
| `SV_HOME` | String `L`/`R` | **missing** |
| `SV_GOTO` | String `L`/`R`/`HOME`, transient. **Ignored unless the motor is stopped and state is `I` or `D`** | **missing** |

---

## 2. Phase P0 — BLE correctness (blocking; nothing else matters until this is right)

Do this phase first and verify it on real hardware. Every later phase assumes a working transport.

### 2.1 Fix the Wi-Fi credential handshake — **confirmed functional bug**

Android `Commands.wifiConnect` sends one combined packet:

```json
{"CMD":"WIFI_CONNECT","SET":{"WIFI_SSID":"...","WIFI_PASS":"..."}}
```

The firmware handles `CMD` **first**, then `SET` (`(.txt) Control Board Code`, the two sequential `if (doc.containsKey(...))` blocks). So `WIFI_CONNECT` executes against the credentials from the *previous* exchange:

```c
} else if (command == "WIFI_CONNECT") {
  if (wifiSSID.length() > 0 && wifiPassword.length() > 0) {   // still the OLD values
    WiFi.disconnect(true);
    WiFi.begin(wifiSSID.c_str(), wifiPassword.c_str());
```

Consequences:

- **First attempt after boot:** both strings are empty (or hold whatever was persisted in NVS), the guard fails, and the board sends **nothing at all**. Android's `isConnecting` is only cleared by `WIFI_CONN_RESULT`, so the spinner hangs forever.
- **Second attempt:** connects using the *first* attempt's credentials. Editing a typo'd password appears to do nothing, then silently succeeds one attempt later.

The firmware iterates every key in a `SET` block independently, so two packets suffice — iOS's three-packet split is not required:

```
{"SET":{"WIFI_SSID":"<ssid>","WIFI_PASS":"<pw>"}}   →  +300 ms
{"CMD":"WIFI_CONNECT"}
```

Keep a short delay between them: each write is dispatched through the GATT queue (§2.5) and the board's `SET` handler ends with a blocking NVS write plus a `sendStatus(true)`. Delete `Commands.wifiConnect` and replace it with `Commands.setWifiCredentials(ssid, pass)` + the existing command builder.

The board replies asynchronously with a standalone `{"WIFI_CONN_RESULT":true}`, or `false` after a **10 s** timeout (`wifiConnectStartTime` check in `loop()`). Size the UI timeout above that.

### 2.2 Verified correct — do **not** "fix" these

These were suspected defects before the firmware source was available. Reading it clears them. Left here so the next agent does not burn time re-deriving them or, worse, "correcting" working code.

| Suspicion | Verdict |
|---|---|
| `StatusPayload.motorDirection` typed `Int?` while the board sends a boolean | **False.** Firmware declares `int motorDirection = 0;` and emits `doc["DIR"] = motorDirection` → JSON `0`/`1`. Android's `Int?` is right. (iOS reads it as `Bool` and only works because Swift's `NSNumber` bridge accepts `0`/`1` as `Bool`.) |
| `handleIncoming`'s exclusive `when` discards status packets carrying events | **False.** `SSID_LIST`, `WIFI_CONN_RESULT` and `OTA_OK` events are each emitted as their own single-key `StaticJsonDocument<64>`. They never travel with status fields. |
| `OTA_OK` guarded by `element.size == 1` is too strict | **False, and load-bearing.** Every status packet carries `doc["OTA_OK"] = nullptr`, so the key is always present. The size check is exactly what distinguishes a real OTA event from the null placeholder. Keep it. |
| `WIFI_RESULT` is missing from `StatusPayload` | **Not a gap.** Neither board's firmware ever emits `WIFI_RESULT`. iOS reads it into `status.wifiLastResult`, which is therefore permanently `nil` — dead code on iOS. Do not port it. |

The only genuine type risk is the fields Android does not model at all (`VAR`, `SV_*`); `ignoreUnknownKeys = true` drops them silently rather than failing, so there is no crash today — just missing data.

**Worth doing anyway while in this file:** replace the bare `catch (_: Throwable)` in `handleIncoming` with one that logs the offending JSON in debug builds. It currently converts any future schema drift into a silent `IncomingMessage.Raw`, which is how a real mismatch would stay invisible.

### 2.3 Model the fields Android ignores

The firmware sends `VAR`, `SV_R`, `SV_L`, `SV_STP` and `SV_HOME` in **every** status packet on fw ≥ 1.2.0. Android's `StatusPayload` does not declare them, so they are discarded by `ignoreUnknownKeys`.

Add all five (see §1.1 for types). Note `VAR` arrives as a **string** from both boards (`"PRO"`/`"STD"`/`"UNK"`) even though the setter accepts an int — so a tolerant deserializer accepting either is cheap insurance, but string-only would work against current firmware.

Because `VAR` is always present on fw ≥ 1.2.0, `didReceiveBoardVariant` is effectively "firmware is new enough", and the forced-selection modal (§5.2) triggers on `VAR == "UNK"` — the factory default (`uint8_t boardVariant = 0`) for a board that has never been told which chassis it is in.

### 2.4 Runtime permissions — **the app cannot scan on any modern device**

The manifest declares `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` but nothing ever requests them, and `BleManager` is blanket-annotated `@SuppressLint("MissingPermission")`. On API 31+ `startScan` fails silently.

Create `app/src/main/java/com/lts/control/core/permissions/BlePermissions.kt`:

- API ≥ 31: `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT`.
- API 26–30: `ACCESS_FINE_LOCATION` (and location services must be **on** — check `LocationManager.isLocationEnabled`; a granted permission with location off still yields zero scan results).
- Gate the Connection screen's "Connect" button and the auto-reconnect on the permission state; render a rationale row when denied, and an "Open settings" action when permanently denied.
- Also handle adapter-off: `adapter.isEnabled == false` should surface an enable prompt (`ACTION_REQUEST_ENABLE`), not the current silent `return`.

Constrain the legacy permissions in the manifest so Play doesn't demand location on new devices:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
```
Drop `ACCESS_COARSE_LOCATION` (fine implies it for BLE scanning on 26–30).

### 2.5 Serialise GATT operations

Android BLE permits exactly one outstanding GATT operation. `onServicesDiscovered` currently fires `requestMtu` and `writeDescriptor` back to back, then declares `Ready` before either completes; concurrent `writeCharacteristic` calls from the UI will be dropped with no error surfaced.

Implement a single-slot operation queue in `BleManager`:

- A `Channel<GattOp>` consumed by one coroutine; each op suspends until its callback (`onMtuChanged`, `onDescriptorWrite`, `onCharacteristicWrite`) resumes it, with a timeout of `BleConstants.WRITE_TIMEOUT_MS` (already defined, currently unused).
- Order on connect: `discoverServices` → `requestMtu(512)` → `setCharacteristicNotification` + CCCD write → **then** `ConnectionState.Ready`.
- Override `onDescriptorWrite` and only then mark ready.

Use the API-33 non-deprecated overloads behind a version check: `gatt.writeCharacteristic(ch, bytes, writeType)` and `onCharacteristicChanged(gatt, ch, value)`; keep the deprecated path for < 33.

### 2.6 Reconnect and disconnect lifecycle

Current problems in `onConnectionStateChange`:

- Reconnect is scheduled on **every** disconnect, including a user-initiated `disconnect()`, and on GATT errors — with a fixed 2 s delay and no cap. Add an `intentionalDisconnect` flag and exponential backoff (2 s → 4 s → 8 s, cap 30 s), reset on a successful `Ready`.
- `disconnect()` calls `gatt.disconnect()` then `gatt.close()` immediately; close before the callback arrives leaks the connection. Close in `onConnectionStateChange(STATE_DISCONNECTED)` instead.
- `status != BleConstants.GATT_SUCCESS && newState != STATE_CONNECTED` — mishandles status 133 during connect. Treat any non-zero status as an error path and always `close()` before retrying.

Mirror iOS's disconnect-state reset (`BLEManager.didDisconnectPeripheral`): clear progress → 0, remaining, filament, temperature, SSID/Wi-Fi flags, fan state, temperature history, SSID list, scanning flag.

### 2.7 Remember the device and auto-reconnect

iOS persists `lastPeripheralUUID` / `storedAccessoryIdentifier` and reconnects at launch (`AccessorySessionManager.restorePreviousConnection`). Android currently requires pressing "Connect" every single launch, and the "Remove saved device" button forgets nothing.

- Persist the device MAC (`BluetoothDevice.address`) in DataStore (see §3.1).
- On app start with permissions granted: `adapter.getRemoteDevice(addr).connectGatt(autoConnect = true)` — no scan needed.
- "Remove saved device" clears the address, disconnects, and re-enables the Connect button.

### 2.8 Capture the board identity

iOS stores `peripheral.name` as `boardVersion` on connect and persists it. Android never reads the device name, so every V3-board rule (fan unsupported, no OTA, upgrade-code screen) and the About screen's board-type label are unimplementable.

Read `gatt.device.name` in `onServicesDiscovered`, persist as `boardVersion`, expose on the state model. Port `AboutView.boardTypeLabel`:

| Contains | Label |
|---|---|
| `CtrBoard V3` | Control Board V3 |
| `CtrBoard V4` / `LTS CB` | Control Board *(Pro)* |
| `LTS DB` | Driver Board *(Pro)* |
| `esp32 PCB` | Driver Board |

Also widen scan matching: `BleConstants.ADVERTISED_NAME` is only `"esp32 PCB"`. Match on service UUID primarily; use name only as a fallback and accept all five known names.

**Acceptance for P0:** on a real board — app launches, requests permissions, auto-connects to a remembered device, status pills update continuously, changing any setting round-trips, Wi-Fi provisioning succeeds, pulling power triggers a backed-off reconnect, and killing the app and relaunching reconnects without user action.

---

## 3. Phase P1 — State model, persistence, echo suppression

### 3.1 Introduce a real device-state holder

iOS has `DeviceStatus` (a persisted, defaulted, mutable model) separate from the raw wire payload. Android conflates the two: the UI reads `StatusPayload?` directly, so **before the first packet arrives every setting shows a hardcoded fallback**, and after a disconnect the UI keeps showing the last payload.

Create `app/src/main/java/com/lts/control/core/state/DeviceStatus.kt` — an immutable data class with the same fields and defaults as iOS `DeviceStatus.init`:

| Field | Default | Field | Default |
|---|---|---|---|
| `speedPercent` | 85 | `torqueLimit` | 0 |
| `jingleStyle` | 0 | `highSpeed` | false |
| `ledBrightness` | 50 | `durationAt80` | 895 |
| `fanSpeed` | 60 | `targetWeight` | 0 |
| `fanAlways` | false | `servoAngleR` | 5 |
| `useFilamentSensor` | true | `servoAngleL` | 175 |
| `directionReversed` | false | `servoStepMm` | 1.75 |
| `motorStrength` | 100 | `servoHome` | "R" |

Plus non-persisted live fields: `hasFilament`, `progress`, `remainingTime`, `chipTemperature`, `wifiSsid`, `wifiConnected`, `wifiConnectionResult`, `otaSuccess`, `isFanOn`, `deviceState`; and persisted identity: `firmwareVersion`, `boardVersion`, `boardVariant`, `didReceiveBoardVariant`. (iOS also carries `wifiLastResult`; omit it — per §2.2 the firmware never sends `WIFI_RESULT`.)

Persist with **DataStore Preferences** (not `SharedPreferences`; the existing private `AppPrefs` class inside `SettingsScreen.kt` should be deleted and its two keys folded in). Keys mirror the iOS `UserDefaults` names so behaviour is comparable, but there is no need to be byte-compatible.

Move `BleViewModel` to own a `MutableStateFlow<DeviceStatus>`, fold incoming payloads into it, and have the UI read **only** from it.

### 3.2 Add `didInitialSync` gating

iOS refuses to send **any** setting (`guard didInitialSync else { return }`) until the first status packet has been processed. Without it, a screen composing on connect can push stale local defaults onto the board and overwrite real values. Android has no such gate — add it: set `didInitialSync` on the first decoded status, and make every `set*` a no-op before that.

### 3.3 Echo suppression — the settings UI currently fights the user

Each iOS setter records `lastLocalSettingChange[key] = now`, and `updateStatus` ignores an incoming value for that key for **0.5 s**. `SPD` gets a stricter scheme: a 1.2 s echo window matching an `expectedEchoInt`, plus a debounce requiring the same remote value twice ≥ 0.12 s apart before it is accepted.

**Why this is unavoidable, confirmed from firmware:** every `SET` block that changes anything ends with a blocking NVS write and then `sendStatus(true)` — an immediate, unconditional echo. On top of that the board free-runs a periodic status notify. So a slider drag produces a burst of notifications each carrying the value as the board currently understands it, interleaved with the user's in-flight edits. Binding UI state directly to that stream *cannot* be made to feel right without a suppression window.

Android has none of this in `SettingsScreen` — every row does `LaunchedEffect(status?.x) { local = x }`, so a stepper tap is immediately overwritten by the next (stale) notification. This is visible as values snapping back.

Implement centrally in the state layer, not per-screen:

```kotlin
class EchoGuard(private val window: Duration = 500.milliseconds) {
    fun markLocal(key: String)
    fun shouldAcceptRemote(key: String): Boolean
}
```

and a `SpeedEchoGuard` for `SPD` reproducing the `expectedEchoInt` + 2-round/120 ms debounce logic. Then delete the per-row `LaunchedEffect` resync hacks and the `isEditing` flag in `ContentScreen`.

### 3.4 Port the remaining RX smoothing rules

| Rule | iOS source |
|---|---|
| `TEMP` is a rolling mean of the last **10** samples | `updateStatus` `temperatureHistory` |
| For 2 s after connect, force `wifiConnected = false` and buffer the incoming value (`pendingWiFiConnected`) | `didConnect` `wifiStatusHoldUntil` |
| `USE_FIL` echo window is 0.5 s like the others | `updateStatus` |
| Firmware ≤ 1.1.2 → clear `boardVariant` and `didReceiveBoardVariant` | `isFirmwareVersionAtMost` |

Port `isFirmwareVersionAtMost` verbatim including its quirks: strips a leading `v`, keeps only digits per segment, missing segments are 0, and the comparison is `<=` on patch.

**Acceptance for P1:** dragging a slider or tapping a stepper never snaps back; settings render correct values before the first packet; temperature is stable rather than jittery; relaunching offline shows the last-known settings.

---

## 4. Phase P2 — Home screen parity

Target: `LTS-Control-iOS/LTS Control/ContentView.swift`.

### 4.1 Replace the placeholder icons — **visible defect**

`ContentScreen.kt` ends with a private `Icons` object whose seven entries all build an `ImageVector` containing `moveTo(0f, 0f)` and nothing else. **All four status pills currently render blank icons.** `material-icons-extended` is already a dependency.

| Pill | iOS SF Symbol | Material substitute |
|---|---|---|
| connected / disconnected | `antenna.radiowaves.left.and.right[.slash]` | `Icons.Rounded.SettingsInputAntenna` / `PortableWifiOff` |
| filament ok / missing | `checkmark.circle` / `xmark.circle` | `Icons.Rounded.CheckCircle` / `Cancel` |
| temp normal / high | `thermometer.medium` / `.high` | `Icons.Rounded.DeviceThermostat` (tint-differentiated) |
| fan on / off | `fan` / `fan.slash` | `Icons.Rounded.Air` / `Icons.Rounded.AirplanemodeInactive`, or ship a vector drawable |

Delete the `Symbols`/`Icons`/`materialPathIcon` stubs entirely.

### 4.2 Fix the fan pill

Currently hardcoded to `"—"`. Port `ContentView.LuefterPill`:

- `boardVersion == "CtrBoard V3"` → slash icon, secondary tint, subtitle "not supported", title greyed.
- else `isFanOn` → subtitle `On: <fanSpeed> %`, icon tinted `ltsBlue`.
- else → subtitle `Off`.

### 4.3 Fix the temperature pill

Two defects: the subtitle drops the `Chip: ` prefix, and the `temperatureInFahrenheit` preference is written by Settings but **never read** (already flagged in `LANG_UPDATE.md` §4.2 as out of scope *there* — it is in scope *here*). Read the preference from the state layer and render `Chip: 52 °C` / `Chip: 126 °F` (`°F = °C × 9/5 + 32`, rounded to integer). Keep the ≥ 65 °C critical threshold for icon + colour.

### 4.4 Complete the status row

iOS renders a three-part row above the progress bar: left `NN %` (or `Fehler!` in red on auto-stop), centre the state text, right `-MM:SS`. Shown only while `running`/`paused`/`auto-stop`; blank otherwise. Both numeric fields are `monospacedDigit` and fixed-width (70 pt) so the centre text does not shift.

Android renders only the centre text. `vm.remainingSeconds` is collected and discarded, and `formatSeconds` is dead code. Wire both up; use a monospace `TextStyle` and fixed-width `Box`es.

### 4.5 Enable/disable the control buttons

iOS disables **Start** when `(useFilamentSensor && !hasFilament) || !isConnected || state == .updating`, and **Stop** when `!isConnected || state == .updating`. Android's `PillButton` has no `enabled` parameter at all and fires on every tap.

Add `enabled: Boolean` to `PillButton` (dimmed background, no ripple, no `onClick`), and apply both predicates. Also: `PillButton` invokes `onClick` after `tryAwaitRelease()` regardless of whether the pointer was released **inside** the button — a drag-away still fires it. Use `Modifier.clickable` with an `indication`, or check `tryAwaitRelease()`'s return value.

### 4.6 Spool visuals

Lower priority, cosmetic. iOS `SpoolWithGradientView` composites a radial gradient halo behind the spool and punches a circular hole (`blendMode(.destinationOut)`, diameter = size / 3.55) through the hub. Android draws a plain rotated bitmap clipped to a circle.

Also reconcile the physics constants — iOS: `maxMotorSpeed = 5`, accel 0.01, decel 0.05, friction 0.98, velocity clamped ±15. Android: auto velocity 15 for running / 5 for updating, friction 0.992, no accel/decel smoothing. Adopt the iOS constants so the spool ramps up and down instead of snapping.

Delete `rememberSmoothRotation` — it is dead code duplicating the inline loop.

### 4.7 Dark mode

`ContentScreen` hardcodes `Color.White` for pills and the control card, and `Color(0xFFF4F9FF)` for the page background; `LtsControlApp` hardcodes `Color.White` for the navigation bar. On a dark device this renders white-on-white. `Theme.kt` already defines a dark scheme that nothing uses.

Replace every hardcoded colour with a `MaterialTheme.colorScheme` role (`surface`, `surfaceContainer`, `background`). Add the missing semantic colours (paused orange, done green, ltsBlue) to [Color.kt](app/src/main/java/com/lts/control/ui/theme/Color.kt) as light/dark pairs, matching `Color.ltsBlue` in `LTSControlApp.swift` (light `#0C4C98`, dark `#145AAA`).

---

## 5. Phase P3 — Settings parity

Target: `LTS-Control-iOS/LTS Control/SettingsView.swift`.

### 5.1 Wire up the Respool amount screen

`RespoolAmountScreen` is fully implemented in [SettingsScreen.kt](app/src/main/java/com/lts/control/ui/screens/SettingsScreen.kt) but `LtsControlApp` passes `onOpenRespoolAmount = { /* später */ }` — **it is unreachable**. Introduce navigation (§9.1) and route to it.

### 5.2 Add the Board Variant section

Shown only when `boardVariant` is `STD` or `PRO` (iOS condition). Row: title "Variante", subtitle `Respooler V4` / `Respooler Pro`, chevron → `BoardVariantSettingsScreen`.

New file `app/src/main/java/com/lts/control/ui/screens/BoardVariantScreen.kt`, two entry points mirroring iOS:

- **Selection** (forced modal, `BoardVariantSelectionView.swift`): two image options + radio, a "Save" button disabled until a choice is made, explanatory footer. Dismisses on save.
- **Settings** (`BoardVariantSettingsView.swift`): same two options, but tapping applies immediately, no save button.

Both send `{"SET":{"VAR":"STD"|"PRO"}}`.

The forced modal must appear when `didReceiveBoardVariant && boardVariant == UNKNOWN` and the onboarding splash is not showing — and must dismiss when the condition clears or BLE disconnects. Port the four `onChange` handlers in `LTSControlApp.swift`.

Requires two new drawables: `respooler.png`, `respooler_pro.png` — source from `LTS-Control-iOS/LTS Control/Assets.xcassets/Respooler.imageset/Respooler.png` and `RespoolerPro.imageset/RespoolerPro.png`.

### 5.3 Add the Servo section (Pro only)

Rendered only when `boardVariant == PRO`. Three elements:

1. Row → "Endpositionen kalibrieren" sub-screen.
2. Stepper: step width, `0.50…4.00 mm`, step `0.01`, two decimals → `SV_STP`. (The firmware clamps to `0.05–20.0`; the tighter iOS range is a deliberate UI choice — keep it.)
3. Segmented control: home side Links/Rechts → `SV_HOME`.

Calibration sub-screen (`ServoCalibrationView` inside `SettingsView.swift`):

- Segmented L/R selector; changing it sends `SV_GOTO` with the new side.
- A ±1° row: chevron-left **increments** toward 180, chevron-right **decrements** toward 0 (note the inversion — preserve it), clamped 0…180, each tap firing a light haptic (`HapticFeedbackType.LongPress` or `VibrationEffect.EFFECT_TICK`) and `SV_L`/`SV_R`.
- Centre label `Winkel: N°`, monospace.
- On enter: select `servoHome` (default `R`) and send `SV_GOTO <side>`. On leave: send `SV_GOTO HOME`.
**Firmware constraint:** `SV_GOTO` is silently ignored unless `!isMotorRunning && (state == 'I' || state == 'D')`. The screen must therefore be unreachable — or at minimum show an explanatory disabled state — while the respooler is running, paused, updating or auto-stopped. iOS does not guard this and simply appears unresponsive; do better on Android.

### 5.4 Missing rows and control-type mismatches

| Item | iOS | Android now | Action |
|---|---|---|---|
| LED brightness | `Stepper`, 0–100 step 10 | `Slider` with `steps = 9` | Use `StepperRow` for consistency with strength/fan |
| Auto-stop sensitivity | `Picker` (dropdown) | −/+ `PickerRow` | Use `DropdownMenu` like the jingle row |
| Fan always on | `Toggle` | `status?.fanAlwaysOn ?: (status?.fanAlwaysOn == true)` — a tautology that always yields `false` when null | `state.fanAlways` |
| Live Activities toggle | present | absent | Add as "Ongoing notification" (see §8.2) |
| Language row | — | — | Owned by `LANG_UPDATE.md` §7; leave the slot |
| Fan speed floor | `setFanSpeed` guards `>= 10` | unguarded | Add the guard in the command layer, not only the UI |

### 5.5 Status capsule

iOS drives the icon+colour from `deviceState` **and** connection state, with `antenna…slash.circle` + primary colour when disconnected. Android's `when` keys off `status == null`, which is not the same thing (status survives a disconnect). Key it off the connection state once §3.1 lands.

---

## 6. Phase P4 — Connection screen

Target: `LTS-Control-iOS/LTS Control/ConnectionView.swift`.

### 6.1 Remove the nested Scaffold

`ConnectionScreen` and `RespoolAmountScreen` each wrap their content in their own `Scaffold` + `TopAppBar`, while `LtsControlApp` already provides one — producing a double app bar. Hoist the title and the scan action into the host's `TopAppBar` (or drop the host bar for these routes), as part of the navigation work in §9.1.

### 6.2 Wi-Fi flow corrections

- Use the two-packet sequence from §2.1.
- `isScanning` is set locally and only ever cleared by an `SSID_LIST` arrival — a failed or ignored scan spins forever. Add a timeout. Note the firmware runs the scan on a separate FreeRTOS task and **always** notifies when it finishes, but it filters results by `MIN_RSSI_THRESHOLD` and drops SSIDs longer than 32 chars, so a legitimately empty list is possible — render "no networks found", not a spinner.
- `isConnecting` must also clear on the board's `false` result, which arrives **10 s** after `WIFI_CONNECT` if the association fails. Do not add a shorter client-side timeout or the two will race.
- Keep the 3.5 s error highlight window and the wiggle/bounce animations — these already match.

### 6.3 Bluetooth section

- **Connect** should open a device picker, not auto-grab the first advertiser. iOS shows a two-entry picker (`LTS Respooler` / `LTS Respooler Pro`) built from manufacturer data `0x01` vs `0x02`. Android equivalent: a bottom sheet listing scan results with name, RSSI and — where manufacturer data is present — the inferred variant, plus the product image. Selecting one connects and persists the address.
- Read manufacturer data during the scan (`result.scanRecord.getManufacturerSpecificData(0xFFFF)`) and use the first byte to pre-seed the variant, avoiding the forced selection modal for boards that advertise it.
- **Remove saved device** must actually clear the persisted address (§2.7).
- Disable **Connect** while permissions are missing or the adapter is off, with an explanatory subtitle.

---

## 7. Phase P5 — The "More" tab (About / OTA / changelog / help)

Currently `AboutScreenPlaceholder()` — a single `Text`. This is the largest single gap: `AboutView.swift` is 558 lines and owns the entire firmware-update experience.

New package `app/src/main/java/com/lts/control/ui/screens/about/`.

### 7.1 `AboutScreen.kt`

Layout top→bottom: app icon (`AppIconSquare` → new `drawable/app_icon_square.png`), "LTS Control" title, board-type label (§2.8), `App Version: <BuildConfig.VERSION_NAME>`, `Board Version: <firmwareVersion>` (hidden when unknown), two capsule buttons (Hinweise → §7.4, Trinkgeld → §7.6), the OTA card (§7.2), and the legal footer:

> © 2025, LTS Design, Heiligenbornstraße 23, 01219 Dresden, Deutschland, info@lts-design.com

(line-height 0.86, footnote size, secondary colour — it is a legal imprint; keep the text exact.)

### 7.2 OTA update card

The state machine, in priority order (from `AboutView.body`):

| Condition | Subtitle | Button |
|---|---|---|
| V3 board **and** fw `1.0.0` | "Updates nicht unterstützt" | "Nicht unterstützt", disabled |
| not BLE-connected | "Board nicht verbunden" | disabled |
| `deviceState == UPDATING` | "Firmware wird aktualisiert..." | spinner, disabled |
| `otaSuccess == true` | "Firmware ist aktuell" | — |
| `otaSuccess == false` | "Update fehlgeschlagen!" | — |
| update available (displayed) | "Update verfügbar (`<from>` → `<to>`)" | enabled iff Wi-Fi connected and past the post-connect delay |
| update available, no Wi-Fi | — | "Keine WLAN-Verbindung", disabled |
| else | "Firmware ist aktuell" | — |

Timing quirks to preserve:

- The button is force-disabled for **1.5 s** after BLE connects (`updateButtonDisabledUntil`).
- The very first time an update becomes available, display is held back **0.5 s** (`holdUpdateAvailableUntil`) to avoid a flash during initial sync.

Version comparison: split on `.`, pad with zeros, compare component-wise (`isNewerVersion`).

Latest-version fetch: `https://download.lts-design.com/Firmware/latest_board_firmware.txt?t=<floor(epochSeconds/15)>`, `Cache-Control: no-cache`, 8 s timeout, polled **every 5 s while the screen is visible**, result cached under `latestBoardFirmwareVersion`. On Android, run it from a `ViewModel` with a `repeatOnLifecycle(STARTED)` loop — do not leak a timer.

Tapping the button sends `{"CMD":"OTA"}`. The board ignores it if an OTA is already running.

**On success the firmware calls `ESP.restart()`** — so `{"OTA_OK":true}` is immediately followed by a BLE disconnect and a ~10 s absence while the board reboots onto the new image. The reconnect backoff from §2.6 must tolerate this, and the UI must not present the drop as a connection error. iOS handles it by deferring the alert to the disconnect handler (`showOTAAlert = status.otaSuccess != nil`); do the same. Failure paths (`{"OTA_OK":false}`) do **not** reboot.

Help icon (top-right of the card): opens the **upgrade-code** screen for a V3/`1.0.0` board, otherwise the **changelog**.

OTA result alert: fires on `OTA_OK`; on success offers "Änderungen anzeigen" (→ changelog) plus OK, on failure OK only, with the message about needing a working internet connection on the board's Wi-Fi. Clearing the alert clears `otaSuccess`.

### 7.3 `ChangelogScreen.kt` + parser

Port `ChangelogEntry.swift`:

- Fetch `https://download.lts-design.com/Firmware/README.md`, no-cache, non-2xx → error.
- Parse: normalise CRLF/CR → LF; `### ` starts a version (strip a leading `v`/`V`); lines starting with `-`, `*`, `•`, `–`, `—` are bullets of the current version; commit an entry only when it has a version **and** at least one bullet.
- Cache the parsed list as JSON in DataStore (`FirmwareChangelog.cache.v1`); show cache immediately, then refresh.
- Empty parse result is an **error**, not an empty list.
- Render version headings + bullet lists with dividers between entries.
- The "new content" fade animation (FNV-1a signature vs `FirmwareChangelog.entriesSignature`, `hasSeenEntries`) is optional polish — implement the caching and rendering first.

Add `INTERNET` to the manifest — it is **not currently declared**, so §7.2 and §7.3 will both fail silently until it is.

### 7.4 `InstructionsScreen.kt`

Six icon+title+body rows (`InstructionsView.swift`): Verbindung herstellen (+ a "Verbindung erneut starten" action that opens the device picker), Board Firmware, Motorstärke, Auto-Stopp, GitHub (body contains a link to `https://github.com/LukasT03/LTS-Respooler` — render with `AnnotatedString` + `LinkAnnotation`), Feedback (`info@lts-design.com`, `mailto:` intent).

### 7.5 `UpgradeCodeScreen.kt`

Two instruction rows plus a capsule showing `CBV4-FREE` with a copy button that switches to a checkmark for 3 s. Use `ClipboardManager`. Reachable only from the OTA card's help icon on a V3/`1.0.0` board.

### 7.6 Tip jar — **decision required**

`TipsView.swift` uses StoreKit 2 with five consumable products (`smallTip` … `biggestTip`), a ☕️×N stepper, and success/failure states. The Android equivalent is Google Play Billing with five consumable SKUs, which requires Play Console products that may not exist yet.

Recommendation: **defer**, and ship the Instructions capsule alone in the About screen's button row. Do not stub a non-functional purchase flow. Flag this for the product owner rather than guessing.

---

## 8. Phase P6 — Notifications and background operation

### 8.1 Local notifications — the toggle is currently inert

`SettingsScreen` requests `POST_NOTIFICATIONS` and stores `notificationsEnabled`, but **nothing ever posts a notification**, and no channel is created. Port `LocalNotificationManager` from `BLEManager.swift`:

| Trigger | Title | Body | Extra |
|---|---|---|---|
| state → `DONE` | "Respooler fertig!" | "Der Spulvorgang wurde erfolgreich fertiggestellt." | — |
| state → `AUTO_STOP` **from `RUNNING`** | "Fehler: Auto-Stopp!" | "Der Respooler hat automatisch angehalten. Überprüfe, ob der Motor blockiert ist." | action "Respooler starten" → sends `START` |

Both are suppressed while the app is **foregrounded** (iOS checks `applicationState == .active`) and when the preference is off. Create a notification channel in `Application.onCreate`. The start action needs a `BroadcastReceiver` that reaches the connected `BleManager` — which requires §8.2.

### 8.2 Foreground service ≈ Live Activity

iOS keeps BLE alive in the background (`UIBackgroundModes: bluetooth-central`) and shows a Live Activity with progress, remaining time and Start/Pause/Stop buttons. Android's `BleManager` lives in a `ViewModel` and dies with the Activity — **the connection drops the moment the app is backgrounded**.

- Move `BleManager` into a bound + started **foreground service** (`ConnectedDeviceService`, `foregroundServiceType="connectedDevice"`, with `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_CONNECTED_DEVICE` permissions).
- The service's ongoing notification is the Live Activity analogue: progress bar, `NN %`, `-MM:SS`, and action buttons matching the Live Activity's state logic — paused → Stop + Start; auto-stop → Start; done → Stop; running → Pause.
- Gate it on a settings toggle, replacing iOS's "Live-Aktivitäten" switch (§5.4). Run the service only while connected and not idle, mirroring `LiveActivityManager.sync`: start on `RUNNING`, stop on `IDLE` or disconnect.
- Hoist the state holder to a singleton/`@Singleton` repository so the service, the notification receiver and the UI share one instance. This is a structural change — do it before P5's OTA work if possible, since the OTA card also wants status while backgrounded.

---

## 9. Phase P7–P9 — Shell, onboarding, surfaces, polish

### 9.1 Navigation

There is no navigation library; `LtsControlApp` is a `when` over a local `Tab` enum, which is why the Respool screen is unreachable and sub-screens nest their own Scaffolds. Add `androidx.navigation:navigation-compose` with a bottom-bar `NavHost`: four tab destinations plus `respoolAmount`, `boardVariant`, `servoCalibration`, `instructions`, `changelog`, `upgradeCode`. Single host `Scaffold`; per-destination top bars.

### 9.2 Onboarding / splash

Port `SplashView.swift`: shown on first launch when `hasCompletedSetup == false` **and** no device is saved. App icon, "Willkommen bei LTS Control" headline, two feature rows (Funktion, Verbinden), and a "Respooler verbinden" CTA that opens the device picker. Mark `hasCompletedSetup` on a successful pairing. Must suppress the forced variant modal while visible (§5.2).

### 9.3 Widget, tile, shortcuts

iOS ships a small home-screen widget with Start/Pause buttons plus three App Shortcuts (start/stop/pause) usable from Siri. Android equivalents:

- **Glance app widget** with Start/Pause buttons dispatching to the service from §8.2.
- **Quick Settings tile** (`TileService`) for Start/Pause — the closest analogue to a Dynamic Island control.
- **App Actions / assistant shortcuts** via `shortcuts.xml` + `ACTION_START`/`ACTION_STOP`/`ACTION_PAUSE` capabilities, so "start the respooler" works.

Lowest priority; the app is fully usable without them.

### 9.4 Large-screen layout

iOS switches to a three-column `NavigationSplitView` on iPad at ≥ 729 pt (About | Settings | Control) and reflows the home pills from a 2×2 grid to a single row of 4 at > 730 pt. Android should use `WindowSizeClass`: `Expanded` → `NavigationRail` or list/detail with the same 4-across pill row; `Compact` → today's layout.

### 9.5 Project hygiene

| Item | Action |
|---|---|
| `Theme.kt`/`Color.kt`/`Type.kt` are in package `com.example.ltscontrol.ui.theme` | rename to `com.lts.control.ui.theme` |
| tests in `com.example.ltscontrol` | move to `com.lts.control`, or delete the template tests |
| `versionCode = 1`, `versionName = "1.0"` | set a real version before any release build |
| release build has `isMinifyEnabled = false` | enable minify + add `kotlinx-serialization` keep rules before shipping |
| `Divider` | → `HorizontalDivider` |
| `Icons.Filled.ArrowBack` | → `Icons.AutoMirrored.Filled.ArrowBack` |
| `with` in `AnimatedContent` transitionSpec (ContentScreen) | → `togetherWith` (already used correctly in SettingsScreen) |
| unused imports (`collectLatest`, `abs`, `drawWithContent`, `Size`, `LocalConfiguration` in ContentScreen) | remove |
| German code comments throughout `core/ble/**` | leave — out of scope per `LANG_UPDATE.md` |

### 9.6 Tests

The protocol layer is pure, is where the confirmed bug (§2.1) lives, and is the cheapest place to catch firmware drift. Capture a real notification stream from a board once and reuse it as a fixture:

- `StatusPayload` decoding against **verbatim captured packets**, including the `"OTA_OK":null` placeholder present in every status packet and the `"WIFI_SSID":null` case when no network is set.
- `VAR` accepted as both `"PRO"`/`"STD"`/`"UNK"` and `0`/`1`/`2`; unknown-key tolerance for forward compatibility.
- Event discrimination: single-key `{"OTA_OK":true}` → `OtaResult`, versus a full status packet carrying `OTA_OK: null` → `Status`. This is the invariant §2.2 says to preserve; a test stops someone "simplifying" the size check away.
- `Commands` output shape for each setter (exact JSON), and that credentials are emitted as a `SET` packet distinct from the `WIFI_CONNECT` command.
- `isFirmwareVersionAtMost` and `isNewerVersion` against `"1.1.2"`, `"v1.2"`, `"1.10.0"`, `"1.2.0"`.
- Changelog parser against a fixture copy of the README.
- `EchoGuard` / `SpeedEchoGuard` timing behaviour with a virtual clock.

The project's configured test framework is **TUnit** per `config_workspace`, but this is a Kotlin/Android module — use JUnit + `kotlinx-coroutines-test`, which is what `libs.junit` already provides.

---

## 10. Not in scope — flag, do not fix

1. **Tip jar / in-app purchases** (§7.6) — needs a Play Console product decision.
2. **Localisation and string extraction** — `LANG_UPDATE.md`.
3. **German code comments** in `core/ble/**`.
4. **iOS-26-specific affordances** (liquid-glass toolbars, `.symbolEffect` variants) — pick Material equivalents, do not attempt visual replication.
5. **App icon redesign** — reuse the exported iOS assets.
6. Firmware-side behaviour. If the board does not respond as §1 describes, report it; do not work around it silently.

---

## 11. Verification

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug          # MissingTranslation is already error-level
./gradlew :app:testDebugUnitTest
```

BLE cannot be tested on an emulator. Device matrix — at minimum one device from each row:

| API | Why |
|---|---|
| 26–30 | legacy location-based scanning path; the AppCompat locale backport also lands here |
| 31–32 | new `BLUETOOTH_SCAN`/`CONNECT` permissions, no location |
| 33+ | `POST_NOTIFICATIONS`, new GATT write overloads |

Manual acceptance, per phase:

| Check | Expected |
|---|---|
| Fresh install, no permissions | rationale shown; granting them enables Connect |
| Pair once, kill app, relaunch | auto-reconnects with no interaction |
| Every settings row | value round-trips to the board and does **not** snap back |
| Wi-Fi provisioning | credentials accepted; the Wi-Fi capsule turns green |
| Wrong Wi-Fi password | error highlight for 3.5 s, capsule wiggles, spinner clears |
| V3 board | fan pill "not supported"; OTA blocked; help icon opens the upgrade-code screen |
| Pro board | Servo section visible; calibration moves the guide and returns HOME on exit |
| Board with no `VAR` support (fw ≤ 1.1.2) | no variant shown anywhere, no forced modal |
| Board reporting `VAR` unknown | forced selection modal; saving persists to the board |
| Background the app mid-run | connection survives; ongoing notification shows live progress |
| Finish a run with the app backgrounded | "fertig" notification arrives |
| Block the motor | auto-stop notification with a working Start action |
| Dark mode, every screen | no white-on-white, no hardcoded light surfaces |
| Airplane mode | About screen degrades to cached firmware/changelog data, no crash |

---

## 12. Suggested work order

Each numbered item should build and run before starting the next.

1. **P0.4** (permissions) — without it nothing can be tested on a modern device, so it gates verification of everything below.
2. **P0.1** (Wi-Fi handshake) — small, self-contained, and the one confirmed protocol defect.
3. **P0.3** (model `VAR` + `SV_*`) — prerequisite for the variant and servo work in P3.
4. **P0.5–P0.8** (GATT queue, reconnect lifecycle, device persistence, board identity).
5. **P1** (state holder, DataStore, `didInitialSync`, echo guards) — touches every screen; do it before UI work, not after.
6. **P2** (home screen: icons, fan, temp, status row, button gating, dark mode).
7. **P9.1** (navigation) — prerequisite for P3/P5 sub-screens.
8. **P3** (settings: respool routing, variant, servo, control-type fixes).
9. **P4** (connection: device picker, Wi-Fi result handling).
10. **P6** (foreground service + notifications) — before P5, since the OTA card wants background-safe status.
11. **P5** (About / OTA / changelog / instructions / upgrade code).
12. **P7** (onboarding), **P9.4** (large screens), **P9.5** (hygiene), **P9.6** (tests).
13. **P8** (widget / tile / shortcuts) last.

Steps 1–5 change behaviour invisibly and can mask each other; verify each against real hardware before moving on. §2.2 lists three things that look broken but are not — read it before "fixing" the BLE layer.
