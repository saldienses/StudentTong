package com.tihai.utils;
import com.tihai.common.Question;
import com.tihai.domain.chaoxing.SuperStarLog;
import com.tihai.service.superstar.SuperStarLogService;
import okhttp3.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class QuizSubmissionOutcomeTest {
    private void submit(boolean accepted,SuperStarLog log) throws Exception {
        CourseUtil util=new CourseUtil();LiveProgressTracker tracker=new LiveProgressTracker();tracker.begin("quiz");Query query=mock(Query.class);when(query.queryAnswerSync(any(Question.class))).thenReturn("Alpha");ServerInfoUtil server=mock(ServerInfoUtil.class);when(server.getCurrentServerInstance()).thenReturn("local");
        ReflectionTestUtils.setField(util,"liveProgress",tracker);ReflectionTestUtils.setField(util,"query",query);ReflectionTestUtils.setField(util,"serverInfoUtil",server);ReflectionTestUtils.setField(util,"superStarLogService",mock(SuperStarLogService.class));
        OkHttpClient client=new OkHttpClient.Builder().addInterceptor(chain->new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create("{\"status\":"+accepted+",\"msg\":\"result\"}",MediaType.parse("application/json"))).build()).build();
        Map<String,Object> q=new HashMap<>();q.put("id","1");q.put("title","Local sample");q.put("options","A. Alpha\nB. Beta");q.put("type","single");q.put("answerField",new HashMap<String,Object>());Map<String,Object> payload=new HashMap<>();payload.put("questions",Collections.singletonList(q));
        try{util.processAnswers(payload,client,log,"Local quiz");}finally{tracker.release();}
    }
    @Test void acceptsBooleanSuccessInsteadOfOnlyStringTrue() throws Exception {SuperStarLog log=new SuperStarLog();submit(true,log);assertEquals(1,log.getStatus());assertNull(log.getErrorMessage());}
    @Test void httpSuccessWithRejectedSubmissionIsStillFailure(){SuperStarLog log=new SuperStarLog();assertThrows(java.io.IOException.class,()->submit(false,log));assertEquals(5,log.getStatus());}
}
