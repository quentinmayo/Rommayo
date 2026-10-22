const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const crypto=require('node:crypto');
const {spawnSync}=require('node:child_process');
const installer=path.join(__dirname,'install.sh');
const source=fs.readFileSync(installer,'utf8');
const read=name=>source.match(new RegExp(`^${name}='([^']+)'`,'m'))[1];
const app=read('APP'),slug=read('SLUG'),version=read('VERSION');
function fixture(callback){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'mayo-install-'));
 try{
  const bin=path.join(dir,'tools'),prefix=path.join(dir,'app with spaces');fs.mkdirSync(bin);
  const executable=(name,text)=>fs.writeFileSync(path.join(bin,name),text,{mode:0o755});
  executable('uname','#!/bin/sh\ncase "$1" in -s) echo "${MAYO_TEST_OS:-Linux}";; -m) echo "${MAYO_TEST_ARCH:-x86_64}";; esac\n');
  executable('curl',`#!/usr/bin/env bash\nset -eu\nfor arg in "$@"; do case "$arg" in https:*) url="$arg";; esac; done\nwhile [ "$#" -gt 0 ]; do if [ "$1" = '-o' ]; then output="$2"; break; fi; shift; done\ncase "$url" in */SHA256SUMS.txt) cp "$MAYO_TEST_FIXTURES/SHA256SUMS.txt" "$output";; *.AppImage) cp "$MAYO_TEST_FIXTURES/app" "$output";; *) exit 22;; esac\n`);
  const content='#!/usr/bin/env bash\nif [ "${MAYO_CAPTURE_PRELOAD:-}" = 1 ]; then printf "%s" "${LD_PRELOAD-unset}"; exit; fi\nprintf "%s\\n" "$@"\n';fs.writeFileSync(path.join(dir,'app'),content);
  const hash=crypto.createHash('sha256').update(content).digest('hex');
  fs.writeFileSync(path.join(dir,'SHA256SUMS.txt'),`${hash}  ${app}-${version}-linux-x86_64.AppImage\n`);
  const env={...process.env,PATH:bin+path.delimiter+process.env.PATH,MAYO_INSTALL_PREFIX:prefix,MAYO_TEST_FIXTURES:dir};
  callback({dir,prefix,env,run:(args=[],extra={})=>spawnSync('bash',[installer,...args],{env:{...env,...extra},encoding:'utf8'})});
 }finally{fs.rmSync(dir,{recursive:true,force:true})}
}
test('verified Steam Deck install needs no sudo and preserves arguments and profile',{skip:process.platform === 'win32'},()=>fixture(({prefix,run})=>{
 const result=run(['--steam-deck']);assert.equal(result.status,0,result.stderr);
 const launcher=path.join(prefix,'bin',slug),entry=fs.readFileSync(path.join(prefix,'share','applications',slug+'.desktop'),'utf8');
 assert.match(entry,new RegExp(`Name=${app}`));assert.ok(entry.includes('Exec="'+launcher+'"'));
 assert.equal(spawnSync(launcher,['a file with spaces'],{encoding:'utf8'}).stdout,'--ozone-platform=x11\n--deck\na file with spaces\n');
 const profile=path.join(prefix,'share',slug,'test-profile');fs.writeFileSync(profile,'keep-progress');
 assert.equal(run(['--steam-deck']).status,0);assert.equal(fs.readFileSync(profile,'utf8'),'keep-progress');
 assert.ok(fs.existsSync(path.join(prefix,'share',slug,app+'.AppImage.previous')));
}));
test('checksum failure leaves installed app unchanged',{skip:process.platform === 'win32'},()=>fixture(({dir,prefix,run})=>{
 assert.equal(run().status,0);const target=path.join(prefix,'share',slug,app+'.AppImage'),original=fs.readFileSync(target);
 fs.writeFileSync(path.join(dir,'SHA256SUMS.txt'),`${'0'.repeat(64)}  ${app}-${version}-linux-x86_64.AppImage\n`);
 const result=run();assert.notEqual(result.status,0);assert.match(result.stderr,/Checksum mismatch/);assert.deepEqual(fs.readFileSync(target),original);
}));
test('dry run selects correct Mac architecture and rejects unsupported combinations',{skip:process.platform === 'win32'},()=>fixture(({run})=>{
 assert.match(run(['--dry-run'],{MAYO_TEST_OS:'Darwin',MAYO_TEST_ARCH:'arm64'}).stdout,/mac-arm64.dmg/);
 assert.match(run(['--dry-run'],{MAYO_TEST_OS:'Darwin',MAYO_TEST_ARCH:'x86_64'}).stdout,/mac-x64.dmg/);
 assert.notEqual(run(['--dry-run','--steam-deck'],{MAYO_TEST_OS:'Darwin'}).status,0);
 assert.notEqual(run(['--dry-run'],{MAYO_TEST_ARCH:'aarch64'}).status,0);
 assert.notEqual(run(['--surprise']).status,0);
}));

test('Deck launcher excludes injected libraries while desktop keeps its environment',{skip:process.platform === 'win32'},()=>fixture(({prefix,run})=>{
 const launcher=path.join(prefix,'bin',slug),options={env:{...process.env,LD_PRELOAD:'',MAYO_CAPTURE_PRELOAD:'1'},encoding:'utf8'};
 assert.equal(run(['--steam-deck']).status,0);assert.equal(spawnSync(launcher,[],options).stdout,'unset');
 assert.equal(run().status,0);assert.equal(spawnSync(launcher,[],options).stdout,'');
}));
