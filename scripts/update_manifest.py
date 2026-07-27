#!/usr/bin/env python3
"""Merge one released extension into update/data-v1.json.

Usage: update_manifest.py <tag> <manifest> <name> <apk>

Extensions are released independently, so this rewrites a single application's
entry and leaves every other one exactly as its own last release left it. The
released extension's version comes from metadata/<name>/versions.json (newest
entry, which is also what the plugin compiles into the APK); the APK is hashed
and its signing certificate read back with apksigner, so the published manifest
describes the exact binary attached to the GitHub release.

update/source.json names the applications and the release URL template, and
stays authoritative for the manifest title, the publishing order and every
application title, so those can be corrected without cutting a release.

The client rejects a package silently if the fingerprint does not match the
installed extension's signing certificate, or if sha256sum/fingerprint are not
64 hex characters, so both are derived from the APK rather than configured.
"""
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys


def find_apksigner():
	found = shutil.which('apksigner')
	if found:
		return found
	sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
	if sdk:
		build_tools = os.path.join(sdk, 'build-tools')
		if os.path.isdir(build_tools):
			# Newest build-tools wins; any version can print certificates.
			for version in sorted(os.listdir(build_tools), reverse=True):
				candidate = os.path.join(build_tools, version, 'apksigner')
				if os.path.isfile(candidate):
					return candidate
	raise SystemExit('apksigner not found; set ANDROID_HOME or put it on PATH')


def read_fingerprint(apksigner, apk_path):
	"""SHA-256 of the signing certificate, which is what ChanManager compares."""
	output = subprocess.run([apksigner, 'verify', '--print-certs', apk_path],
			check=True, capture_output=True, text=True).stdout
	digests = re.findall(r'Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]{64})', output)
	if not digests:
		raise SystemExit(f'{apk_path}: apksigner printed no SHA-256 certificate digest')
	# The client compares the whole signer set, so every signer has to be listed.
	return [d.lower() for d in digests]


def group_hex(value):
	return ':'.join(value[i:i + 2] for i in range(0, len(value), 2)).upper()


def latest_version(root, name):
	path = os.path.join(root, 'metadata', name, 'versions.json')
	with open(path) as f:
		versions = json.load(f)['versions']
	return max(versions, key=lambda v: v['code'])


def read_published(manifest_path):
	"""The entries of the previous manifest, keyed by application name.

	Missing or empty is the first-release case, not an error.
	"""
	if not os.path.exists(manifest_path):
		return {}
	with open(manifest_path) as f:
		manifest = json.load(f)
	return {a['name']: a for a in manifest.get('applications', [])}


def main():
	tag, manifest_path, name, apk_path = sys.argv[1:5]
	root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
	with open(os.path.join(root, 'update', 'source.json')) as f:
		source = json.load(f)
	application = next((a for a in source['applications'] if a['name'] == name), None)
	if application is None:
		known = ', '.join(a['name'] for a in source['applications'])
		raise SystemExit(f'unknown extension {name}; update/source.json has {known}')

	archive = application['archive']
	version = latest_version(root, name)
	with open(apk_path, 'rb') as f:
		data = f.read()
	sha256 = hashlib.sha256(data).hexdigest()
	fingerprints = read_fingerprint(find_apksigner(), apk_path)
	package = {
		# The client only auto-selects a package titled exactly "Release".
		'title': 'Release',
		'version_name': version['name'],
		'version_code': version['code'],
		# Chan entries are gated on api_version alone; min/max_api_version are ignored.
		'api_version': source['api_version'],
		'min_sdk': source['min_sdk'],
		'length': len(data),
		'source': source['source_template'].format(
				tag=tag, archive=archive, code=version['code']),
		'sha256sum': group_hex(sha256),
	}
	if len(fingerprints) == 1:
		package['fingerprint'] = group_hex(fingerprints[0])
	else:
		package['fingerprints'] = [group_hex(f) for f in fingerprints]

	published = read_published(manifest_path)
	published[name] = {
		'name': name,
		'type': 'chan',
		'title': application['title'],
		'packages': [package],
	}
	# Applications never released yet have no entry to keep; source.json orders the rest so
	# the file stays stable no matter which extension was released.
	applications = []
	for entry in source['applications']:
		current = published.get(entry['name'])
		if current is not None:
			current['title'] = entry['title']
			applications.append(current)

	manifest = {'title': source['title'], 'applications': applications}
	with open(manifest_path, 'w') as f:
		json.dump(manifest, f, indent='\t', ensure_ascii=False)
		f.write('\n')


if __name__ == '__main__':
	main()
