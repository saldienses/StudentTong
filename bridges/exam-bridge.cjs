/* Student exam browser bridge. Runs platform scripts; never fabricates verification or signatures. */
const fs=require('fs'),path=require('path'),readline=require('readline');
const {chromium}=require(path.join(process.env.STUDYDESK_ROOT||path.resolve(__dirname,'..'),'runtime/vision-node/node_modules/playwright'));
let browser,context,page,identity,total=0,closed=false,submissionAttempted=false;const answered=new Set();
function emit(value){process.stdout.write(JSON.stringify(value)+'\n');}
function safeError(e){return String(e.message||e).replace(/https?:\/\/\S+/g,'[页面]').slice(0,180);}
function trusted(url){const u=new URL(url);return u.protocol==='https:'&&u.hostname.endsWith('.chaoxing.com');}
async function currentId(){return page.locator('.singleQuesId').first().getAttribute('data');}
async function identityCheck(){const actual=await page.evaluate(()=>({courseId:document.getElementById('courseId')?.value,classId:document.getElementById('classId')?.value,examId:document.getElementById('testPaperId')?.value}));if(!trusted(page.url())||actual.courseId!==identity.courseId||actual.classId!==identity.classId||actual.examId!==identity.examId)throw new Error('考试身份变化，自动操作已停止');}
async function paper(){await identityCheck();emit({event:'paper',html:await page.content()});}
async function waitForPaper(){let waiting=false;const until=Date.now()+30*60*1000;while(Date.now()<until&&!closed){if(await page.locator('form#submitTest').count()&&await page.locator('.singleQuesId').count()&&new URL(page.url()).pathname.includes('reVersionTestStartNew')){await page.waitForLoadState('domcontentloaded');await identityCheck();const start=await page.locator('#start').inputValue().catch(()=> '0');if(start!=='0'){const canBack=await page.locator('#allowAnswerBack').inputValue().catch(()=> '0');if(canBack!=='1')throw new Error('本场考试不允许返回前题，当前进度需人工核对');await page.locator('li[onclick^="getTheQuestionByStart(0,"]').first().click();await page.waitForFunction(()=>document.getElementById('start')?.value==='0');}await paper();return;}
 const text=await page.locator('body').innerText().catch(()=> '');if(/已交卷|考试已提交|考试已完成/.test(text)&&!await page.locator('#startBtn').count()){emit({event:'completed',message:'平台显示考试已完成'});return;}
 if(/passport|login/.test(new URL(page.url()).pathname))throw new Error('考试登录已过期，请在学生通重新查询课程登录');
 if(!waiting&&/请完成安全验证|向右拖动|验证码|人脸识别|请使用.*客户端|请扫码/.test(text)){waiting=true;emit({event:'verification',message:'学习通要求安全验证，请在已打开的考试窗口完成验证；程序不会跳过验证'});}
 await page.waitForTimeout(500);
 }throw new Error('等待平台验证超时；考试计时可能继续，请核对平台');}
async function initialize(c){if(!trusted(c.url))throw new Error('不支持的考试地址');const u=new URL(c.url);if(u.pathname!=='/exam-ans/exam/test/examcode/examnotes'||u.searchParams.get('courseId')!==c.courseId||u.searchParams.get('classId')!==c.classId||u.searchParams.get('examId')!==c.examId)throw new Error('考试入口身份校验失败');identity=c;total=c.total||0;
 browser=await chromium.launch({headless:false,executablePath:c.edge,args:['--no-first-run']});context=await browser.newContext({viewport:{width:1280,height:850}});await context.addCookies(c.cookies);page=await context.newPage();page.setDefaultTimeout(15000);await page.goto(c.url,{waitUntil:'domcontentloaded',timeout:60000});
 const agreement=page.locator('.face_agreement');if(await agreement.count()&&!await page.locator('.face_agreement .agree_checked').count())await agreement.click();
 if(await page.locator('#startBtn').count()){const requiredCode=await page.locator('#needCode').inputValue().catch(()=> '0');if(requiredCode!=='1'){await page.locator('#startBtn').click();if(await page.locator('#confirmEnterWin .confirm').isVisible().catch(()=>false))await page.locator('#confirmEnterWin .confirm').click();}}
 await waitForPaper();
}
async function apply(c){await identityCheck();const qid=await currentId();if(qid!==c.id)throw new Error('当前题号变化，未填写答案');if(!['single','multiple','judgement'].includes(c.type))throw new Error('考试当前支持单选、多选和判断题');
 const q=page.locator('.singleQuesId[data="'+c.id+'"]');const targets=c.type==='judgement'?[c.answer]:c.answer.split('');
 const options=await q.locator('.stem_answer .answerBg').evaluateAll(nodes=>nodes.map(x=>{const n=x.querySelector('[data]');return {label:n?.getAttribute('data'),selected:n?.classList.contains('check_answer')||n?.classList.contains('check_answer_dx')||n?.classList.contains('num_option_checked')};}));
 if(targets.some(t=>!options.some(o=>o.label===t)))throw new Error('答案无法匹配当前考试选项');
 if(c.type==='multiple'){const before=await q.locator('input[name="answer'+c.id+'"]').inputValue();for(const o of options){const selected=before.includes(o.label),want=targets.includes(o.label);if(selected!==want)await q.locator('.stem_answer .answerBg').filter({has:page.locator('[data="'+o.label+'"]')}).click();}}
 else await q.locator('.stem_answer .answerBg').filter({has:page.locator('[data="'+targets[0]+'"]')}).click();
 const value=await q.locator('input[name="answer'+c.id+'"]').inputValue();if(value.split('').sort().join('')!==c.answer.split('').sort().join(''))throw new Error('平台答案输入未核验通过');answered.add(c.id);emit({event:'applied',id:c.id});
}
async function next(){const old=await currentId();const btn=page.locator('a[onclick="getTheNextQuestion(1)"]:visible');if(!await btn.count())throw new Error('未找到平台下一题按钮');await btn.click();await page.waitForFunction(id=>{const n=document.querySelector('.singleQuesId');return !!n&&n.getAttribute('data')!==id;},old,{timeout:30000});await paper();}
async function submit(c){if(submissionAttempted)throw new Error('本次交卷已尝试，不会重复发送');if(answered.size!==c.total||c.total<=0)throw new Error('题量未完成，未交卷');await identityCheck();
 const preview=page.locator('a[onclick="topreview()"]:visible');if(!await preview.count())throw new Error('未找到平台试卷预览入口');await preview.click();await page.waitForURL('**/mooc2/exam/preview**',{timeout:30000});await page.waitForLoadState('domcontentloaded');await page.waitForFunction(n=>document.querySelectorAll('.singleQuesId').length===n,c.total,{timeout:30000});
 const unfinished=await page.evaluate(()=>typeof checkUnAnswered==='function'?checkUnAnswered().unAnswerTypeName:'__unsupported__');if(unfinished)throw new Error('平台仍提示有未作答题目或预览结构未识别，未交卷');
 const ids=await page.locator('.singleQuesId').evaluateAll(nodes=>nodes.map(n=>n.getAttribute('data')));if(ids.length!==c.total||ids.some(id=>!answered.has(id)))throw new Error('交卷前整卷题号核验失败');
 await page.locator('a[onclick="finalSubmit();"]:visible').click();const confirm=page.locator('#submitConfirmPop .confirm');await confirm.waitFor({state:'visible',timeout:15000});
 submissionAttempted=true;emit({event:'submit_sent'});await confirm.click();
 const until=Date.now()+60000;while(Date.now()<until&&!closed){const result=await page.evaluate(()=>{const text=document.body?.innerText||'';const complete=!!document.querySelector('.testTit_status_complete,.subTime,.examResult')||/考试已提交|考试已完成|已成功交卷|提交成功|已交卷/.test(text);return {complete,path:location.pathname};});if(result.complete&&!await page.locator('#submitConfirmPop:visible').count()){emit({event:'completed',message:'平台结果页已确认交卷完成',html:await page.content()});return;}await page.waitForTimeout(500);}
 emit({event:'uncertain',message:'交卷请求已发出，平台最终状态尚未确认；不会自动重复交卷'});
}
async function close(){closed=true;await context?.close().catch(()=>{});await browser?.close().catch(()=>{});}
const rl=readline.createInterface({input:process.stdin,crlfDelay:Infinity});let chain=Promise.resolve();rl.on('line',line=>{let c;try{c=JSON.parse(line);}catch{emit({event:'error',message:'考试桥接命令无效'});return;}if(c.op==='close'){close().finally(()=>process.exit(0));return;}chain=chain.then(async()=>{try{if(c.op==='init')await initialize(c);else if(c.op==='apply')await apply(c);else if(c.op==='next')await next();else if(c.op==='submit')await submit(c);else throw new Error('不支持的考试命令');}catch(e){emit({event:submissionAttempted?'uncertain':'error',message:safeError(e)});}});});rl.on('close',()=>close().finally(()=>process.exit(0)));process.on('SIGTERM',()=>close().finally(()=>process.exit(0)));
