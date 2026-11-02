package com.tihai.utils;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
class LiveProgressTrackerTest {
    @Test void interpolationIsBoundedAndReportedPositionRemainsDistinct(){
        AtomicLong clock=new AtomicLong(0);LiveProgressTracker t=new LiveProgressTracker(clock::get,()->1000L);
        t.begin("one");t.video("Video","Video",120);t.reported(30);t.waiting(30,60,60000);clock.set(30_000_000_000L);
        Map<String,Object> s=t.snapshot("one");assertEquals(45d,(Double)s.get("video_estimated_seconds"),.01);assertEquals(30L,s.get("video_reported_seconds"));
        clock.set(300_000_000_000L);assertEquals(60d,(Double)t.snapshot("one").get("video_estimated_seconds"),.01);
        t.reported(60);assertEquals(60L,t.snapshot("one").get("video_reported_seconds"));t.release();assertEquals(false,t.snapshot("one").get("active"));
    }
    @Test void quizPreparationDoesNotPretendToBeSubmission(){
        LiveProgressTracker t=new LiveProgressTracker();t.begin("quiz");t.quiz("Chapter test",10);t.question(3,"multiple");t.answerSource("DeepSeek 生成中");
        assertEquals(3,t.snapshot("quiz").get("question_index"));assertEquals(0,t.snapshot("quiz").get("prepared_questions"));
        t.prepared(3);assertEquals("answer_ready",t.snapshot("quiz").get("phase"));t.submitting();assertEquals("submitting",t.snapshot("quiz").get("phase"));
        t.submitted(false);assertEquals("error",t.snapshot("quiz").get("phase"));t.release();
    }
    @Test void taskAndPhaseChangesDoNotLeakPreviousCounters() throws Exception {
        LiveProgressTracker t=new LiveProgressTracker();t.begin("a");t.chapter(2,18,"Chapter");t.job(1,2,"Video");t.video("Video","Video",100);
        Thread other=new Thread(()->{t.begin("b");t.quiz("Quiz",5);t.question(1,"single");t.release();});other.start();other.join();
        assertEquals(100L,t.snapshot("a").get("video_duration_seconds"));assertNull(t.snapshot("b").get("video_duration_seconds"));assertEquals(5,t.snapshot("b").get("question_total"));
        t.job(2,2,"Quiz");t.quiz("Quiz",10);assertNull(t.snapshot("a").get("video_duration_seconds"));t.release();assertNull(t.snapshot("missing"));
    }
}
