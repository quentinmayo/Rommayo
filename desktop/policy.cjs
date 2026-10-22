'use strict';
const crypto = require('node:crypto');

function normalizeServer(input) {
  if (typeof input !== 'string' || input.length > 2048) throw new Error('Enter a valid RomM server address.');
  const value = input.trim();
  const url = new URL(value.includes('://') ? value : `https://${value}`);
  if (!['https:', 'http:'].includes(url.protocol) || !url.hostname || url.username || url.password || url.search || url.hash) {
    throw new Error('Use an HTTP or HTTPS address without credentials, query parameters or fragments.');
  }
  // Keep reverse-proxy base paths. The server is responsible for supporting its base path.
  url.pathname = url.pathname.replace(/\/+$/, '') + '/';
  return url.href;
}
function isWebUrl(value) {
  try { const u = new URL(value); return ['http:', 'https:'].includes(u.protocol) && !u.username && !u.password; }
  catch { return false; }
}
function sameOrigin(a, b) {
  try { return new URL(a).origin === new URL(b).origin; } catch { return false; }
}
function partitionFor(server) {
  return `persist:rommayo-${crypto.createHash('sha256').update(new URL(server).origin).digest('hex').slice(0, 24)}`;
}
module.exports = { normalizeServer, isWebUrl, sameOrigin, partitionFor };
