# Update metadata

- `data-v1.json` — the update manifest the client reads (see the client's
  `URI_UPDATES_EXTENSIONS`); APKs are attached to
  [GitHub Releases](https://github.com/dashchan-pixel/extensions/releases) of this
  repository (tag = version name). **Generated — do not hand-edit:** the `Release`
  workflow rewrites it from the APKs it just published, via
  `scripts/update_manifest.py`. It starts out with an empty `applications` list and
  is filled in by the first release.
- `source.json` — input for that generator: repository title, the release URL
  template and the applications to publish. This is the file to edit.
- Changelogs live in `metadata/<chan>/en/changelogs/<code>.txt` and become the body
  of the GitHub release.

## Why the manifest is generated

The client hides an update whose `fingerprint` does not exactly match the installed
extension's signing certificate, and rejects a package whose `sha256sum` does not
match the downloaded bytes. Both are therefore read back from the built APK
(`apksigner`) rather than written by hand — a manifest committed from a local
debug-signed build would advertise the debug certificate and silently update
nobody.
