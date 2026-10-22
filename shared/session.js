"use strict";
// Same-origin companion action. Never clear IndexedDB or local game saves.
const logout = document.createElement('button');
logout.type = 'button'; logout.textContent = 'Log out · Rommayo';
logout.setAttribute('aria-label', 'Log out of RomM');
Object.assign(logout.style, {position:'fixed',right:'12px',bottom:'12px',zIndex:'2147483647',padding:'12px 16px',border:'1px solid #67c4ff',borderRadius:'6px',color:'#ecf5ff',background:'#1b2838',font:'14px system-ui',cursor:'pointer'});
logout.onclick = async () => {
 if (!confirm('Save your game before logging out. End this device’s session? Local game saves are preserved.')) return;
 logout.disabled = true;
 try { const response = await fetch('/__rommayo/logout', {method:'POST',headers:{'Content-Type':'application/json'},body:'{}'}); if (!response.ok) throw Error(); location.assign('/__rommayo/'); }
 catch { logout.disabled=false; alert('Could not log out. Check your connection and retry.'); }
};
document.body.append(logout);
