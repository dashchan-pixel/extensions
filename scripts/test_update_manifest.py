#!/usr/bin/env python3
"""Tests for update_manifest.py, run by `./gradlew check` (task manifestScriptTest).

This file exists because the same defect shipped three times. Reading the signing
certificate out of apksigner broke releases twice -- once on the label apksigner
prints, once on the line filter in front of it -- and each time the APK had already
been built, signed and published before the manifest step failed, so the gate that
would have caught it has to run somewhere other than a release.

Nothing here shells out to apksigner or Gradle: the point is to pin the parsing and
the merge, both of which are pure text handling, so the test runs anywhere python3
does. Expectations about the repository's own applications are derived from
update/source.json at runtime rather than written down, so cutting a release does
not invalidate the test.
"""
import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import types
import unittest

# Importing by path would leave scripts/__pycache__ behind, which .gitignore does not
# cover, so it would show up as untracked next to the script it was built from.
sys.dont_write_bytecode = True

SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT_DIR = os.path.dirname(SCRIPTS_DIR)


def load_module():
	path = os.path.join(SCRIPTS_DIR, 'update_manifest.py')
	spec = importlib.util.spec_from_file_location('update_manifest', path)
	module = importlib.util.module_from_spec(spec)
	spec.loader.exec_module(module)
	return module


um = load_module()

# Two distinguishable certificate digests. The first is the one every published
# extension is actually signed with, which makes a mismatch obvious in a failure.
CERT = '16f0d0d0895b3fc54d5ea172d798ca5e1db515a2c47b91f5b51b1df52a3e04c0'
OTHER = 'aa' * 32


class ReadFingerprintTest(unittest.TestCase):
	"""Which label apksigner prints is not something the repository controls.

	It is not even a property of the build-tools version: 36.0.0 prints "Signer #1"
	for an APK whose signature schemes all carry the same signer and switches to the
	per-scheme "V2 Signer:" form otherwise, so both spellings reach CI from the same
	pinned toolchain and both have to be read.
	"""

	def digests(self, output):
		fake = types.SimpleNamespace(
				run=lambda *args, **kwargs: types.SimpleNamespace(stdout=output))
		original = um.subprocess
		um.subprocess = fake
		try:
			return um.read_fingerprint('apksigner', 'fake.apk')
		finally:
			um.subprocess = original

	def test_per_scheme_label(self):
		"""The exact output that failed the dvach-26.7.2 release."""
		self.assertEqual(self.digests(
				'Verifies\n'
				'Verified using v2 scheme (APK Signature Scheme v2): true\n'
				'V2 Signer: certificate DN: C=US, O=Android, CN=Android Debug\n'
				f'V2 Signer: certificate SHA-256 digest: {CERT}\n'
				'V2 Signer: certificate SHA-1 digest: a1955d39f2625050da389ab9ac0e6548c3e3227a\n'
				'V2 Signer: certificate MD5 digest: 82a71d84250679c901f0746d4b2449fb\n'),
				[CERT])

	def test_numbered_label(self):
		self.assertEqual(self.digests(
				'Signer #1 certificate DN: CN=whoever\n'
				f'Signer #1 certificate SHA-256 digest: {CERT}\n'
				'Signer #1 certificate SHA-1 digest: a1955d39f262505\n'),
				[CERT])

	def test_sdk_range_parenthetical_is_one_signer(self):
		"""One certificate recorded per SDK range is still one signer."""
		self.assertEqual(self.digests(
				f'Signer #1 (minSdkVersion=24, maxSdkVersion=32) certificate SHA-256 digest: {CERT}\n'
				f'Signer #1 (minSdkVersion=33, maxSdkVersion=36) certificate SHA-256 digest: {CERT}\n'),
				[CERT])

	def test_same_certificate_across_schemes_is_one_signer(self):
		self.assertEqual(self.digests(
				f'V2 Signer: certificate SHA-256 digest: {CERT}\n'
				f'V3 Signer: certificate SHA-256 digest: {CERT}\n'),
				[CERT])

	def test_several_signers_are_all_reported(self):
		"""The client compares the whole signer set, so none may be dropped."""
		self.assertEqual(self.digests(
				f'Signer #1 certificate SHA-256 digest: {CERT}\n'
				f'Signer #2 certificate SHA-256 digest: {OTHER}\n'),
				[CERT, OTHER])

	def test_lineage_keys_are_excluded(self):
		"""Rotated-away keys are not what an installed extension was signed with."""
		self.assertEqual(self.digests(
				f'Signer #1 in lineage certificate SHA-256 digest: {OTHER}\n'
				f'Signer #1 certificate SHA-256 digest: {CERT}\n'),
				[CERT])

	def test_public_key_digest_is_not_the_certificate(self):
		"""-v adds a public key digest; the client hashes the certificate."""
		self.assertEqual(self.digests(
				f'Signer #1 public key SHA-256 digest: {OTHER}\n'
				f'Signer #1 certificate SHA-256 digest: {CERT}\n'),
				[CERT])

	def test_uppercase_hex_is_normalised(self):
		self.assertEqual(self.digests(
				f'Signer #1 certificate SHA-256 digest: {CERT.upper()}\n'), [CERT])

	def test_trailing_whitespace_tolerated(self):
		self.assertEqual(self.digests(
				f'Signer #1 certificate SHA-256 digest: {CERT}  \n'), [CERT])

	def test_no_digest_aborts(self):
		"""Never return empty: a manifest without a fingerprint installs for nobody."""
		with self.assertRaises(SystemExit):
			self.digests('Verifies\nDOES NOT VERIFY\n')

	def test_truncated_digest_is_not_accepted(self):
		with self.assertRaises(SystemExit):
			self.digests('Signer #1 certificate SHA-256 digest: 16f0d0d0\n')

	def test_failure_message_quotes_the_output(self):
		"""So the next format change is diagnosable from the run log alone."""
		with self.assertRaises(SystemExit) as caught:
			self.digests('Signer #1 certificate DN: CN=whoever\n')
		self.assertIn('CN=whoever', str(caught.exception))


class GroupHexTest(unittest.TestCase):
	def test_grouped_uppercase(self):
		self.assertEqual(um.group_hex('aabbcc'), 'AA:BB:CC')

	def test_client_rejects_anything_but_64_hex_bytes(self):
		grouped = um.group_hex(CERT)
		self.assertEqual(len(grouped.split(':')), 32)
		self.assertEqual(grouped, grouped.upper())


class LatestVersionTest(unittest.TestCase):
	def test_highest_code_wins_not_file_order(self):
		with tempfile.TemporaryDirectory() as tmp:
			path = os.path.join(tmp, 'metadata', 'demo')
			os.makedirs(path)
			with open(os.path.join(path, 'versions.json'), 'w') as f:
				json.dump({'versions': [
					{'code': 3, 'name': 'newest'},
					{'code': 1, 'name': 'oldest'},
					{'code': 2, 'name': 'middle'},
				]}, f)
			self.assertEqual(um.latest_version(tmp, 'demo'), {'code': 3, 'name': 'newest'})

	def test_every_published_extension_parses(self):
		"""versions.json is what the plugin compiles in, so it must stay readable."""
		with open(os.path.join(ROOT_DIR, 'update', 'source.json')) as f:
			source = json.load(f)
		for application in source['applications']:
			version = um.latest_version(ROOT_DIR, application['name'])
			self.assertIsInstance(version['code'], int)
			self.assertTrue(version['name'])


class MergeTest(unittest.TestCase):
	"""main() rewrites one application and must leave every other one untouched.

	Extensions are released independently, so a run for one of them is the only thing
	that may change its entry -- and must not disturb the entries describing releases
	it knows nothing about.
	"""

	def setUp(self):
		with open(os.path.join(ROOT_DIR, 'update', 'source.json')) as f:
			self.source = json.load(f)
		self.names = [a['name'] for a in self.source['applications']]
		self.tmp = tempfile.mkdtemp()
		self.addCleanup(shutil.rmtree, self.tmp)
		self.apk = os.path.join(self.tmp, 'fake.apk')
		with open(self.apk, 'wb') as f:
			f.write(b'not really an apk, but it is bytes with a length and a hash')
		self.manifest = os.path.join(self.tmp, 'data-v1.json')

	def run_main(self, tag, name, fingerprints=(CERT,)):
		original_argv, original_read = sys.argv, um.read_fingerprint
		sys.argv = ['update_manifest.py', tag, self.manifest, name, self.apk]
		um.read_fingerprint = lambda apksigner, apk: list(fingerprints)
		um.find_apksigner = lambda: 'apksigner'
		try:
			um.main()
		finally:
			sys.argv, um.read_fingerprint = original_argv, original_read
		with open(self.manifest) as f:
			return json.load(f)

	def entry(self, manifest, name):
		return next(a for a in manifest['applications'] if a['name'] == name)

	def test_first_release_describes_the_apk(self):
		name = self.names[0]
		version = um.latest_version(ROOT_DIR, name)
		with open(self.apk, 'rb') as f:
			data = f.read()
		manifest = self.run_main(f'{name}-{version["name"]}', name)
		self.assertEqual([a['name'] for a in manifest['applications']], [name])
		package = self.entry(manifest, name)['packages'][0]
		# Both are read back from the bytes rather than configured: the client rejects a
		# package whose sha256sum does not match what it downloaded.
		self.assertEqual(package['length'], len(data))
		self.assertEqual(package['sha256sum'],
				um.group_hex(hashlib.sha256(data).hexdigest()))
		self.assertEqual(package['fingerprint'], um.group_hex(CERT))
		self.assertEqual(package['version_code'], version['code'])
		self.assertEqual(package['version_name'], version['name'])
		# The client only auto-selects a package titled exactly "Release".
		self.assertEqual(package['title'], 'Release')
		self.assertIn(f'/{name}-{version["name"]}/', package['source'])

	def test_releasing_one_leaves_the_others_byte_identical(self):
		first, second = self.names[0], self.names[1]
		self.run_main(f'{first}-{um.latest_version(ROOT_DIR, first)["name"]}', first)
		with open(self.manifest) as f:
			before = self.entry(json.load(f), first)
		after = self.entry(
				self.run_main(f'{second}-{um.latest_version(ROOT_DIR, second)["name"]}', second),
				first)
		self.assertEqual(before, after)

	def test_order_follows_source_and_omits_unreleased(self):
		released = [self.names[2], self.names[0]]
		for name in released:
			manifest = self.run_main(
					f'{name}-{um.latest_version(ROOT_DIR, name)["name"]}', name)
		# Published in source.json order regardless of which was released when, so the
		# file does not churn, and applications never released have no entry to keep.
		self.assertEqual([a['name'] for a in manifest['applications']],
				[n for n in self.names if n in released])

	def test_titles_are_refreshed_from_source(self):
		"""So a title can be corrected without cutting a release."""
		name = self.names[0]
		tag = f'{name}-{um.latest_version(ROOT_DIR, name)["name"]}'
		self.run_main(tag, name)
		with open(self.manifest) as f:
			stale = json.load(f)
		self.entry(stale, name)['title'] = 'stale title'
		with open(self.manifest, 'w') as f:
			json.dump(stale, f)
		manifest = self.run_main(tag, name)
		expected = next(a for a in self.source['applications'] if a['name'] == name)
		self.assertEqual(self.entry(manifest, name)['title'], expected['title'])

	def test_single_signer_uses_the_singular_key(self):
		name = self.names[0]
		package = self.entry(self.run_main(
				f'{name}-{um.latest_version(ROOT_DIR, name)["name"]}', name),
				name)['packages'][0]
		self.assertIn('fingerprint', package)
		self.assertNotIn('fingerprints', package)

	def test_several_signers_use_the_plural_key(self):
		name = self.names[0]
		package = self.entry(self.run_main(
				f'{name}-{um.latest_version(ROOT_DIR, name)["name"]}', name,
				fingerprints=(CERT, OTHER)), name)['packages'][0]
		self.assertEqual(package['fingerprints'],
				[um.group_hex(CERT), um.group_hex(OTHER)])
		self.assertNotIn('fingerprint', package)

	def test_unknown_extension_is_refused(self):
		with self.assertRaises(SystemExit):
			self.run_main('nosuch-1.0', 'nosuch')

	def test_output_survives_the_json_gate(self):
		"""CI commits this file, and the pre-commit hook rejects other formatting."""
		if shutil.which('jq') is None:
			self.skipTest('jq is not installed')
		name = self.names[0]
		self.run_main(f'{name}-{um.latest_version(ROOT_DIR, name)["name"]}', name)
		with open(self.manifest, 'rb') as f:
			written = f.read()
		formatted = subprocess.run(['jq', '--indent', '4', '.', self.manifest],
				check=True, capture_output=True).stdout
		self.assertEqual(written, formatted)


if __name__ == '__main__':
	unittest.main(verbosity=2)
