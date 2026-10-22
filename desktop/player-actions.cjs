'use strict';
const actions = new Set(['playPause','saveState','loadState','saveSavFiles','loadSavFiles','gamepad','settings','volumeUp','volumeDown','restart']);
function playerAction(action) {
  if (!actions.has(action)) throw new Error('Unknown player action.');
  // Click upstream controls so RomM's save hooks and EmulatorJS's netplay rules still run.
  return `(${function (action) {
    const engine=window.EJS_emulator;
    if (!engine?.started || !engine.elements?.bottomBar) return false;
    if(action==='volumeUp'||action==='volumeDown'){
      const slider=engine.elements.menu?.querySelector('input[data-range="volume"]');
      if(!slider || slider.disabled) return false;
      slider.value=String(Math.max(0,Math.min(1,Number(slider.value)+(action==='volumeUp'?0.1:-0.1))));
      slider.dispatchEvent(new Event('change',{bubbles:true}));return true;
    }
    const buttons=[engine.elements.bottomBar[action]].flat(Infinity);
    const button=buttons.find(b=>b?.tagName==='BUTTON' && !b.disabled && b.style.display!=='none' && getComputedStyle(b).display!=='none');
    if(!button) return false;
    button.click();return true;
  }.toString()})(${JSON.stringify(action)})`;
}
module.exports = { playerAction };
