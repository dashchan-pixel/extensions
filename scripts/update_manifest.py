#!/usr/bin/env python3
"""Regenerate update/data-v1.json from the built extension APKs.

Usage: update_manifest.py <tag> <manifest> [apk...]

update/source.json names the applications and the release URL template; each
extension's version comes from metadata/<name>/versions.json (newest entry,
which is also what the plugin compiles into the APK). Every APK is hashed and
its signing certificate read back with apksigner, so the published manifest
describes the exact binaries attached to the GitHub release.

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


def main():
	tag, manifest_path = sys.argv[1:3]
	apk_paths = sys.argv[3:]
	root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
	with open(os.path.join(root, 'update', 'source.json')) as f:
		source = json.load(f)
	apksigner = find_apksigner()

	apks = {os.path.basename(p): p for p in apk_paths}
	applications = []
	for application in source['applications']:
		name = application['name']
		archive = application['archive']
		version = latest_version(root, name)
		apk_path = apks.get(f'{archive}-release.apk')
		if apk_path is None:
			raise SystemExit(f'no APK passed for {name} ({archive}-release.apk)')
		with open(apk_path, 'rb') as f:
			data = f.read()
		sha256 = hashlib.sha256(data).hexdigest()
		fingerprints = read_fingerprint(apksigner, apk_path)
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
		applications.append({
			'name': name,
			'type': 'chan',
			'title': application['title'],
			'packages': [package],
		})

	manifest = {'title': source['title'], 'applications': applications}
	with open(manifest_path, 'w') as f:
		json.dump(manifest, f, indent='\t', ensure_ascii=False)
		f.write('\n')


if __name__ == '__main__':
	main()
