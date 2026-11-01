// Render only course question areas. Scripts and all non-static network requests are disabled.
const fs=require('fs'),path=require('path');
const {chromium}=require(path.join(__dirname,'../runtime/vision-node/node_modules/playwright'));
let raw='';process.stdin.setEncoding('utf8');process.stdin.on('data',c=>{raw+=c;if(raw.length>16*1024*1024)process.exit(2)});
process.stdin.on('end',async()=>{
 let browser;
 const timeout=setTimeout(async()=>{if(browser)await browser.close().catch(()=>{});process.exit(3)},45000);
 try{
  const input=JSON.parse(raw);const root=path.resolve(input.outputDir);fs.mkdirSync(root,{recursive:true});
  browser=await chromium.launch({executablePath:input.edge,headless:true,timeout:15000,args:['--no-first-run','--disable-background-networking'],env:{...process.env,TEMP:root,TMP:root}});
  const page=await browser.newPage({viewport:{width:1100,height:900},javaScriptEnabled:false,deviceScaleFactor:1});
  await page.route('**/*',route=>{
   const r=route.request(),u=new URL(r.url());
   const allowed=['chaoxing.com','chaoxing.com.cn','chaoxingcdn.com','chaoxingcdn.com.cn','cldisk.com'].some(d=>u.hostname===d||u.hostname.endsWith('.'+d));
   return allowed&&u.protocol==='https:'&&r.method()==='GET'&&['stylesheet','image','font'].includes(r.resourceType())?route.continue():route.abort();
  });
  // No session cookies, scripts, user identifiers or browser chrome are included in the image sent to AI.
  const html=input.html.replace(/<head([^>]*)>/i,'<head$1><base href="https://mooc1.chaoxing.com/"><style>html,body{background:white!important}.singleQuesId{background:white!important;color:#111!important;max-width:1000px!important}</style>');
  await page.setContent(html,{waitUntil:'load',timeout:20000});
  await page.evaluate(()=>document.fonts.ready);
  const items=[];
  for(const id of input.ids){
   if(!/^\d+$/.test(id))throw Error('id');
   const q=page.locator('.singleQuesId[data="'+id+'"],#question'+id);
   if(await q.count()!==1)throw Error('question container');
   // Do not send a partial image if a question image failed to load.
   const ready=await q.evaluate(el=>[...el.querySelectorAll('img')].every(img=>img.complete&&img.naturalWidth>0));
   if(!ready)throw Error('image not loaded');
   const box=await q.boundingBox();if(!box||box.height<10||box.height>7000||box.width>8000)throw Error('question size');
   const filename=id+'.png';await q.screenshot({path:path.join(root,filename),timeout:5000});items.push({id,file:filename});
  }
  process.stdout.write(JSON.stringify({items}));
 }catch(e){process.stderr.write('题目截图失败：页面不可见、图片未加载或题号无法匹配。');process.exitCode=1;}
 finally{clearTimeout(timeout);if(browser)await browser.close();}
});
