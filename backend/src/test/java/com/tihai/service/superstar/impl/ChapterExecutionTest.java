package com.tihai.service.superstar.impl;
import com.tihai.common.*;
import com.tihai.domain.chaoxing.*;
import com.tihai.dubbo.pojo.course.Course;
import com.tihai.properties.StudyProperties;
import com.tihai.service.superstar.SuperStarLogService;
import com.tihai.utils.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ChapterExecutionTest {
    private ChapterPoint chapter(String id){ChapterPoint p=new ChapterPoint();p.setId(id);p.setTitle(id);return p;}
    private Job job(String type,String id,JobInfo info){Job j=new Job();j.setType(type);j.setTitle(id);j.setJobId(id);j.setCardInfo(info);return j;}
    private Course course(){Course c=new Course();c.setClazzId("class");c.setCourseId("course");c.setCpi("cpi");return c;}
    private CoursePoint group(ChapterPoint... points){CoursePoint g=new CoursePoint();g.setPoints(Arrays.asList(points));return g;}
    private SuperStarTaskServiceImpl service(CourseUtil util) throws Exception {SuperStarTaskServiceImpl s=new SuperStarTaskServiceImpl();ReflectionTestUtils.setField(s,"courseUtil",util);ReflectionTestUtils.setField(s,"superStarLogService",mock(SuperStarLogService.class));ReflectionTestUtils.setField(s,"liveProgress",new LiveProgressTracker());StudyProperties p=new StudyProperties();p.setSpeed(1);ReflectionTestUtils.setField(s,"studyProperties",p);return s;}
    @Test void allVideosAndQuizReceiveTheirOwnCardContext() throws Exception {
        CourseUtil util=mock(CourseUtil.class);JobInfo one=new JobInfo(),two=new JobInfo(),three=new JobInfo();one.setKtoken("one");two.setKtoken("two");three.setKtoken("three");Job a=job("video","first",one),b=job("video","second",two),q=job("workid","quiz",three);Course c=course();SuperStarLog log=new SuperStarLog();
        when(util.getJobList(any(),any(),any(),eq("ch"))).thenReturn(new Pair<>(Arrays.asList(a,b,q),Arrays.asList(one,two,three)));
        service(util).processCoursePoints(new SuperStarTask(),log,c,Arrays.asList(group(chapter("ch"))));
        verify(util).studyVideo(c,a,one,1d,"Video",log);verify(util).studyVideo(c,b,two,1d,"Video",log);verify(util).studyWork(c,q,three,log);assertEquals(4,log.getStatus());assertEquals(100d,log.getCurrentProgress().doubleValue());
    }
    @Test void failedVideoDoesNotSkipOtherVideoOrQuizAndCannotMarkChapterComplete() throws Exception {
        CourseUtil util=mock(CourseUtil.class);JobInfo info=new JobInfo();Job a=job("video","first",info),b=job("video","second",info),q=job("workid","quiz",info);Course c=course();SuperStarLog log=new SuperStarLog();when(util.getJobList(any(),any(),any(),any())).thenReturn(new Pair<>(Arrays.asList(a,b,q),Arrays.asList(info,info,info)));doThrow(new java.io.IOException("Sample failure")).when(util).studyVideo(c,a,info,1d,"Video",log);
        service(util).processCoursePoints(new SuperStarTask(),log,c,Arrays.asList(group(chapter("ch"))));verify(util).studyVideo(c,b,info,1d,"Video",log);verify(util).studyWork(c,q,info,log);assertEquals(5,log.getStatus());assertTrue(log.getCurrentProgress().doubleValue()<100);assertEquals(0,log.getCurrentChapterIndex());
    }
    @Test void lockedChapterAndMultipleGroupsDoNotProduceFalseHundredPercent() throws Exception {
        CourseUtil util=mock(CourseUtil.class);JobInfo locked=new JobInfo();locked.setNotOpen(true);when(util.getJobList(any(),any(),any(),eq("locked"))).thenReturn(new Pair<>(Collections.emptyList(),Collections.singletonList(locked)));when(util.getJobList(any(),any(),any(),eq("empty"))).thenReturn(new Pair<>(Collections.emptyList(),Collections.emptyList()));SuperStarLog log=new SuperStarLog();
        service(util).processCoursePoints(new SuperStarTask(),log,course(),Arrays.asList(group(chapter("locked")),group(chapter("empty"))));verify(util).getJobList(any(),any(),any(),eq("empty"));assertEquals(5,log.getStatus());assertEquals(50d,log.getCurrentProgress().doubleValue());assertEquals(0,log.getCurrentChapterIndex());
    }
}
