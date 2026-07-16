# AGENTS.md

The **extensions** repository of the Dashchan rework — individual Android extension APKs (one per imageboard/engine, e.g., `fourchan`, `dvach`, `arhivach`, `local`) that link against the main app's SDK/API (`@Public` / `@Extendable`).

## Build

- Build with the **Gradle wrapper** directly: `./gradlew assembleRelease` / `assembleDebug` / `clean`, or per-extension (e.g., `./gradlew :chans:fourchan:assembleRelease`).
- Signing is handled automatically during extension builds via `keystore.properties` (or debug fallback) configured in `build-logic`.
- Gradle 9.6, AGP 9.2, Kotlin DSL (`build.gradle.kts` / `settings.gradle.kts`), Java 17 toolchain (`sourceCompatibility` / `targetCompatibility = JavaVersion.VERSION_17`). **100% Kotlin** across `build-logic`, `sdk`, `engines`, and `chans` (no `.java` files remain).
- minSdk / compile / target = **36** (Android 16).
- `allWarningsAsErrors` and `progressiveMode` are enabled — **the release and check builds must stay warning-clean.**

## Source map

- `sdk/**` — the **extension API surface** and shared utilities (`api`, `template-parser`, `uri-handler`). `sdk/api` employs a compilation `BuildConfig` shim (`Private` class injection) allowing extensions and engines to compile against the main app's `@Public` / `@Extendable` API contracts without bundling the client implementations. **Never change a `@Public` or `@Extendable` signature's nullability without matching the client.**
- `engines/**` — shared board/post/thread parsing engines (`foolfuuka`, `vichan`, `wakaba`) used by specific chan extensions.
- `chans/**` — individual extension APK projects (`fourchan`, `dvach`, `arhivach`, `local`). Each defines its `ChanLocator`, `ChanPerformer`, `ChanConfiguration`, and `ChanMarkup`.
- `build-logic/**` — custom Gradle convention plugins (`ExtensionPlugin` for `chans/`, `LibraryPlugin` for `sdk/` / `engines/`, and `ProjectConfiguration` for quality gates / JVM 17 compatibility).

## Git / workflow

- **`rework` is the default branch** (`origin/HEAD -> rework`). Push/publish only with explicit user OK.
- **`.gitignore` is an allowlist** (`/*` then `!/…`) — a new top-level file won't be tracked until it's explicitly allowlisted.

## Traps left by the Java→Kotlin conversion

Unlike the main app (`../client` which used JetBrains' J2K converter), **the Java→Kotlin conversion in the `extensions` repository was performed completely by AI**.

While the AI translation successfully migrated all `.java` files to Kotlin, it introduced specific patterns and traps that require careful handling:

- **Mechanical `!!` Non-Null Casts (`UnsafeCallOnNullableType`)**: The AI often inserted `!!` operators where the original Java either handled `null` gracefully or relied on platform/implicit nullability conventions — turning optional fallbacks into potential runtime crashes and making original null checks appear as "always true/false" warnings.
- **Never delete a "dead" null check or silence a warning with `!!`** without checking the contract and matching semantics. `!!` *is* correct where the original Java dereferenced unconditionally.
- **`!!` tracking:** `detektDoubleBang` tracks `!!` usage against baseline files (`detekt-baseline.xml` across `engines/` and `chans/`).
- **Decision rule:** When cleaning up `!!` or AI-generated residue, only replace `!!` if proven safe or if the API nullable contract allows it (`?.` / `?:` early return). Never turn an unguarded `x!!.foo()` into `x?.foo()` if that hides a required failure as a silent no-op. **Never change `@Public` / `@Extendable` signatures or nullability in `sdk/api`**, as extensions must strictly match the binary contract expected by the client app.

## Lint & inspections

- Use **`./gradlew check`** to verify all quality gates (`detekt`, `detektDoubleBang`, and Android `lint` across all modules).
- Rules are configured in `detekt.yml` and `detekt-doublebang.yml`.

## Testing

If a single device/emulator is connected (`adb`), you may use it to build/install/test. **Never delete or uninstall anything from it without asking**, and **do not otherwise manipulate the device** — it may be a real phone in use by a person.
