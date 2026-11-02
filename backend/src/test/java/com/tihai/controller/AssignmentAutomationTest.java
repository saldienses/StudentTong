package com.tihai.controller;
import com.tihai.utils.*;import com.tihai.common.*;import org.junit.jupiter.api.Test;import org.springframework.test.util.ReflectionTestUtils;import okhttp3.*;import java.util.*;import java.util.concurrent.atomic.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class AssignmentAutomationTest {
 static final String URL="https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?workId=1&courseId=2&classId=3";
 static String html(String title){return "<form id='submitForm' method='post' action='/mooc-ans/work/addStudentWorkNewWeb?token=fixture'><input type='hidden' name='answerId' value='answer-record'><input type='hidden' name='answertype1' value='0'><div class='singleQuesId' data='1'><div class='Zy_TItle'>"+title+"</div><div class='TiMu' data='0'></div><ul><li>A. Alpha</li><li>B. Beta</li></ul></div></form>";}
 static class Fixture extends AssessmentController implements AutoCloseable {
  AtomicInteger posts=new AtomicInteger(),gets=new AtomicInteger();boolean completed,changed;Query query=mock(Query.class);CourseUtil util=mock(CourseUtil.class);FormBody sent;
  Fixture(boolean completeAfterPost,boolean change)throws Exception{completed=completeAfterPost;changed=change;
   OkHttpClient http=new OkHttpClient.Builder().addInterceptor(chain->{String body;
    if(chain.request().method().equals("POST")){posts.incrementAndGet();sent=(FormBody)chain.request().body();body="{\"status\":true}";}
    else {int get=gets.incrementAndGet();body=completed&&posts.get()>0?"<div class='testTit_status_complete'>已完成</div><span class='achievement'>成绩80分</span>":html(changed&&get>1?"Changed question":"Local test");}
    return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create(body,MediaType.parse("text/html"))).build();}).build();
   when(util.initSession(false,false)).thenReturn(http);when(query.queryAnswerSync(any(Question.class))).thenReturn("Alpha");
   ReflectionTestUtils.setField(this,"answerQuery",query);ReflectionTestUtils.setField(this,"progress",new LiveProgressTracker());ReflectionTestUtils.setField(this,"vision",mock(QuizVisionService.class));
   ((Map)ReflectionTestUtils.getField(this,"entries")).put("work",new Entry("test-account",URL,"homework"));
  }
  @Override protected CourseUtil session(String account){return util;}
  void start(boolean auto){Map<String,Object> body=new HashMap<>();body.put("id","work");body.put("autoSubmit",auto);assertEquals("200",String.valueOf(startAnswer(body).get("code")));}
  Map<String,Object> waitState(String expected)throws Exception{long until=System.currentTimeMillis()+5000;while(System.currentTimeMillis()<until){Map<String,Object> data=(Map<String,Object>)draft(Collections.singletonMap("id","work")).get("data");if(data!=null&&expected.equals(data.get("state")))return data;Thread.sleep(10);}throw new AssertionError("Did not reach "+expected);}
  public void close(){stopWorkers();}
 }
 @Test void uncheckedAutoSubmitOnlyBuildsDraftAndRequiresExplicitConfirmation()throws Exception{
  try(Fixture f=new Fixture(true,false)){f.start(false);Map<String,Object> state=f.waitState("ready");assertEquals(false,state.get("autoSubmit"));assertEquals(0,f.posts.get());Map<String,Object> body=new HashMap<>();body.put("id","work");assertNotEquals("200",String.valueOf(f.submitDraft(body).get("code")));assertEquals(0,f.posts.get());body.put("confirmed",true);assertEquals("200",String.valueOf(f.submitDraft(body).get("code")));f.waitState("completed");assertEquals(1,f.posts.get());String answer=null,record=null;for(int i=0;i<f.sent.size();i++){if("answer1".equals(f.sent.name(i)))answer=f.sent.value(i);if("answerId".equals(f.sent.name(i)))record=f.sent.value(i);}assertEquals("A",answer);assertEquals("answer-record",record);}
 }
 @Test void checkedAutoSubmitPostsOnceButSuccessResponseAloneDoesNotMeanCompleted()throws Exception{
  try(Fixture f=new Fixture(false,false)){f.start(true);f.waitState("uncertain");assertEquals(1,f.posts.get());Map<String,Object> body=new HashMap<>();body.put("id","work");body.put("confirmed",true);assertNotEquals("200",String.valueOf(f.submitDraft(body).get("code")));assertEquals(1,f.posts.get());}
 }
 @Test void allAnswersMustExistEvenWhenAutoSubmitChecked()throws Exception{try(Fixture f=new Fixture(true,false)){when(f.query.queryAnswerSync(any(Question.class))).thenReturn(null);f.start(true);f.waitState("failed");assertEquals(0,f.posts.get());}}
 @Test void absentAutoSubmitFlagDefaultsToNoSubmission()throws Exception{try(Fixture f=new Fixture(true,false)){Map<String,Object> body=new HashMap<>();body.put("id","work");f.startAnswer(body);assertEquals(false,f.waitState("ready").get("autoSubmit"));assertEquals(0,f.posts.get());}}
 @Test void cancellationStopsAnsweringWithoutSubmitting()throws Exception{try(Fixture f=new Fixture(true,false)){java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1);when(f.query.queryAnswerSync(any(Question.class))).thenAnswer(call->{entered.countDown();Thread.sleep(10000);return "Alpha";});f.start(true);assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));f.stopAnswer(Collections.singletonMap("id","work"));f.waitState("stopped");assertEquals(0,f.posts.get());}}
 @Test void changedQuestionStopsBeforeAnyPost()throws Exception{try(Fixture f=new Fixture(true,true)){f.start(true);Map<String,Object> state=f.waitState("ready");Thread.sleep(100);assertEquals(0,f.posts.get());assertEquals("ready",f.waitState("ready").get("state"));}}
 @Test void submissionPlanRejectsOtherEndpointsOrUnmappedAnswers()throws Exception{
  Map<String,Object> page=QuizPageReader.read(html("Local test"));List<Map<String,Object>> qs=(List<Map<String,Object>>)page.get("questions");qs.get(0).put("answer","A");
  assertThrows(java.io.IOException.class,()->AssignmentDraft.request(html("Local test").replace("/mooc-ans/work/addStudentWorkNewWeb","https://example.org/submit"),URL,page,qs));qs.get(0).put("answer","");assertThrows(java.io.IOException.class,()->AssignmentDraft.request(html("Local test"),URL,page,qs));
 }
}
