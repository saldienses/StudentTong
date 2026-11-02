package com.tihai.controller;
import com.tihai.utils.*;import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import org.springframework.test.util.ReflectionTestUtils;import okhttp3.*;import java.util.*;import java.nio.file.*;import java.util.concurrent.atomic.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class HomeworkRetakeFlowTest {
 @TempDir Path temp;
 static final String URL="https://mooc1.chaoxing.com/mooc-ans/mooc2/work/view?courseId=2&classId=3&cpi=4&workId=5&answerId=6";
 static String reviewed(String correct){return "<input id=courseId value=2><input id=classId value=3><input id=cpi value=4><input id=workId value=5><input id=answerId value=6><div class=fanyaMarking><span class=resultNum>80分</span><a class=redo onclick='redoWork()'>重做</a><div class=singleQuesId data=1><h3 class=mark_name>1. (单选题) Example</h3><ul><li>A. Alpha</li><li>B. Beta</li></ul><span>正确答案:<span class=rightAnswerContent>"+correct+"</span></span></div></div><script>function redoWork(){var redoTimes=5+0-1;var url='/work/phone/redo';location.href='/mooc-ans/mooc2/work/dowork?courseId=2&classId=3&cpi=4&workId=5&answerId=6&enc=fixture';}</script>";}
 static String edit(){return "<form id=submitForm method=post action='/mooc-ans/work/addStudentWorkNewWeb'><input type=hidden name=questionIds value=''><input type=hidden name=pyFlag value=1><input name=answertype99 value=0><div class=singleQuesId data=99><h3 class=mark_name>(单选题) Example</h3><ul><li>A. Beta</li><li>B. Alpha</li></ul></div></form>";}
 class Fixture extends AssessmentController implements AutoCloseable {
  AtomicInteger resets=new AtomicInteger(),posts=new AtomicInteger();CourseUtil util=mock(CourseUtil.class);boolean missing,timeout,changed;Query query=mock(Query.class);FormBody body;
  Fixture(boolean missing,boolean timeout,boolean changed){this.missing=missing;this.timeout=timeout;this.changed=changed;
   OkHttpClient http=new OkHttpClient.Builder().addInterceptor(chain->{Request req=chain.request();String data;
    if(req.url().encodedPath().endsWith("/redo")){resets.incrementAndGet();if(this.timeout)throw new java.io.IOException("fixture ambiguous reset");data="{\"status\":1}";}
    else if("POST".equals(req.method())){posts.incrementAndGet();body=(FormBody)req.body();data="{\"status\":true}";}
    else if(posts.get()>0||resets.get()==0)data=reviewed(this.missing?"":"B");else data=this.changed?edit().replace("Beta","Changed"):edit();
    return new Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create(data,MediaType.parse("text/html"))).build();}).build();
   when(util.initSession(false,false)).thenReturn(http);ReflectionTestUtils.setField(this,"progress",new LiveProgressTracker());ReflectionTestUtils.setField(this,"retakes",new HomeworkRetake(temp));ReflectionTestUtils.setField(this,"answerQuery",query);((Map)ReflectionTestUtils.getField(this,"entries")).put("work",new Entry("fixture",URL,"homework"));
  }
  @Override protected CourseUtil session(String account){return util;}
  Map<String,Object> waitFor(String state)throws Exception{long end=System.currentTimeMillis()+6000;while(System.currentTimeMillis()<end){Map<String,Object> d=(Map<String,Object>)draft(Collections.singletonMap("id","work")).get("data");if(d!=null&&state.equals(d.get("state")))return d;Thread.sleep(20);}throw new AssertionError("state "+state);}
  void start(boolean auto){Map<String,Object> b=new HashMap<>();b.put("id","work");b.put("confirmed",true);b.put("autoSubmit",auto);assertEquals(200,startRetake(b).get("code"));}
  public void close(){stopWorkers();}
 }
 @Test void explicitRetakeUsesPublishedAnswerNoAiThenSubmitsOnceAndVerifiesModernScore()throws Exception{try(Fixture f=new Fixture(false,false,false)){Map<String,Object> preview=(Map<String,Object>)f.retakePreview(Collections.singletonMap("id","work")).get("data");assertEquals(true,preview.get("canRetake"));assertEquals(0,f.resets.get());assertEquals(0,f.posts.get());assertNotEquals(200,f.startRetake(Collections.singletonMap("id","work")).get("code"));f.start(true);assertEquals(1,f.waitFor("completed").get("done"));assertEquals(1,f.resets.get());assertEquals(1,f.posts.get());verifyNoInteractions(f.query);Map<String,String> values=new HashMap<>();for(int i=0;i<f.body.size();i++)values.put(f.body.name(i),f.body.value(i));assertEquals("A",values.get("answer99"));assertEquals("99,",values.get("questionIds"));assertEquals("",values.get("pyFlag"));}}
 @Test void manualRetakeOnlyPreparesAnswersAfterOneReset()throws Exception{try(Fixture f=new Fixture(false,false,false)){f.start(false);f.waitFor("ready");assertEquals(1,f.resets.get());assertEquals(0,f.posts.get());}}
 @Test void missingPublishedAnswerDoesNotConsumeRetake()throws Exception{try(Fixture f=new Fixture(true,false,false)){f.start(true);f.waitFor("failed");assertEquals(0,f.resets.get());assertEquals(0,f.posts.get());verifyNoInteractions(f.query);}}
 @Test void ambiguousResetNeverRetriesOrSubmits()throws Exception{try(Fixture f=new Fixture(false,true,false)){f.start(true);f.waitFor("uncertain");assertEquals(1,f.resets.get());assertEquals(0,f.posts.get());f.startRetake(new HashMap<String,Object>(){{put("id","work");put("confirmed",true);put("autoSubmit",true);}});Thread.sleep(100);assertEquals(1,f.resets.get());}}
 @Test void changedRetakeQuestionStopsBeforeSubmit()throws Exception{try(Fixture f=new Fixture(false,false,true)){f.start(true);f.waitFor("uncertain");assertEquals(1,f.resets.get());assertEquals(0,f.posts.get());}}
}
