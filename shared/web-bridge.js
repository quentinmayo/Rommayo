'use strict';
window.rommayo = {
  onState() {},
  async send(command) {
    if (command.type === 'init') return (await fetch('/__rommayo/config', { cache: 'no-store' })).json();
    if (command.type === 'connect') {
      const response = await fetch('/__rommayo/connect', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(command) });
      const result = await response.json();
      if (!response.ok) throw new Error(result.error || 'Could not connect.');
      document.getElementById('password').value = '';
      if (result.redirect) window.location.assign(result.redirect);
      return;
    }
    if (command.type === 'forget' || command.type === 'logout') {
      const response = await fetch('/__rommayo/logout', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
      if (!response.ok) throw new Error('Could not log out. Retry while connected to Rommayo.');
      window.location.assign('/__rommayo/'); return;
    }
    throw new Error('This action is available in the native app.');
  }
};
