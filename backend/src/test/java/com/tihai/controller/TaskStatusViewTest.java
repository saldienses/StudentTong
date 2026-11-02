package com.tihai.controller;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class TaskStatusViewTest {
    private Map<String,Object> task(int queue,Integer log,String progress) {
        Map<String,Object> t=new HashMap<>();t.put("status",queue);t.put("log_status",log);
        t.put("current_progress",progress==null?null:new BigDecimal(progress));TaskStatusView.enrich(t);return t;
    }
    @Test void runningLogOverridesStaleQueueButStopTakesPriority() {
        assertEquals("running",task(2,1,"72.22").get("display_status"));
        assertEquals("stopped",task(6,1,"72.22").get("display_status"));
        assertEquals("failed",task(2,5,"72.22").get("display_status"));
        assertEquals("answering",task(2,3,"72.22").get("display_status"));
    }
    @Test void completionAndMissingProgressAreDistinct() {
        assertEquals(new BigDecimal("100"),task(2,4,null).get("current_progress"));
        assertNull(task(2,null,null).get("current_progress"));
        assertEquals("queued",task(2,null,null).get("display_status"));
        assertEquals("pending",task(0,null,null).get("display_status"));
        assertEquals("unknown",task(91,null,null).get("display_status"));
    }
    @Test void clampsOutOfRangeMeasurements() {
        assertEquals(BigDecimal.ZERO,task(2,1,"-12").get("current_progress"));
        assertEquals(new BigDecimal("100"),task(2,1,"150").get("current_progress"));
    }
    @Test void historicalRunningLogDoesNotPretendToRunAfterRestart() {
        Map<String,Object> stale=task(2,1,"72.22");stale.put("start_time",new java.sql.Timestamp(1000));
        TaskStatusView.enrich(stale,2000);assertEquals("stopped",stale.get("display_status"));
        Map<String,Object> live=task(2,1,"72.22");live.put("start_time",new java.sql.Timestamp(3000));
        TaskStatusView.enrich(live,2000);assertEquals("running",live.get("display_status"));
        Map<String,Object> jdbcLocalTime=task(2,1,"72.22");jdbcLocalTime.put("start_time",java.time.LocalDateTime.of(2026,10,30,10,34));
        TaskStatusView.enrich(jdbcLocalTime,java.time.LocalDateTime.of(2026,10,30,15,50).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());
        assertEquals("stopped",jdbcLocalTime.get("display_status"));
    }
}
