# Dashchan Extensions

The extension APKs for [dashchan-redacted/client](https://github.com/dashchan-redacted/client),
a subset of the upstream set. Each extension is a separate APK the client loads dynamically; the
ones this repository carries are the directories under `chans/`.

## Building Guide

1. Install JDK 17
2. Install the Android SDK, define the `ANDROID_HOME` environment variable or set `sdk.dir` in `local.properties`
3. Run `./gradlew :chans:%CHAN_NAME%:assembleRelease` (or `./gradlew assembleRelease` for all)

The resulting APK file will appear in the `chans/%CHAN_NAME%/build/outputs/apk` directory.

## Versions

`metadata/%CHAN_NAME%/versions.json` is the single source of truth for an extension's version:
the newest entry defines both `versionCode` and `versionName`, so a release bump is one atomic
edit and the built APK can never disagree with the published update manifest.

Each extension is versioned on its own. Version codes and names never have to agree between
extensions, because the update manifest records one entry per application and a release only
rewrites the entry it published.

## Build Signed Binary

Create `keystore.properties` in the source code directory:

```properties
store.file=%PATH_TO_KEYSTORE_FILE%
store.password=%KEYSTORE_PASSWORD%
key.alias=%KEY_ALIAS%
key.password=%KEY_PASSWORD%
```

Each value falls back to an environment variable when absent, which is how CI signs:
`KEYSTORE_FILENAME`, `KEYSTORE_PASSWORD`, `RELEASE_SIGN_KEY_ALIAS`, `RELEASE_SIGN_KEY_PASSWORD`
(the same names the client repo uses).

**Without a readable keystore, release builds are signed with the debug key** so they stay
installable for local development. Such an APK can never update a release-signed install, so
CI verifies every published APK against the release certificate rather than trusting the build
to have picked it up.

## Releasing

Extensions are released one at a time.

1. Append the new version to `metadata/%CHAN_NAME%/versions.json` and add
   `metadata/%CHAN_NAME%/en/changelogs/<code>.txt` (one atomic commit), push.
2. Push a tag named `%CHAN_NAME%-<version>` (e.g. `endchan-26.7.2`), where `<version>` is that
   extension's newest version name. The workflow refuses a tag that disagrees with it.
3. The `Release` workflow builds that one APK, verifies its signature, publishes it as its own
   GitHub release, and commits an `update/data-v1.json` (via `scripts/update_manifest.py`) in
   which only that application's entry changed, hashing the exact published binary.

Releasing several extensions means pushing several tags; nothing has to be released together.

`update/source.json` configures the manifest: repository title, the release URL template, and
the applications to publish along with the order they appear in. It stays authoritative for the
titles, so correcting one does not need a release.

## License

Extensions are available under the [GNU General Public License, version 3 or later](COPYING).
