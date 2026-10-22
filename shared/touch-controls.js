
'use strict';
function bindPlayerMenu(root, toggle, isOpen) {
  const buttons=()=>[...root.querySelectorAll('button')].filter(b=>!b.disabled && b.offsetParent!==null);
  let selected=0;
  function move(delta){const all=buttons();if(!all.length)return;const active=all.indexOf(document.activeElement);selected=((active<0?selected:active)+delta+all.length)%all.length;all[selected].focus();}
  function select(){const all=buttons();const active=all.includes(document.activeElement)?document.activeElement:all[0];active?.click();}
  document.addEventListener('keydown',event=>{if(event.key==='F10'){event.preventDefault();toggle(!isOpen());return;}if(!isOpen())return;if(['ArrowLeft','ArrowUp','ArrowRight','ArrowDown','Escape'].includes(event.key)){event.preventDefault();if(event.key==='Escape')toggle(false);else move(['ArrowLeft','ArrowUp'].includes(event.key)?-1:1);}});
  let previous=new Map();
  setInterval(()=>{
    const current=new Map();
    for(const pad of navigator.getGamepads?.()||[]){
      if(!pad || pad.mapping!=='standard')continue;
      const pressed=pad.buttons.map(b=>b.pressed),old=previous.get(pad.index)||[];
      current.set(pad.index,pressed);
      if(pressed[10]&&pressed[11]&&!(old[10]&&old[11])){toggle(!isOpen());continue;}
      if(!isOpen())continue;
      if(pressed[1]&&!old[1])toggle(false);
      else if(pressed[0]&&!old[0])select();
      else if((pressed[12]&&!old[12])||(pressed[14]&&!old[14]))move(-1);
      else if((pressed[13]&&!old[13])||(pressed[15]&&!old[15]))move(1);
    }
    previous=current;
  },50);
  return ()=>{selected=0;buttons()[0]?.focus();};
}
