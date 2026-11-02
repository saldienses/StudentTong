package com.tihai.utils;
import com.tihai.common.*;
import com.tihai.dubbo.pojo.course.Course;
import okhttp3.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MediaAndAnswerTest {
    @Test void reachingDurationWithoutPlatformConfirmationIsNotCompletion() throws Exception {
        CourseUtil u=new CourseUtil();u.setCookies("_uid=1; fid=1");ReflectionTestUtils.setField(u,"liveProgress",new LiveProgressTracker());Course c=new Course();c.setClazzId("c");c.setCpi("p");c.setCourseId("course");Job j=new Job();j.setJobId("video");j.setObjectId("object");j.setOtherInfo("courseId=course");
        OkHttpClient no=new OkHttpClient.Builder().addInterceptor(chain->new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create("{\"isPassed\":false}",MediaType.parse("application/json"))).build()).build();
        OkHttpClient yes=new OkHttpClient.Builder().addInterceptor(chain->new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create("{\"isPassed\":true}",MediaType.parse("application/json"))).build()).build();
        assertFalse(u.videoProgressLog(no,c,j,new JobInfo(),"dummy",60,60,"Video"));assertTrue(u.videoProgressLog(yes,c,j,new JobInfo(),"dummy",60,60,"Video"));
    }
    @Test void answerLabelsAndEmptyAnswersAreHandledWithoutGuessing() throws Exception {
        List<String> options=Arrays.asList("A. Alpha","B. Beta","C. Gamma");assertEquals("B",AnswerMapper.map("single","B",options));assertEquals("A",AnswerMapper.map("single","Alpha",options));assertEquals("AC",AnswerMapper.map("multiple","C. Gamma#A. Alpha",options));assertEquals("AB",AnswerMapper.map("multiple","AB",options));assertEquals("true",AnswerMapper.map("judgement","true",options));assertThrows(java.io.IOException.class,()->AnswerMapper.map("single",null,options));assertThrows(java.io.IOException.class,()->AnswerMapper.map("single","Z",options));assertThrows(java.io.IOException.class,()->AnswerMapper.map("unknown","value",options));
    }
    @Test void fillAndShortQuestionTypesAreDecoded() throws Exception {
        String html="<form><div class='singleQuesId' data='1'><div class='Zy_TItle'>Fill question</div><div class='TiMu' data='2'></div></div><div class='singleQuesId' data='2'><div class='Zy_TItle'>Short question</div><div class='TiMu' data='4'></div></div></form>";
        Map<String,Object> result=Decode.decodeQuestionsInfo(html);List<Map<String,Object>> qs=(List<Map<String,Object>>)result.get("questions");assertEquals("fill",qs.get(0).get("type"));assertEquals("short",qs.get(1).get("type"));
    }
}
