const { test } = require('node:test');
const assert = require('node:assert/strict');
const { normalizeServer, sameOrigin, partitionFor, isWebUrl } = require('../desktop/policy.cjs');
test('server addresses preserve LAN ports and reverse-proxy base paths', () => {
  assert.equal(normalizeServer('192.168.0.2:8095'), 'https://192.168.0.2:8095/');
  assert.equal(normalizeServer(' http://localhost:8095/romm/ '), 'http://localhost:8095/romm/');
  assert.equal(normalizeServer('https://ROmm.example.com///'), 'https://romm.example.com/');
});
test('server addresses reject dangerous schemes and embedded credentials', () => {
  for (const input of ['file:///etc/passwd', 'javascript://alert(1)', 'https://user:password@example.com', 'https://romm.example/#fragment', 'https://romm.example/?token=secret', '', 'not an address']) {
    assert.throws(() => normalizeServer(input), undefined, input);
  }
});
test('navigation checks use complete origins including ports', () => {
  assert(sameOrigin('https://romm.example/a', 'https://romm.example/b'));
  assert(!sameOrigin('http://romm.example', 'https://romm.example'));
  assert(!sameOrigin('http://localhost:8095', 'http://localhost:8096'));
  assert(!isWebUrl('file:///tmp/passwords'));
  assert(!isWebUrl('https://username:secret@example.com'));
});
test('each server gets a stable separate cookie partition', () => {
  assert.equal(partitionFor('https://romm.example/a'), partitionFor('https://romm.example/b'));
  assert.notEqual(partitionFor('https://romm.example'), partitionFor('https://another.example'));
  assert.notEqual(partitionFor('http://localhost:1'), partitionFor('http://localhost:2'));
  assert.match(partitionFor('https://romm.example'), /^persist:rommayo-[a-f0-9]{24}$/);
});
