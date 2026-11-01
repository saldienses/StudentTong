package com.tihai.controller;
import com.tihai.utils.*;
import com.tihai.dubbo.pojo.course.Course;
import com.tihai.common.R;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.io.IOException;
import java.net.URLDecoder;

@RestController
@RequestMapping("/local/assessments")
public class AssessmentController {
 @Autowired private com.tihai.utils.AccountRegistry registry;@Autowired private ExamController exams;
 @Autowired private JdbcTemplate db;
 @Autowired private ApplicationContext context;
 private final Map<String,Entry> entries=new HashMap<>();
 private final Map<String,Map<String,Object>> cache=new HashMap<>();
 private final Map<String,Long> times=new HashMap<>();
 static String stableId(String account,String target){HttpUrl url=HttpUrl.parse(target);StringBuilder key=new StringBuilder(account);if(url!=null){for(String name:Arrays.asList("workId","workid","courseId","courseid","classId","clazzid")){String value=url.queryParameter(name);if(value!=null)key.append('|').append(name.toLowerCase()).append('=').append(value);}if(key.toString().equals(account))key.append('|').append(target);}else key.append('|').append(target);return UUID.nameUUIDFromBytes(key.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();}
 static class Entry {String account,url,type,courseId;Entry(String a,String u,String t){account=a;url=u;type=t;}}
 protected CourseUtil session(String account){
  String cookie=db.queryForObject("SELECT cookies FROM th_wk_user WHERE account=? LIMIT 1",String.class,account);
  CourseUtil util=new CourseUtil();context.getAutowireCapableBeanFactory().autowireBean(util);util.setAccount(account);util.setCookies(cookie);return util;
 }
 static String get(OkHttpClient http,String url)throws IOException{
  HttpUrl u=HttpUrl.parse(url);if(u==null||!u.isHttps()||!(u.host().equals("chaoxing.com")||u.host().endsWith(".chaoxing.com")))throw new IOException("不支持的学习通页面地址");
  try(Response r=http.newCall(new Request.Builder().url(u).get().build()).execute()){if(!r.isSuccessful()||r.body()==null)throw new IOException("学习通页面读取失败");String html=r.body().string();if(r.request().url().host().contains("passport")||html.contains("没有此页面访问权限"))throw new IOException("登录已过期或没有页面权限，请重新查询课程登录");return html;}
 }
 static String value(Document d,String id){Element e=d.getElementById(id);return e==null?"":e.val();}
 @GetMapping("/{type}") public synchronized R list(@PathVariable String type){
  if("exams".equals(type))return exams.list();
  if(!Arrays.asList("homework","exams").contains(type))return R.error(400,"不支持的模块");
  if(cache.containsKey(AccountScope.cacheKey()+type)&&System.currentTimeMillis()-times.getOrDefault(AccountScope.cacheKey()+type,0L)<60000)return R.ok().setData(enrich(cache.get(AccountScope.cacheKey()+type)));
  List<Map<String,Object>> items=new ArrayList<>();List<String> errors=new ArrayList<>();
  List<Map<String,Object>> accounts=com.tihai.utils.AccountScope.accounts(db);
  for(Map<String,Object> row:accounts){String account=String.valueOf(row.get("login_account"));try{
   CourseUtil util=session(account);OkHttpClient http=util.initSession(false,false);
   if(type.equals("exams")){
    Document d=Jsoup.parse(get(http,"https://mooc1-api.chaoxing.com/exam-ans/exam/test/examcode/examlist"));
    if(!d.title().contains("考试列表"))throw new IOException("考试列表页面结构未识别");
    for(Element tr:d.select("tr.dataTr")){org.jsoup.select.Elements cells=tr.select("td");if(cells.size()<9)throw new IOException("考试行结构发生变化");Map<String,Object> item=new LinkedHashMap<>();item.put("id",UUID.randomUUID().toString());item.put("title",cells.get(1).text());item.put("status","考试安排");item.put("detail",tr.text());item.put("actions",Collections.emptyList());items.add(item);}
   }else{
    Set<String> selected=new HashSet<>(db.queryForList("SELECT DISTINCT course_id FROM th_wk_queue WHERE login_account=?",String.class,account));
    for(Course course:util.getCourseList())if(selected.contains(course.getCourseId()))try{
     String middle=HttpUrl.parse("https://mooc1.chaoxing.com/visit/stucoursemiddle").newBuilder().addQueryParameter("courseid",course.getCourseId()).addQueryParameter("clazzid",course.getClazzId()).addQueryParameter("cpi",course.getCpi()).addQueryParameter("ismooc2","1").addQueryParameter("v","2").build().toString();
     Document md=Jsoup.parse(get(http,middle));String enc=value(md,"workEnc");if(enc.isEmpty())throw new IOException("课程作业签名未生成");
     String url=HttpUrl.parse("https://mooc1.chaoxing.com/mooc2/work/list").newBuilder().addQueryParameter("courseId",course.getCourseId()).addQueryParameter("classId",course.getClazzId()).addQueryParameter("cpi",course.getCpi()).addQueryParameter("ut","s").addQueryParameter("openc",value(md,"openc")).addQueryParameter("enc",enc).addQueryParameter("stuenc",value(md,"enc")).addQueryParameter("isdisplaytable","2").build().toString();
     Document d=Jsoup.parse(get(http,url));if(!d.title().contains("作业列表"))throw new IOException("作业列表页面结构未识别");
     for(Element li:d.select("li[onclick*=goTask][data]")){
      String target=URLDecoder.decode(li.attr("data"),"UTF-8");String id=stableId(account,target);Element title=li.selectFirst("p.overHidden2"),status=li.selectFirst("p.status");
      if(title==null||status==null)throw new IOException("作业条目结构发生变化");
      Entry entry=new Entry(account,target,type);entry.courseId=course.getCourseId();entries.put(id,entry);if(registry!=null)registry.resource("assessment",id,account);Map<String,Object> item=new LinkedHashMap<>();item.put("id",id);item.put("title",title.text());item.put("status",status.text());item.put("detail","已添加课程的独立作业 · 当前列表页");item.put("actions",Collections.singletonList("view"));items.add(item);
     }
    }catch(Exception e){errors.add("部分课程作业读取失败，请重新登录或检查课程权限");}
   }
  }catch(Exception e){errors.add("账号会话或列表读取失败，请重新查询课程登录");}}
  Map<String,Object> data=new LinkedHashMap<>();data.put("items",items);data.put("errors",errors);data.put("readOnly",!type.equals("homework"));data.put("message",(type.equals("homework")?"已添加课程的作业列表":"考试安排列表")+" · "+items.size()+" 项 · 60秒缓存"+(errors.isEmpty()?"":" · 存在读取失败，结果可能不完整"));
  cache.put(AccountScope.cacheKey()+type,data);times.put(AccountScope.cacheKey()+type,System.currentTimeMillis());return R.ok().setData(enrich(data));
 }
 @PostMapping("/detail") public synchronized R detail(@org.springframework.web.bind.annotation.RequestBody Map<String,String> body){
  Entry entry=entries.get(body.get("id"));if(entry==null)return R.error(404,"条目已过期，请刷新列表");if(!"homework".equals(entry.type))return R.error(400,"考试题目入口尚未验证，不会自动进入考试");
  try{String html=get(session(entry.account).initSession(false,false),entry.url);Map<String,Object> decoded=HomeworkRetake.submitted(html)?HomeworkRetake.read(html,entry.url):QuizPageReader.read(html);Map<String,Object> result=new LinkedHashMap<>();result.put("questions",decoded.get("questions"));result.put("alreadySubmitted",HomeworkRetake.submitted(html));return R.ok().setData(result);}catch(Exception e){return R.error(400,"题目读取失败或页面结构尚未支持，未生成或提交任何答案");}
 }

 @Autowired private HomeworkRetake retakes;
 @Autowired private ReviewStore reviewStore;
 @Autowired private Query answerQuery;
 @Autowired private LearningTongAiClient learningTongAi;
 @Autowired private LiveProgressTracker progress;
 @Autowired private QuizVisionService vision;
 private final java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"homework-answer-worker");t.setDaemon(true);return t;});
 private final Map<String,Job> jobs=new java.util.concurrent.ConcurrentHashMap<>();
 static class Job {
  volatile String state="generating",message="正在读取作业";volatile int total,done;final boolean autoSubmit;final Entry entry;final String id;
  volatile boolean retake;volatile String workingUrl;volatile Map<String,Object> retakePlan;
  volatile List<Map<String,Object>> questions=new ArrayList<>();volatile String originalSignature;volatile java.util.concurrent.Future<?> future;
  Job(String id,Entry e,boolean auto){this.id=id;entry=e;autoSubmit=auto;}
 }
 @javax.annotation.PreDestroy public void stopWorkers(){executor.shutdownNow();}
 private Map<String,Object> jobState(Job j){Map<String,Object> m=new LinkedHashMap<>();m.put("state",j.state);m.put("message",j.message);m.put("total",j.total);m.put("done",j.done);m.put("autoSubmit",j.autoSubmit);m.put("retake",j.retake);return m;}
 private Map<String,Object> enrich(Map<String,Object> data){
  for(Map<String,Object> item:(List<Map<String,Object>>)data.get("items")){Job j=jobs.get(String.valueOf(item.get("id")));if(j!=null)item.put("job",jobState(j));else item.remove("job");}return data;
 }
 @PostMapping("/answer") public synchronized R startAnswer(@org.springframework.web.bind.annotation.RequestBody Map<String,Object> body){
  String id=String.valueOf(body.get("id"));Entry entry=entries.get(id);if(entry==null||!"homework".equals(entry.type))return R.error(400,"没有可验证的作业入口；考试适配尚未验证");
  Job old=jobs.get(id);if(old!=null&&Arrays.asList("generating","ready","submitting","uncertain","completed").contains(old.state))return R.error(409,"已有草稿或执行记录，请先查看；待确认的提交不会自动重试");
  Job job=new Job(id,entry,Boolean.TRUE.equals(body.get("autoSubmit")));jobs.put(id,job);job.future=executor.submit(()->generate(job));return R.ok("自动答题已排队；只有本次勾选自动提交时才会尝试提交");
 }
 private void generate(Job job){
  String oldAccount=learningTongAi==null?null:learningTongAi.bindAccount(job.entry.account);
  progress.begin("homework:"+job.id);
  try{
   OkHttpClient http=session(job.entry.account).initSession(false,false).newBuilder().callTimeout(60,java.util.concurrent.TimeUnit.SECONDS).build();
   String html=get(http,job.entry.url);if(HomeworkRetake.submitted(html)){job.state="completed";job.message="平台已完成，未重复作答；可使用平台答案重测";return;}Map<String,Object> page=QuizPageReader.read(html);
   if(Boolean.TRUE.equals(page.get("alreadySubmitted"))){job.state="completed";job.message="平台已完成，未重复作答";return;}
   job.questions=(List<Map<String,Object>>)page.get("questions");job.total=job.questions.size();job.originalSignature=AssignmentDraft.signature(job.questions);progress.quiz("独立作业",job.total);
   vision.fill(html,page);
   for(Map<String,Object> q:job.questions){progress.checkCancelled();job.message="正在准备第 "+(job.done+1)+" / "+job.total+" 题";q.put("answer",AssignmentDraft.answer(answerQuery,q));job.done++;}
   progress.checkCancelled();job.state="ready";job.message="答案草稿已就绪，尚未提交";
   if(job.autoSubmit&&claimSubmit(job))submitJob(job);
  }catch(Exception e){if("stopped".equals(job.state)||Thread.currentThread().isInterrupted()||e instanceof java.util.concurrent.CancellationException){job.state="stopped";job.message="已停止，未自动提交剩余内容";}else{job.state="failed";job.message="第 "+(job.done+1)+" 题或页面处理失败；未自动提交，请检查识别、题库或 AI 接口";}}
  finally{if(learningTongAi!=null)learningTongAi.restoreAccount(oldAccount);progress.release();Thread.interrupted();}
 }
 @PostMapping("/answer/stop") public synchronized R stopAnswer(@org.springframework.web.bind.annotation.RequestBody Map<String,String> body){Job j=jobs.get(body.get("id"));if(j==null||!"generating".equals(j.state))return R.error(409,"当前没有可停止的答题任务；发送中的提交不能撤回");j.state="stopped";j.message="已请求停止";progress.cancel("homework:"+j.id);if(j.future!=null)j.future.cancel(true);return R.ok("已请求停止自动答题");}
 @PostMapping("/draft") public synchronized R draft(@org.springframework.web.bind.annotation.RequestBody Map<String,String> body){Job j=jobs.get(body.get("id"));if(j==null)return R.error(404,"草稿不存在或后台已重启，请重新生成");if("generating".equals(j.state)||"submitting".equals(j.state))return R.error(409,"正在处理，请完成后查看");Map<String,Object> data=jobState(j);data.put("questions",j.questions);return R.ok().setData(data);}
 @PostMapping("/submit") public synchronized R submitDraft(@org.springframework.web.bind.annotation.RequestBody Map<String,Object> body){
  Job j=jobs.get(String.valueOf(body.get("id")));if(j==null||!"ready".equals(j.state)||!Boolean.TRUE.equals(body.get("confirmed")))return R.error(409,"需要就绪草稿和明确提交确认；不会重复提交");
  if(!claimSubmit(j))return R.error(409,"提交状态已变化，请刷新");j.message="提交已排队";j.future=executor.submit(()->{progress.begin("homework:"+j.id);try{submitJob(j);}finally{progress.release();}});return R.ok("提交已排队，请等待平台状态确认；请勿重复提交");
 }
 private synchronized void invalidateHomework(){times.clear();}
 private boolean claimSubmit(Job j){synchronized(j){if(!"ready".equals(j.state))return false;j.state="submitting";return true;}}
 private void submitJob(Job j){
  boolean sent=false;
  try{
   progress.checkCancelled();j.state="submitting";j.message="正在核对最新题目与表单";
   OkHttpClient http=session(j.entry.account).initSession(false,false).newBuilder().retryOnConnectionFailure(false).callTimeout(60,java.util.concurrent.TimeUnit.SECONDS).build();
   String source=j.workingUrl==null?j.entry.url:j.workingUrl;String html=get(http,source);Map<String,Object> fresh=QuizPageReader.read(html);
   if(Boolean.TRUE.equals(fresh.get("alreadySubmitted"))){j.state="completed";j.message="平台已完成，无需再次提交";return;}
   if(!j.originalSignature.equals(AssignmentDraft.signature((List<Map<String,Object>>)fresh.get("questions"))))throw new IOException("题目变化，未提交");
   // Visual transcriptions are only used for answers; the original DOM signature guards form identity.
   List<Map<String,Object>> raw=(List<Map<String,Object>>)fresh.get("questions");
   for(int i=0;i<raw.size();i++)raw.get(i).put("answer",j.questions.get(i).get("answer"));
   Request request=AssignmentDraft.request(html,source,fresh,raw);progress.checkCancelled();
   okhttp3.Call call=http.newCall(request);progress.registerCall(call);
   try{sent=true;try(Response response=call.execute()){
    if(!response.isSuccessful()||response.body()==null)throw new IOException("提交结果未知");
    com.fasterxml.jackson.databind.JsonNode result=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body().string());
    if(!result.path("status").asBoolean(false)){j.state="uncertain";j.message="平台未接受本次请求，需核对原平台；不会自动重试";return;}
   }}finally{progress.unregisterCall(call);}
   boolean confirmed=false;
   for(int check=0;check<3&&!confirmed;check++){if(check>0)Thread.sleep(800);String after=get(http,source);confirmed=HomeworkRetake.submitted(after);if(!confirmed)try{confirmed=Boolean.TRUE.equals(QuizPageReader.read(after).get("alreadySubmitted"));}catch(Exception ignored){} }
   if(confirmed){j.state="completed";j.message="平台成绩页确认已完成";invalidateHomework();if(j.retake){j.retakePlan.put("stage","submitted");retakes.save(j.id,j.retakePlan);}}
   else{j.state="uncertain";j.message="平台已接收答案，但未确认最终交卷；可能仍需平台确认步骤，不会重试";}
  }catch(Exception e){j.state=sent?"uncertain":"ready";j.message=sent?"请求已发出但结果无法确认，请核对平台；不会自动重试":"提交前校验失败，未发送；请核对题型、题目与表单";}
 }


 private void archive(Entry entry,String id,Map<String,Object> plan)throws Exception{
  if(reviewStore==null||db==null)return;for(Map<String,Object> row:db.queryForList("SELECT id,course_name FROM th_wk_queue WHERE login_account=? AND course_id=?",entry.account,entry.courseId)){String course=String.valueOf(row.get("id"));reviewStore.course(course,String.valueOf(row.get("course_name")));reviewStore.add(course,"作业",String.valueOf(plan.get("title")),id,(List<Map<String,Object>>)plan.get("questions"),0);}
 }
 private Map<String,Object> preview(Entry entry,String id,String html)throws Exception{
  Map<String,Object> plan;
  if(HomeworkRetake.submitted(html)){plan=HomeworkRetake.read(html,entry.url);archive(entry,id,plan);Map<String,Object> previous=retakes.load(id);if(previous!=null&&"resetRequested".equals(previous.get("stage")))throw new IOException("上次重做结果未知，请核对平台，禁止重复消耗次数");}
  else{plan=retakes.load(id);if(plan==null)throw new IOException("尚无已保存的平台答案；请先完成一次作业并等待平台公布答案");plan.put("resume",true);HomeworkRetake.match((List<Map<String,Object>>)plan.get("questions"),(List<Map<String,Object>>)QuizPageReader.read(html).get("questions"));}
  return plan;
 }
 @PostMapping("/retake/preview") public synchronized R retakePreview(@org.springframework.web.bind.annotation.RequestBody Map<String,String> body){
  String id=body.get("id");Entry entry=entries.get(id);if(entry==null||!"homework".equals(entry.type))return R.error(404,"作业入口已过期，请刷新");try{Map<String,Object> plan=preview(entry,id,get(session(entry.account).initSession(false,false),entry.url));Map<String,Object> out=new LinkedHashMap<>(plan);out.remove("questions");boolean full=plan.get("total").equals(plan.get("available"))&&((Number)plan.get("total")).intValue()>0;out.put("canRetake",full&&(Boolean.TRUE.equals(plan.get("resume"))||Boolean.TRUE.equals(plan.get("redoAvailable"))&&((Number)plan.getOrDefault("remaining",1)).intValue()>0));return R.ok().setData(out);}catch(Exception e){return R.error(400,e instanceof IOException?e.getMessage():"平台答案读取失败，未重做、未提交");}
 }
 @PostMapping("/retake") public synchronized R startRetake(@org.springframework.web.bind.annotation.RequestBody Map<String,Object> body){
  String id=String.valueOf(body.get("id"));Entry entry=entries.get(id);if(entry==null||!"homework".equals(entry.type)||!Boolean.TRUE.equals(body.get("confirmed")))return R.error(400,"需要明确确认重做覆盖成绩");Job old=jobs.get(id);if(old!=null&&Arrays.asList("generating","ready","submitting","uncertain").contains(old.state))return R.error(409,"当前作业正在处理或有待核对草稿，请先处理");
  Job job=new Job(id,entry,Boolean.TRUE.equals(body.get("autoSubmit")));job.retake=true;jobs.put(id,job);job.future=executor.submit(()->retake(job));return R.ok("平台答案重测已排队；将覆盖原成绩，仅使用平台已公布答案");
 }
 private void retake(Job j){boolean resetSent=false;progress.begin("homework:"+j.id);try{
  OkHttpClient http=session(j.entry.account).initSession(false,false).newBuilder().retryOnConnectionFailure(false).callTimeout(60,java.util.concurrent.TimeUnit.SECONDS).build();String html=get(http,j.entry.url);Map<String,Object> plan=preview(j.entry,j.id,html);HomeworkRetake.complete(plan);j.retakePlan=plan;j.total=((Number)plan.get("total")).intValue();
  if(!Boolean.TRUE.equals(plan.get("resume"))){Request reset=HomeworkRetake.reset(html,j.entry.url);j.workingUrl=HomeworkRetake.editableUrl(html,j.entry.url);progress.checkCancelled();plan.put("stage","resetRequested");retakes.save(j.id,plan);j.state="submitting";j.message="正在请求重做（覆盖上次成绩），请勿重复操作";resetSent=true;
   try(Response response=http.newCall(reset).execute()){if(!response.isSuccessful()||response.body()==null)throw new IOException("重做结果无法确认");com.fasterxml.jackson.databind.JsonNode data=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body().string());if(data.path("status").asInt(0)!=1){plan.put("stage","rejected");retakes.save(j.id,plan);resetSent=false;throw new IOException("平台拒绝重做，可能次数耗尽或已截止");}}
   html=get(http,j.workingUrl);plan.put("stage","editable");retakes.save(j.id,plan);
  }else j.workingUrl=j.entry.url;
  Map<String,Object> page=QuizPageReader.read(html);if(Boolean.TRUE.equals(page.get("alreadySubmitted")))throw new IOException("未取得重做答题页面");j.questions=(List<Map<String,Object>>)page.get("questions");j.originalSignature=AssignmentDraft.signature(j.questions);HomeworkRetake.match((List<Map<String,Object>>)plan.get("questions"),j.questions);j.done=j.total;progress.checkCancelled();j.state="ready";j.message="平台公布答案已逐题匹配，草稿就绪";if(j.autoSubmit&&claimSubmit(j))submitJob(j);
 }catch(Exception e){if("stopped".equals(j.state)||e instanceof java.util.concurrent.CancellationException){j.state="stopped";j.message="已停止；已公布答案保存在本机";}else{j.state=resetSent?"uncertain":"failed";j.message=e instanceof IOException?e.getMessage():"平台答案重测处理失败，未自动交卷";}}finally{progress.release();Thread.interrupted();}}

 public synchronized List<Map<String,String>> reviewWorkLinks(String account,String courseId)throws IOException{
  R response=list("homework");Map<String,Object> data=(Map<String,Object>)response.get("data");if(data==null||!((List)data.get("errors")).isEmpty())throw new IOException("作业列表读取不完整");List<Map<String,String>> out=new ArrayList<>();
  for(Map<String,Object> item:(List<Map<String,Object>>)data.get("items")){String id=String.valueOf(item.get("id"));Entry e=entries.get(id);if(e!=null&&account.equals(e.account)&&courseId.equals(e.courseId)){Map<String,String> row=new LinkedHashMap<>();row.put("id",id);row.put("title",String.valueOf(item.get("title")));row.put("url",e.url);out.add(row);}}return out;
 }
}
