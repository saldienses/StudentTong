package com.tihai.service.superstar.impl;
import com.tihai.domain.chaoxing.*;
import com.tihai.dubbo.dto.CourseSubmitTaskDTO;
import com.tihai.exception.BusinessException;
import com.tihai.service.superstar.SuperStarLogService;
import com.tihai.utils.ServerInfoUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.concurrent.ThreadPoolExecutor;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TaskResumeTest {
    private SuperStarTask task() { SuperStarTask t=new SuperStarTask();t.setId("123");t.setLoginAccount("dummy");t.setCourseName("Test course");t.setCourseId("course");t.setStatus(2);t.setRetryCount(0);t.setPriority(1);return t; }
    private SuperStarTaskServiceImpl service(SuperStarTask task,SuperStarLogService logs) throws Exception {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"local-test"),SuperStarTask.class);
        SuperStarTaskServiceImpl s=spy(new SuperStarTaskServiceImpl());
        ReflectionTestUtils.setField(s,"superStarLogService",logs);ReflectionTestUtils.setField(s,"liveProgress",new com.tihai.utils.LiveProgressTracker());
        doReturn(task).when(s).getById("123");doReturn(true).when(s).updateById(any());return s;
    }
    @Test void explicitSubmissionRestoresExistingTaskWithoutCreatingDuplicate() throws Exception {
        SuperStarTask t=task();SuperStarLogService logs=mock(SuperStarLogService.class);
        SuperStarLog log=new SuperStarLog();log.setStatus(1);log.setCurrentProgress(new BigDecimal("72.22"));when(logs.getLatestLogByLoginAccount(any(),any())).thenReturn(log);
        SuperStarTaskServiceImpl s=service(t,logs);doReturn(t).when(s).getSuperStarTask("dummy","course");doNothing().when(s).startChaoxingTask();
        CourseSubmitTaskDTO dto=new CourseSubmitTaskDTO();dto.setLoginAccount("dummy");dto.setPassword("test-password");dto.setCourseId("course");
        s.addChaoXingTask(dto);
        assertEquals("123",t.getId());assertEquals(0,t.getStatus());assertEquals(new BigDecimal("72.22"),log.getCurrentProgress());
        verify(s).startChaoxingTask();verify(s).updateById(t);
    }
    @Test void duplicateSchedulingRunsOnceAndTracksWorkerLifetime() throws Exception {
        SuperStarTask t=task();SuperStarLogService logs=mock(SuperStarLogService.class);SuperStarTaskServiceImpl s=service(t,logs);
        ThreadPoolExecutor executor=mock(ThreadPoolExecutor.class);ServerInfoUtil server=mock(ServerInfoUtil.class);when(server.getCurrentServerInstance()).thenReturn("local");
        ReflectionTestUtils.setField(s,"taskExecutor",executor);ReflectionTestUtils.setField(s,"serverInfoUtil",server);
        doReturn(Collections.singletonList(t)).when(s).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        doAnswer(invocation->{assertEquals("running",s.executionState("123"));return null;}).when(s).executeCourseTask(t);
        s.startChaoxingTask();s.startChaoxingTask();assertEquals("queued",s.executionState("123"));
        ArgumentCaptor<Runnable> job=ArgumentCaptor.forClass(Runnable.class);verify(executor,times(1)).execute(job.capture());
        assertEquals("任务已在执行，无需重复提交",s.resumeTask("123"));
        job.getValue().run();assertEquals("idle",s.executionState("123"));
    }
    @Test void completedAndMissingTasksCannotBeResubmitted() throws Exception {
        SuperStarTask t=task();t.setStatus(4);SuperStarTaskServiceImpl s=service(t,mock(SuperStarLogService.class));
        assertThrows(BusinessException.class,()->s.resumeTask("123"));verify(s,never()).updateById(any());
        doReturn(null).when(s).getById("missing");
        assertThrows(BusinessException.class,()->s.resumeTask("missing"));
    }
}
