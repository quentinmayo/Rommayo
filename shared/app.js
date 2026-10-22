'use strict';
const $ = (id) => document.getElementById(id);
const send = (command) => {
  if (window.rommayo) return window.rommayo.send(command);
  if (window.rommayoBridge) { window.rommayoBridge.postMessage(JSON.stringify(command)); return Promise.resolve(); }
  return Promise.reject(new Error('Open Rommayo in its Android or desktop app.'));
};
function showError(message) { $('error').textContent = message; $('error').hidden = !message; }
function updateHttp() {
  const insecure = /^http:\/\//i.test($('server').value.trim());
  $('http-note').hidden = !insecure;
  $('http-consent-row').hidden = !insecure;
  $('http-consent').required = insecure;
  if (!insecure) $('http-consent').checked = false;
}
function render(state) {
  if (state.configuredServer && !$('server').value) { $('server').value = state.configuredServer; updateHttp(); }
  $('toolbar').hidden = !state.connected;
  $('onboarding').hidden = !!state.connected;
  document.body.classList.toggle('connected', !!state.connected);
  $('server-label').textContent = state.server || '';
  $('saved-section').hidden = !state.servers?.length;
  $('saved-servers').replaceChildren();
  for (const server of state.servers || []) {
    const button = document.createElement('button');
    button.className = 'saved-server'; button.textContent = server;
    button.title = state.configuredServer ? 'Select server' : 'Open saved session';
    button.addEventListener('click', () => {
      $('server').value = server; updateHttp();
      if (state.configuredServer) { $('consent').focus(); return; }
      // A native recent server was saved only after the user acknowledged its notices.
      $('consent').checked = true; $('http-consent').checked = /^http:\/\//i.test(server);
      connect(true);
    });
    $('saved-servers').append(button);
  }
  if (state.error) showError(state.error);
}
window.rommayoState = render;
if (window.rommayo) window.rommayo.onState(render);
if (window.rommayoBridge) window.rommayoBridge.onmessage = (event) => render(JSON.parse(event.data));
$('server').addEventListener('input', () => { $('http-consent').checked = false; updateHttp(); });
async function connect(webLogin = false) {
  showError('');
  const button = document.querySelector('.connect-button');
  button.disabled = true; $('connect-text').textContent = 'Connecting…';
  try {
    const state = await send({ type: 'connect', server: $('server').value, username: webLogin ? '' : $('username').value, password: webLogin ? '' : $('password').value, webLogin, consent: $('consent').checked, allowHttp: $('http-consent').checked, steamTheme: $('steam-theme').checked });
    if (state) { render(state); if (state.connected) $('password').value = ''; }
  } catch (error) { showError(error.message || 'Could not connect. Check the server address.'); }
  finally { button.disabled = false; $('connect-text').textContent = 'Connect & sign in'; }
}
$('connect-form').addEventListener('submit', (event) => {
  event.preventDefault(); connect();
});
$('web-login').addEventListener('click', () => {
  for (const id of ['server', 'consent', 'http-consent']) if (!$(id).reportValidity()) return;
  connect(true);
});
for (const type of ['back', 'home', 'reload', 'fullscreen']) $(type).addEventListener('click', () => send({ type }).catch((e) => showError(e.message)));
$('switch').addEventListener('click', () => send({ type: 'disconnect' }).then((s) => s && render(s)));
$('forget').addEventListener('click', () => send({ type: 'forget' }).then((s) => s && render(s)));
send({ type: 'init' }).then((state) => state && render(state)).catch((e) => showError(e.message));
