package com.tihai.service.superstar.impl;
import com.tihai.domain.chaoxing.SuperStarTask;
import com.tihai.exception.BusinessException;
import com.tihai.service.superstar.SuperStarLogService;
import com.tihai.utils.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class TaskControlTest {
    private SuperStarTask task(){SuperStarTask t=new SuperStarTask();t.setId("test");t.setStatus(0);t.setRetryCount(0);t.setPriority(1);return t;}
    private SuperStarTaskServiceImpl service(SuperStarTask t,ThreadPoolExecutor pool,LiveProgressTracker tracker) throws Exception {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"controls-test"),SuperStarTask.class);
        SuperStarTaskServiceImpl s=spy(new SuperStarTaskServiceImpl());doReturn(t).when(s).getById("test");doReturn(true).when(s).updateById(any());doReturn(Collections.singletonList(t)).when(s).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        ServerInfoUtil server=mock(ServerInfoUtil.class);when(server.getCurrentServerInstance()).thenReturn("local");ReflectionTestUtils.setField(s,"serverInfoUtil",server);ReflectionTestUtils.setField(s,"taskExecutor",pool);ReflectionTestUtils.setField(s,"liveProgress",tracker);ReflectionTestUtils.setField(s,"superStarLogService",mock(SuperStarLogService.class));return s;
    }
    @Test void stoppingQueuedTaskPreventsExecutionAndAllowsDeletion() throws Exception {
        SuperStarTask t=task();ThreadPoolExecutor pool=mock(ThreadPoolExecutor.class);SuperStarTaskServiceImpl s=service(t,pool,new LiveProgressTracker());when(pool.remove(any())).thenReturn(true);doReturn(true).when(s).removeById("test");
        s.startChaoxingTask();assertThrows(BusinessException.class,()->s.deleteTask("test"));
        assertEquals("任务已停止",s.stopTask("test"));assertEquals("idle",s.executionState("test"));assertEquals(6,t.getStatus());s.deleteTask("test");verify(s).removeById("test");verify(s,never()).executeCourseTask(any());
    }
    @Test void runningWorkerIsInterruptedAndDoesNotRetry() throws Exception {
        SuperStarTask t=task();ThreadPoolExecutor pool=mock(ThreadPoolExecutor.class);LiveProgressTracker tracker=new LiveProgressTracker();SuperStarTaskServiceImpl s=service(t,pool,tracker);CountDownLatch entered=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();try{Thread.sleep(30000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new CancellationException();}return null;}).when(s).executeCourseTask(t);
        s.startChaoxingTask();ArgumentCaptor<Runnable> job=ArgumentCaptor.forClass(Runnable.class);verify(pool).execute(job.capture());Thread worker=new Thread(job.getValue());worker.start();assertTrue(entered.await(2,TimeUnit.SECONDS));
        s.stopTask("test");worker.join(2000);assertFalse(worker.isAlive());assertEquals("idle",s.executionState("test"));assertEquals(6,t.getStatus());assertEquals(0,t.getRetryCount());
    }
    @Test void stopCancelsRegisteredNetworkCallAndFencesLaterSubmission(){
        LiveProgressTracker tracker=new LiveProgressTracker();tracker.begin("network");okhttp3.Call call=mock(okhttp3.Call.class);tracker.registerCall(call);tracker.cancel("network");verify(call).cancel();assertThrows(CancellationException.class,tracker::checkCancelled);tracker.unregisterCall(call);tracker.release();
    }
}
