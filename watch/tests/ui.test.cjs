const {chromium}=require(process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES ? process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES+'/playwright' : 'playwright');
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const assets=path.resolve(__dirname,'../app/src/main/assets');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.WATCH_TEST_CHROME,args:['--no-sandbox']});
 const context=await browser.newContext({viewport:{width:1280,height:800},acceptDownloads:true});
 const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
 let configured=false,signed=false,cameras=[{id:'usb',name:'Main camera',type:'usb',enabled:false,status:'Ready',live:false,lastFrame:0}];
 const state=()=>({cameras,free:5.9e9,internalFree:5.9e9,retentionDays:30,storage:'Tablet',addresses:['https://192.168.1.10:8443'],cloudConfigured:false,cloudStatus:'Cloud backup off',version:'0.1.0-beta'});
 await page.route('**/*',async route=>{const r=route.request(),u=new URL(r.url()),p=u.pathname;let status=200,j={};
 if(['/','/ui.js','/style.css'].includes(p)){const file=path.join(assets,p==='/'?'index.html':p.slice(1));return route.fulfill({status:200,contentType:p==='/ui.js'?'text/javascript':p==='/style.css'?'text/css':'text/html',body:fs.readFileSync(file)});}
 if(p==='/meta')j={configured,local:true};else if(p==='/setup'){configured=true;signed=true;j={csrf:'test-csrf'};}
 else if(p==='/login'){if(r.postDataJSON().password!=='test-password'){status=401;j={error:'Incorrect password'};}else{signed=true;j={csrf:'test-csrf'};}}
 else if(p==='/auth'){status=signed?200:401;j=signed?{csrf:'test-csrf'}:{error:'Please sign in'};}
 else if(p==='/state')j=state();else if(p==='/camera'){const b=r.postDataJSON();cameras.push({...b,id:'network-test',enabled:false,status:'Ready',live:false});j={id:'network-test'};}
 else if(p==='/toggle'){const b=r.postDataJSON();const c=cameras.find(c=>c.id===b.id);c.enabled=b.enabled;c.status=b.enabled?'Connect your USB camera':'Stopped';j={ok:true};}
 else if(p==='/recordings')j={recordings:[]};else if(p==='/frame'){status=404;j={error:'No frame'};}
 else if(p==='/logout'){signed=false;j={ok:true};}else{status=404;j={error:'Not found'};}
 return route.fulfill({status,contentType:'application/json',body:JSON.stringify(j)});
 });
 await page.goto('http://127.0.0.1:8765/',{waitUntil:'domcontentloaded'});await page.locator('#password').fill('test-password');await page.locator('#confirm').fill('different');await page.locator('#loginform button').click();await page.getByText('Passwords do not match',{exact:true}).waitFor();
 await page.locator('#confirm').fill('test-password');await page.locator('#loginform button').click();await page.getByRole('heading',{name:'A clear view of your space'}).waitFor();
 await page.locator('[data-toggle="usb"]').click();await page.getByRole('button',{name:'Stop recording',exact:true}).waitFor();
 await page.locator('[data-toggle="usb"]').click();await page.getByRole('button',{name:'Start recording',exact:true}).waitFor();
 await page.locator('#add-camera').click();await page.locator('#camera-name').fill('Garage <img src=x>');await page.locator('#camera-url').fill('rtsp://192.168.1.50:554/stream1');await page.locator('#camera-form button').click();await page.getByRole('heading',{name:'Garage <img src=x>',exact:true}).waitFor();
 assert.equal(await page.locator('img[src="x"]').count(),0,'camera names must be escaped');
 await page.locator('[data-page="recordings"]').click();await page.locator('#filter-date').fill('2026-10-25');await page.locator('#search').click();await page.getByText('No finalized recordings',{exact:false}).waitFor();
 await page.locator('[data-page="settings"]').click();await page.getByRole('heading',{name:'Your recorder address'}).waitFor();assert.equal(await page.locator('#storage').isDisabled(),true,'storage selection must be tablet-only');
 await page.locator('[data-page="live"]').click();await page.screenshot({path:path.resolve(__dirname,'../../tools/tablet-ui.png'),fullPage:true});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,'tablet overflow');
 await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.resolve(__dirname,'../../tools/mobile-ui.png'),fullPage:true});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,'mobile overflow');
 await page.locator('[data-page="settings"]').click();await page.locator('#settings-logout').click();await page.getByRole('heading',{name:'Welcome back'}).waitFor();await page.locator('#password').fill('wrong-password');await page.locator('#loginform button').click();await page.getByText('Incorrect password',{exact:true}).waitFor();
 assert.deepEqual(errors,[]);await browser.close();console.log('UI: setup, password mismatch, login failure, recording controls, camera naming/XSS, filters, settings, logout, tablet/mobile layout passed.');
})().catch(e=>{console.error(e);process.exit(1)});
