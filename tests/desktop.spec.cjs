const { test, expect, _electron } = require('@playwright/test');
const http = require('node:http');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
let fixture, address, app, shellPage, data;
test.beforeAll(async () => {
  fixture = http.createServer((req, res) => {
    if (req.url === '/api/login') {
      if (req.headers.authorization !== `Basic ${Buffer.from('demo:demo').toString('base64')}`) { res.writeHead(401); return res.end('{}'); }
      res.setHeader('Set-Cookie', 'romm_session=fixture-session; HttpOnly; SameSite=Lax; Max-Age=86400; Path=/'); res.setHeader('Content-Type', 'application/json'); return res.end('{}');
    }
    if (req.url === '/fixture-download') { res.setHeader('Content-Disposition', 'attachment; filename="fixture.txt"'); return res.end('authorized fixture content'); }
    res.setHeader('Content-Type', 'text/html');
    if (!req.headers.cookie?.includes('romm_session=fixture-session')) return res.end('<h1>RomM sign-in</h1>');
    res.end('<html><head><title>Fixture library</title></head><body class="v-theme--dark"><main class="v-application"><h1>My RomM library</h1><a href="/collections">Collections</a><a href="/fixture-download">Save fixture</a><input type="file" multiple><button id="blob">Save blob</button></main><script>document.getElementById("blob").onclick=()=>{const a=document.createElement("a");a.download="save.txt";a.href=URL.createObjectURL(new Blob(["fixture save"]));a.click()};</script></body></html>');
  });
  await new Promise((r) => fixture.listen(0, '127.0.0.1', r)); address = `http://127.0.0.1:${fixture.address().port}`;
});
test.afterAll(async () => { if (fixture) await new Promise((r) => fixture.close(r)); });
test.beforeEach(async () => {
  data = fs.mkdtempSync(path.join(os.tmpdir(), 'rommayo-desktop-'));
  app = await _electron.launch({ args: [...(process.env.CI ? ['--no-sandbox'] : []), path.join(__dirname, '..')], env: { ...process.env, ROMMAYO_USER_DATA_DIRECTORY: data } });
  shellPage = await app.firstWindow(); await shellPage.locator('#server').waitFor();
});
test.afterEach(async () => { if (app) await app.close(); fs.rmSync(data, { recursive: true, force: true }); });
async function fill(password = 'demo') {
  await shellPage.locator('#server').fill(address);
  await shellPage.locator('#username').fill('demo'); await shellPage.locator('#password').fill(password);
  await shellPage.locator('#consent').check(); await shellPage.locator('#http-consent').check();
}
async function remotePage() {
  await expect.poll(() => app.context().pages().filter((p) => p.url().startsWith(address)).length).toBe(1);
  return app.context().pages().find((p) => p.url().startsWith(address));
}
test('login transfers HttpOnly session to isolated full RomM view; uploads and navigation work', async () => {
  await fill(); await shellPage.locator('.connect-button').click();
  const remote = await remotePage(); await expect(remote.locator('h1')).toHaveText('My RomM library');
  expect(await remote.evaluate(() => [typeof window.rommayo, typeof require, typeof process])).toEqual(['undefined', 'undefined', 'undefined']);
  expect(await remote.evaluate(() => document.cookie)).not.toContain('fixture-session');
  await expect(shellPage.locator('#password')).toHaveValue('');
  expect(await remote.locator('.v-application').evaluate((e) => getComputedStyle(e).backgroundImage)).toContain('linear-gradient');
  await remote.getByText('Collections', { exact: true }).click(); await expect(remote).toHaveURL(`${address}/collections`);
  await remote.locator('input[type=file]').setInputFiles([{ name: 'homebrew.txt', mimeType: 'text/plain', buffer: Buffer.from('test upload') }]);
  expect(await remote.locator('input[type=file]').evaluate((e) => e.files[0].name)).toBe('homebrew.txt');
  const config = fs.readFileSync(path.join(data, 'servers.json'), 'utf8');
  expect(config).not.toContain('password'); expect(config).not.toContain('fixture-session');
  await shellPage.locator('#switch').click(); await expect(shellPage.locator('#onboarding')).toBeVisible();
  await shellPage.locator('#web-login').click(); const reopened = await remotePage();
  await expect(reopened.locator('h1')).toHaveText('My RomM library');
});
test('bad credentials show a readable error and never open a remote view', async () => {
  await fill('wrong'); await shellPage.locator('.connect-button').click();
  await expect(shellPage.locator('#error')).toContainText('RomM rejected');
  expect(app.context().pages().filter((p) => p.url().startsWith(address))).toHaveLength(0);
});
test('server IPC independently rejects HTTP without explicit acknowledgement', async () => {
  const error = await shellPage.evaluate(async (url) => {
    try { await window.rommayo.send({ type: 'connect', server: url, consent: true, allowHttp: false, webLogin: true }); }
    catch (e) { return e.message; }
  }, address);
  expect(error).toContain('unencrypted HTTP');
});
test('session cookies survive app restart and server passwords are not needed again', async () => {
  await fill(); await shellPage.locator('.connect-button').click(); await remotePage();
  await app.close();
  app = await _electron.launch({ args: [...(process.env.CI ? ['--no-sandbox'] : []), path.join(__dirname, '..')], env: { ...process.env, ROMMAYO_USER_DATA_DIRECTORY: data } });
  shellPage = await app.firstWindow(); await shellPage.locator('.saved-server').click();
  await shellPage.locator('#consent').check(); await shellPage.locator('#http-consent').check();
  await shellPage.locator('#web-login').click(); const remote = await remotePage();
  await expect(remote.locator('h1')).toHaveText('My RomM library');
});
test('HTTP and blob downloads use native save handling', async () => {
  await fill(); await shellPage.locator('.connect-button').click(); const remote = await remotePage();
  const output = path.join(data, 'saved-fixture.txt');
  const key = require('../desktop/policy.cjs').partitionFor(address);
  await app.evaluate(({ session }, { key, output }) => {
    session.fromPartition(key).on('will-download', (_e, item) => item.setSavePath(output));
  }, { key, output });
  await remote.getByText('Save fixture', { exact: true }).click();
  await expect.poll(() => fs.existsSync(output)).toBe(true); expect(fs.readFileSync(output, 'utf8')).toBe('authorized fixture content');
  fs.unlinkSync(output); await remote.locator('#blob').click();
  await expect.poll(() => fs.existsSync(output)).toBe(true); expect(fs.readFileSync(output, 'utf8')).toBe('fixture save');
});
