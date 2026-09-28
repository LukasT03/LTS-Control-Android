# LANG_UPDATE.md — Localisation Plan (DE → DE + EN, with in-app language picker)

**Audience:** an AI agent implementing this change. Follow `AGENTS.md` (use OurMCP hashline tools for all file I/O; do not commit).

**Goal**
1. Remove every hardcoded user-facing German string from Kotlin sources.
2. `values/strings.xml` = **English** (default / fallback), `values-de/strings.xml` = **German** (current wording, verbatim).
3. Add a modern per-app language selector (Android 13+ system integration + backport to minSdk 26).

---

## 0. Current state (verified by reading the files)

| Fact | Value |
|---|---|
| UI stack | 100% Jetpack Compose, Material3 |
| `minSdk` / `targetSdk` / `compileSdk` | 26 / 36 / 36 |
| AGP / Kotlin | 9.3.2 / 2.2.20 |
| Activity base class | `ComponentActivity` (**not** `AppCompatActivity`) |
| App theme parent | `android:Theme.Material.Light.NoActionBar` (**not** an AppCompat theme) |
| `strings.xml` contents | one entry: `app_name` = `LTS Control` |
| Manifest `android:label` | hardcoded literal `"LTS Control"`, not `@string/app_name` |
| appcompat dependency | **absent** |

Files holding user-facing German:

- [app/src/main/java/com/lts/control/ui/LtsControlApp.kt](app/src/main/java/com/lts/control/ui/LtsControlApp.kt) — 6 strings
- [app/src/main/java/com/lts/control/ui/screens/ContentScreen.kt](app/src/main/java/com/lts/control/ui/screens/ContentScreen.kt) — 18 strings
- [app/src/main/java/com/lts/control/ui/screens/SettingsScreen.kt](app/src/main/java/com/lts/control/ui/screens/SettingsScreen.kt) — 45 strings
- [app/src/main/java/com/lts/control/ui/screens/ConnectionScreen.kt](app/src/main/java/com/lts/control/ui/screens/ConnectionScreen.kt) — 19 strings

`core/ble/**` contains German **comments only** — no user-facing strings. Leave comments alone (out of scope; translating them is churn with no user benefit).

---

## 1. Language-selection mechanism — decision

**Use AndroidX per-app languages (`AppCompatDelegate.setApplicationLocales`) + `generateLocaleConfig`.**

Rationale: on API 33+ this delegates to the platform `LocaleManager` and makes the app appear under *Settings → System → Languages → App languages*. On API 26–32 appcompat 1.7 backports it (persists the choice and recreates activities). One API, both paths. The alternative (hand-rolled `attachBaseContext` + `createConfigurationContext`) is more code, loses system integration, and is not what Google recommends.

**Cost of this choice — two mandatory prerequisites, do not skip either:**

1. `MainActivity` must extend `AppCompatActivity`, not `ComponentActivity`. The <33 backport recreates activities through AppCompat's delegate; with a bare `ComponentActivity` the locale change will not apply until the process restarts.
2. `Theme.LTSControl` must inherit from an AppCompat theme. `AppCompatActivity` **crashes at startup** (`IllegalStateException: You need to use a Theme.AppCompat theme`) under the current `android:Theme.Material.Light.NoActionBar` parent.

Neither changes the Compose UI — `AppCompatActivity` extends `ComponentActivity`, and `setContent`/`enableEdgeToEdge` keep working. The XML theme is only the window backdrop; all real styling is `LTSControlTheme`.

---

## 2. Step-by-step

### Step 1 — Dependencies

`gradle/libs.versions.toml`:

```toml
[versions]
appcompat = "1.7.0"

[libraries]
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
```

`app/build.gradle.kts` → `dependencies`:

```kotlin
implementation(libs.androidx.appcompat)
```

### Step 2 — Enable locale-config generation

`app/build.gradle.kts`, inside `android { }`:

```kotlin
androidResources {
    generateLocaleConfig = true
}
```

Create `app/src/main/res/resources.properties` (plain properties file, sits next to `values/`, **not** inside it):

```properties
unqualifiedResLocale=en-US
```

This declares that the unqualified `values/` folder holds English. AGP then generates `locales_config.xml` from the `values-*` folders present and injects `android:localeConfig` into the merged manifest — **do not** hand-write `res/xml/locales_config.xml` or add `android:localeConfig` to `AndroidManifest.xml`; that duplicates and conflicts with the generated one.

If `generateLocaleConfig` misbehaves on AGP 9.3.2, fall back to the manual route: drop `androidResources { }` and `resources.properties`, add `app/src/main/res/xml/locales_config.xml` listing `en` and `de`, and set `android:localeConfig="@xml/locales_config"` on `<application>`. Prefer the generated route.

### Step 3 — Theme + Activity prerequisites

`app/src/main/res/values/themes.xml`:

```xml
<style name="Theme.LTSControl" parent="Theme.AppCompat.DayNight.NoActionBar" />
```

`AndroidManifest.xml` → `<application>`: replace `android:label="LTS Control"` with `android:label="@string/app_name"`, and add `android:theme="@style/Theme.LTSControl"` (currently the theme is declared but never applied — applying it is required for the AppCompat check above to pass).

`MainActivity.kt`: change `ComponentActivity` → `androidx.appcompat.app.AppCompatActivity`. Import swap only; body unchanged.

### Step 4 — String resources

Create both files with **identical key sets** (see §3 for the full table).

- `app/src/main/res/values/strings.xml` — English, keeps existing `app_name`.
- `app/src/main/res/values-de/strings.xml` — German, wording copied **verbatim** from the current Kotlin literals. Do not "improve" the German while moving it.

Escaping rules that bite here:
- Apostrophes and `&` must be escaped (`\'`, `&amp;`). The current German has none, the English translations below have none either — keep it that way.
- Multi-line footers: keep as a single line, or use `\n\n` (the respool footer already needs a literal `\n\n`).
- `%` in a literal string is fine, but in a **format** string it must be `%%`. Affects `speed_percent`/`led_percent`-style entries — they are written as `%1$d %%` below.
- Always use positional specifiers (`%1$d`, not `%d`) so translators can reorder.

### Step 5 — Replace literals in Compose code

Inside `@Composable` scope: `stringResource(R.string.key)` / `stringResource(R.string.key, arg)`. Add `import androidx.compose.ui.res.stringResource` and `import com.lts.control.R` (ContentScreen already imports `R`).

**Non-composable helper functions** — these five currently return German `String`s from plain functions and cannot call `stringResource`:

| File | Function |
|---|---|
| ContentScreen.kt | `tempLabel(tempC: Int?)`, `formatSeconds(s: Int)` |
| SettingsScreen.kt | `deviceStateLabel(state)`, `targetWeightLabel(tag: Int)`, `formatMinutesSeconds(seconds: Int)` |

Convert each to return a `@StringRes Int` (plus args where needed) and resolve at the call site, e.g.:

```kotlin
@StringRes
fun DeviceState.labelRes(): Int = when (this) {
    DeviceState.IDLE      -> R.string.state_idle
    DeviceState.RUNNING   -> R.string.state_running
    DeviceState.PAUSED    -> R.string.state_paused
    DeviceState.AUTO_STOP -> R.string.state_auto_stop
    DeviceState.UPDATING  -> R.string.state_updating
    DeviceState.DONE      -> R.string.state_done
    DeviceState.ERROR     -> R.string.state_error
}
```

Prefer `@StringRes Int` over `@Composable fun` helpers: it keeps the mapping unit-testable and avoids forcing composable context on pure logic.

**DRY note — duplicated state labels.** `ContentScreen.kt` and `SettingsScreen.kt` each map `DeviceState` to text, with *different* German wording for two states:

| State | ContentScreen | SettingsScreen |
|---|---|---|
| `IDLE` | `Leerlauf` | `Bereit` |
| `RUNNING` | `Läuft...` | `Läuft` |
| `UPDATING` | `Wird aktualisiert...` | `Wird aktualisiert…` (note: real ellipsis char) |

Do **not** silently merge these into one set — that is a visible UI change, not a localisation change. Keep two key families (`state_*` for the settings/status capsule, `home_state_*` for the home screen) so both screens render exactly as today. Put the single `labelRes()` extension in `core/ble/model/DeviceState.kt` for the settings set, and a second `homeLabelRes()` next to it. Note in the PR description that the two sets could be unified later as a deliberate UX decision.

### Step 6 — Locale-sensitive number formatting

`targetWeightLabel` hardcodes German decimal commas: `"1,0 kg"`, `"0,5 kg"`, `"0,25 kg"`. Do not translate these with `String.format` gymnastics — just give each its own resource key (`weight_1_0kg` etc.), German `1,0 kg`, English `1.0 kg`. Simplest and correct for both.

`formatMinutesSeconds` returns `"Dauer: ${m}m ${r}s"` — note it embeds its own label, and the caller *also* passes it as the `valueText` of a row already titled `Dauer`. Preserve that duplication as-is (resource `duration_value` = `Dauer: %1$dm %2$ds` / `Duration: %1$dm %2$ds`); fixing the redundancy is a UI change, out of scope.

`formatSeconds` in ContentScreen.kt is currently **dead code** (defined, never called). Either delete it or localise it via `time_mmss` / `time_seconds`. Deleting is preferred — flag it in the summary.

### Step 7 — Language picker UI

Add a `Sprache` / `Language` row to the **App** section of `SettingsScreen.kt`, immediately above the existing `Benachrichtigungen` toggle. Reuse the existing `RowSetting(clickable = true)` composable — do not invent a new row style.

Create `app/src/main/java/com/lts/control/ui/screens/LanguagePicker.kt`:

```kotlin
enum class AppLanguage(val tag: String?, @StringRes val labelRes: Int) {
    SYSTEM(null, R.string.language_system),
    ENGLISH("en", R.string.language_english),
    GERMAN("de", R.string.language_german);

    companion object {
        fun current(): AppLanguage {
            val tag = AppCompatDelegate.getApplicationLocales()
                .takeUnless { it.isEmpty }?.get(0)?.language
            return entries.firstOrNull { it.tag == tag } ?: SYSTEM
        }
    }
}

fun AppLanguage.apply() = AppCompatDelegate.setApplicationLocales(
    tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
)
```

Row + `AlertDialog` with three `RadioButton` options; selecting one calls `apply()`. The activity recreates itself — no manual `recreate()`, no restart prompt.

Behaviour to verify: **read the current value with `AppCompatDelegate.getApplicationLocales()`, never from a local `SharedPreferences` key.** The user can also change the language from system Settings on API 33+, and a private pref would then be stale. Do not add a language field to the existing `AppPrefs` class.

Subtitle of the row: show the resolved language name (`Deutsch` / `English` / `Systemsprache`) — resource-backed via `labelRes`.

### Step 8 — Lint gate

`app/build.gradle.kts`, inside `android { }`:

```kotlin
lint {
    warning += "MissingTranslation"
    error += "MissingQuantity"
}
```

Keep `MissingTranslation` at warning while iterating; promote to `error` once both files are complete so future additions cannot land untranslated.

---

## 3. String table

Key naming: `<screen>_<element>` prefixes — `nav_`, `home_`, `set_`, `conn_`, `respool_`, `state_`, `language_`, `common_`.

### 3.1 Shared / navigation — `LtsControlApp.kt`

| Key | German (current) | English (new) |
|---|---|---|
| `app_name` | LTS Control | LTS Control |
| `title_home` | LTS Respooler | LTS Respooler |
| `title_settings` | Einstellungen | Settings |
| `title_connection` | Verbindung | Connection |
| `title_more` | Mehr | More |
| `nav_control` | Steuerung | Control |
| `nav_settings` | Einstellungen | Settings |
| `nav_connection` | Verbindung | Connection |
| `nav_more` | Mehr | More |
| `more_placeholder` | Mehr (About) – kommt später | More (About) – coming soon |

`title_*` and `nav_*` overlap in value but are distinct call sites (top bar vs bottom bar) — keep them separate keys so either can change independently.

### 3.2 Home — `ContentScreen.kt`

| Key | German | English |
|---|---|---|
| `home_state_idle` | Leerlauf | Idle |
| `home_state_running` | Läuft... | Running... |
| `home_state_paused` | Pausiert | Paused |
| `home_state_auto_stop` | Auto-Stopp | Auto-stop |
| `home_state_updating` | Wird aktualisiert... | Updating... |
| `home_state_done` | Fertig | Done |
| `home_state_error` | Fehler | Error |
| `pill_connection` | Verbindung | Connection |
| `pill_temperature` | Temperatur | Temperature |
| `pill_filament` | Filament | Filament |
| `pill_fan` | Lüfter | Fan |
| `common_connected` | Verbunden | Connected |
| `common_disconnected` | Getrennt | Disconnected |
| `filament_detected` | Erkannt | Detected |
| `filament_not_detected` | Nicht erkannt | Not detected |
| `value_none` | — | — |
| `temp_unknown` | – | – |
| `temp_celsius` | %1$d °C | %1$d °C |
| `action_start` | Start | Start |
| `action_pause` | Pause | Pause |
| `action_stop` | Stopp | Stop |
| `speed_percent` | %1$d %% | %1$d %% |

`value_none` (em dash, fan subtitle) and `temp_unknown` (en dash) are different characters in the source — preserve both exactly.

### 3.3 Settings — `SettingsScreen.kt`

| Key | German | English |
|---|---|---|
| `set_status` | Status | Status |
| `state_idle` | Bereit | Ready |
| `state_running` | Läuft | Running |
| `state_paused` | Pausiert | Paused |
| `state_auto_stop` | Auto-Stopp | Auto-stop |
| `state_updating` | Wird aktualisiert… | Updating… |
| `state_done` | Fertig | Done |
| `state_error` | Fehler | Error |
| `set_section_configuration` | Konfiguration | Configuration |
| `set_completion_sound` | Ton bei Fertigstellung | Completion sound |
| `jingle_off` | Aus | Off |
| `jingle_simple` | Einfach | Simple |
| `jingle_glissando` | Glissando | Glissando |
| `jingle_star_wars` | Star Wars | Star Wars |
| `set_led_brightness` | LED Helligkeit | LED brightness |
| `set_respool_amount` | Respool-Menge | Respool amount |
| `set_use_filament_sensor` | Filament Sensor nutzen | Use filament sensor |
| `set_footer_filament_sensor` | Wenn der Sensor deaktiviert ist, wird nicht auf den Verlust von Filament reagiert. | When the sensor is disabled, loss of filament will not be detected. |
| `set_section_motor` | Motor | Motor |
| `set_reverse_direction` | Richtung umkehren | Reverse direction |
| `set_strength` | Stärke | Strength |
| `set_auto_stop_sensitivity` | Auto-Stopp Empfindlichkeit | Auto-stop sensitivity |
| `sensitivity_off` | Aus | Off |
| `sensitivity_low` | Gering | Low |
| `sensitivity_medium` | Mittel | Medium |
| `sensitivity_high` | Hoch | High |
| `set_footer_auto_stop` | Der Auto-Stopp stoppt den Motor bei Widerstand. | Auto-stop halts the motor when it meets resistance. |
| `set_high_speed` | High-Speed | High speed |
| `set_footer_high_speed` | Der High-Speed Modus erhöht die Geschwindigkeit des Motors. Auto-Stopp ist dabei nicht verfügbar. | High-speed mode increases the motor speed. Auto-stop is not available in this mode. |
| `set_section_fan` | Lüfter | Fan |
| `set_speed` | Geschwindigkeit | Speed |
| `set_fan_always_on` | Lüfter immer an | Fan always on |
| `set_temperature_unit` | Temperatur-Einheit | Temperature unit |
| `unit_celsius` | Celsius | Celsius |
| `unit_fahrenheit` | Fahrenheit | Fahrenheit |
| `set_footer_fan` | Der Lüfter schaltet sich standardmäßig 10 Sekunden nach stoppen des Respoolers aus. | By default the fan switches off 10 seconds after the respooler stops. |
| `set_section_calibration` | Kalibrierung | Calibration |
| `set_duration` | Dauer | Duration |
| `duration_value` | Dauer: %1$dm %2$ds | Duration: %1$dm %2$ds |
| `set_footer_calibration` | Für genauere Zeitangaben bzw. Respool-Mengen die benötigte Dauer für eine 1 kg Spule bei 80 % Geschwindigkeit messen und hier anpassen. | For more accurate time estimates and respool amounts, measure how long a 1 kg spool takes at 80 % speed and enter it here. |
| `set_section_app` | App | App |
| `set_notifications` | Benachrichtigungen | Notifications |
| `set_footer_notifications` | Erhalte Push-Benachrichtigungen, wenn der Respooler stoppt oder fertig ist. | Get push notifications when the respooler stops or finishes. |
| `percent_value` | %1$d %% | %1$d %% |

`percent_value` replaces the three separate `"${x.toInt()} %"` sites (LED, motor strength, fan speed) and can also serve `speed_percent` on the home screen — collapse to one key if the trailing-space rendering is identical (it is).

### 3.4 Respool amount screen — `SettingsScreen.kt`

| Key | German | English |
|---|---|---|
| `respool_title` | Respool-Menge | Respool amount |
| `common_back` | Zurück | Back |
| `respool_entire_spool` | Gesamte Spule | Entire spool |
| `weight_1_0kg` | 1,0 kg | 1.0 kg |
| `weight_0_5kg` | 0,5 kg | 0.5 kg |
| `weight_0_25kg` | 0,25 kg | 0.25 kg |
| `respool_footer_entire` | Der Respooler stoppt anhand des Filament Sensors, sobald die obere Spule leer ist. Empfohlen, wenn Filament zwischen zwei 1 kg Spulen übertragen wird. | The respooler stops based on the filament sensor as soon as the upper spool is empty. Recommended when transferring filament between two 1 kg spools. |
| `respool_footer_fixed` | Der Respooler stoppt anhand der übertragenen Menge. Empfohlen, wenn die obere Spule größer als 1 kg ist.\n\nDas Stoppen funktioniert auf Basis des dynamisch berechneten Fortschritts. Die Genauigkeit kann je nach Material variieren. | The respooler stops based on the amount transferred. Recommended when the upper spool is larger than 1 kg.\n\nStopping is based on dynamically calculated progress. Accuracy may vary by material. |

### 3.5 Connection — `ConnectionScreen.kt`

| Key | German | English |
|---|---|---|
| `conn_title` | Verbindung | Connection |
| `conn_scan_networks` | Netzwerk-Scan | Scan for networks |
| `conn_bluetooth` | Bluetooth | Bluetooth |
| `conn_wlan` | WLAN | Wi-Fi |
| `conn_error_short` | Fehler! | Error! |
| `conn_wlan_hint` | Für die WLAN-Verbindung hier nach verfügbaren Netzwerken suchen und das Passwort eingeben. Funktioniert nur mit 2,4 GHz Netzwerken. | To connect to Wi-Fi, scan for available networks here and enter the password. Works with 2.4 GHz networks only. |
| `conn_network` | Netzwerk | Network |
| `conn_select_count` | Auswählen (%1$d) | Select (%1$d) |
| `conn_password` | Passwort | Password |
| `conn_connecting` | Verbinden… | Connecting… |
| `conn_send_credentials` | Zugangsdaten senden | Send credentials |
| `conn_bluetooth_hint` | Bei Verbindungsproblemen das gespeicherte Gerät entfernen und die Verbindung neu aufbauen. | If you have connection problems, remove the saved device and reconnect. |
| `conn_connect` | Verbindung herstellen | Connect |
| `conn_forget_device` | Gespeichertes Gerät entfernen | Remove saved device |

Reuses `common_connected`, `common_disconnected`, `common_back` from §3.2/§3.4.

Note `2,4 GHz` → `2.4 GHz` in English (decimal separator).

### 3.6 Language picker (new)

| Key | German | English |
|---|---|---|
| `set_language` | Sprache | Language |
| `language_system` | Systemsprache | System default |
| `language_english` | English | English |
| `language_german` | Deutsch | Deutsch |
| `language_dialog_title` | Sprache wählen | Choose language |
| `common_cancel` | Abbrechen | Cancel |

Language names are endonyms (`Deutsch`, `English`) in **both** files — a picker that renders language names in the currently-active language is harder to use, not easier.

---

## 4. Not in scope — flag, do not fix

1. **`StepperRow` / `PickerRow` use `"–"` and `"+"` as button labels** with `contentDescription = null`. These are unlabelled for screen readers in every language. Accessibility fix, not localisation.
2. **`showFahrenheit` is stored but never read by `ContentScreen.tempLabel()`** — the temperature-unit setting has no effect on the home screen. Pre-existing bug; localising `temp_celsius` does not fix it and must not be conflated with it.
3. **German code comments** throughout `core/ble/**` and the UI files.
4. **Hardcoded `Color.White`** in `PillRow`/`ControlCard`/`NavigationBar` — breaks dark mode. Unrelated.
5. **`ContentScreen.rememberSmoothRotation`** is dead code alongside `formatSeconds`.

---

## 5. Verification

```bash
# build
./gradlew :app:assembleDebug

# lint — must report zero MissingTranslation
./gradlew :app:lintDebug

# confirm no German literals survive in Kotlin (comments will still match; check each hit is a comment)
```
Use `mcp_OurMCP_file_grep` with pattern `"[^"]*[äöüÄÖÜß][^"]*"` over `app/src/main/java` — every remaining hit must be inside a comment.

Manual checks on device/emulator:

| Check | How |
|---|---|
| English default | Fresh install with device set to a non-German locale → all UI English |
| German | Device locale `de-DE` → UI matches pre-change wording exactly, screen by screen |
| In-app picker | Settings → App → Language → English/Deutsch/System; UI switches immediately without restarting the app |
| Persistence | Kill and relaunch → chosen language retained |
| System integration (API 33+) | Settings → System → Languages → App languages → LTS Control lists English + Deutsch; changing it there updates the app and the in-app picker's subtitle |
| Backport (API 26–32) | Same picker behaviour on an API 26 emulator — this is the path most likely to break; test it explicitly |
| adb shortcut | `adb shell cmd locale set-app-locales com.lts.control --locales de-DE` (API 33+ only) |

**Regression risk to watch:** the `AppCompatActivity` + AppCompat-theme switch in Step 3 is the one change that can break app startup outright. Verify the app launches before touching any strings — do Steps 1–3 as one commit-sized unit and run it, then proceed to Steps 4–7.

---

## 6. Suggested work order

1. Steps 1–3 (deps, locale config, AppCompat migration) → build, launch, confirm nothing regressed.
2. Step 4 (both `strings.xml` files, complete, from the §3 tables) → build.
3. Step 5, one file at a time in this order: `LtsControlApp.kt` → `ContentScreen.kt` → `ConnectionScreen.kt` → `SettingsScreen.kt` (largest last) → build after each.
4. Step 6 (number formatting) folded into the `SettingsScreen.kt` pass.
5. Step 7 (picker) → build, test both API levels.
6. Step 8 (lint gate) → `lintDebug` clean.
