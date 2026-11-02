package com.tihai.utils;
import com.tihai.common.Question;
import com.tihai.domain.chaoxing.SuperStarLog;
import com.tihai.service.superstar.SuperStarLogService;
import okhttp3.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class QuizProgressIntegrationTest {
    @Test void actualAnswerLoopPublishesQuestionNumberAndSubmissionStage() throws Exception {
        LiveProgressTracker tracker=new LiveProgressTracker();tracker.begin("sample-task");
        CourseUtil util=new CourseUtil();Query query=mock(Query.class);AtomicInteger answered=new AtomicInteger();
        when(query.queryAnswerSync(any(Question.class))).thenAnswer(call->{int index=answered.incrementAndGet();assertEquals(index,tracker.snapshot("sample-task").get("question_index"));assertEquals("answering",tracker.snapshot("sample-task").get("phase"));return "Alpha";});
        ServerInfoUtil server=mock(ServerInfoUtil.class);when(server.getCurrentServerInstance()).thenReturn("local-test");
        ReflectionTestUtils.setField(util,"query",query);ReflectionTestUtils.setField(util,"liveProgress",tracker);ReflectionTestUtils.setField(util,"serverInfoUtil",server);ReflectionTestUtils.setField(util,"superStarLogService",mock(SuperStarLogService.class));
        OkHttpClient client=new OkHttpClient.Builder().addInterceptor(chain->{
            assertEquals("submitting",tracker.snapshot("sample-task").get("phase"));assertEquals(2,tracker.snapshot("sample-task").get("prepared_questions"));
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create("{\"status\":\"true\",\"msg\":\"success\"}",MediaType.parse("application/json"))).build();
        }).build();
        List<Object> questions=new ArrayList<>();for(int i=1;i<=2;i++){Map<String,Object> q=new HashMap<>();q.put("id",String.valueOf(i));q.put("title","Local sample");q.put("options","A. Alpha\nB. Beta");q.put("type","single");q.put("answerField",new HashMap<String,Object>());questions.add(q);}
        Map<String,Object> payload=new HashMap<>();payload.put("questions",questions);
        util.processAnswers(payload,client,new SuperStarLog(),"Local quiz");
        assertEquals(2,answered.get());assertEquals("quiz_response",tracker.snapshot("sample-task").get("phase"));assertEquals(2,tracker.snapshot("sample-task").get("question_total"));tracker.release();
    }
}
