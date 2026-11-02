package com.tihai.utils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.tihai.common.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ChapterPagingTest {
    static String card(String id,String attachments){return "<script>mArg = {\n\"defaults\":{\"cardid\":\""+id+"\",\"ktoken\":\"token-"+id+"\"},\n\"attachments\":"+attachments+"\n};</script>";}
    @Test void discoversVideoVideoAndQuizBeyondFirstThreeCards() throws Exception {
        List<String> pages=Arrays.asList(card("0","[{\"type\":\"video\",\"jobid\":\"v1\",\"objectId\":\"one\",\"job\":true,\"property\":{\"name\":\"First video.mp4\"}}]"),card("1","[{\"type\":\"video\",\"jobid\":\"v2\",\"objectId\":\"two\",\"job\":false,\"property\":{\"name\":\"Second video.mp4\"}}]"),card("2","[]"),card("3","[{\"type\":\"workid\",\"job\":false,\"property\":{\"workid\":\"quiz\",\"title\":\"Chapter quiz\"}}]"),card("4","[{\"type\":\"video\",\"isPassed\":true,\"jobid\":\"done\"}]"),"<html>End</html>");
        CourseUtil util=new CourseUtil(){@Override protected String readCourseCardPage(String a,String b,String c,String d,int page){return pages.get(page);}};
        ReflectionTestUtils.setField(util,"liveProgress",new LiveProgressTracker());Pair<List<Job>,List<JobInfo>> result=util.getJobList("c","course","p","k");
        assertEquals(3,result.getFirst().size());assertEquals(3,result.getSecond().size());assertEquals("Second video.mp4",result.getFirst().get(1).getName());assertEquals("token-1",result.getSecond().get(1).getKtoken());assertEquals("work-quiz",result.getFirst().get(2).getJobId());assertEquals("token-3",result.getFirst().get(2).getCardInfo().getKtoken());
    }
    @Test void repeatedPageStopsWithoutDuplicatingJobs() throws Exception {
        String page=card("same","[{\"type\":\"video\",\"jobid\":\"v\",\"objectId\":\"o\"}]");
        CourseUtil util=new CourseUtil(){@Override protected String readCourseCardPage(String a,String b,String c,String d,int index){return page;}};ReflectionTestUtils.setField(util,"liveProgress",new LiveProgressTracker());assertEquals(1,util.getJobList("a","b","c","d").getFirst().size());
    }
    @Test void preservesSpacesAndBracesInsideStringsAndRejectsInvalidFirstPage() throws Exception {
        String page=card("id","[{\"type\":\"video\",\"jobid\":\"v\",\"objectId\":\"o\",\"property\":{\"name\":\"A name } ; video.mp4\"}}]");assertEquals("A name } ; video.mp4",Decode.decodeCourseCard(page).getFirst().get(0).getName());
        CourseUtil util=new CourseUtil(){@Override protected String readCourseCardPage(String a,String b,String c,String d,int index){return "<html>Login required</html>";}};ReflectionTestUtils.setField(util,"liveProgress",new LiveProgressTracker());assertThrows(java.io.IOException.class,()->util.getJobList("a","b","c","d"));
    }
}
