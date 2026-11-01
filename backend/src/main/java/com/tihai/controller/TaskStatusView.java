package com.tihai.controller;
import java.util.Map;
import java.math.BigDecimal;

public final class TaskStatusView {
    private TaskStatusView() {}
    private static int code(Object value) { return value instanceof Number ? ((Number)value).intValue() : -1; }
    public static void enrich(Map<String,Object> task,long applicationStartedAt) {
        enrich(task);
        Object execution=task.get("execution_state");
        if("stopping".equals(execution)){task.put("display_status","stopping");task.put("status_label","正在停止");return;}
        if ("running".equals(execution)) { task.put("display_status","running");task.put("status_label","进行中");return; }
        if ("queued".equals(execution)) { task.put("display_status","queued");task.put("status_label","排队中");return; }
        if ("idle".equals(execution)) {
            if ("running".equals(task.get("display_status")) || "answering".equals(task.get("display_status")) || "queued".equals(task.get("display_status"))) {
                task.put("display_status","stopped");task.put("status_label","已停止");
            }
            return;
        }
        Object started=task.get("start_time");
        Long startedMillis=started instanceof java.util.Date?((java.util.Date)started).getTime():null;
        if(started instanceof java.time.LocalDateTime) startedMillis=((java.time.LocalDateTime)started).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        if(startedMillis!=null && startedMillis<applicationStartedAt
            && ("running".equals(task.get("display_status")) || "answering".equals(task.get("display_status")))) {
            task.put("display_status","stopped");task.put("status_label","已停止");
        }
    }
    public static void enrich(Map<String,Object> task) {
        int queue=code(task.get("status")), latest=code(task.get("log_status"));
        String state="unknown",label="状态未知";
        if(queue==6) { state="stopped";label="已停止"; }
        else if(queue==5 || latest==5) { state="failed";label="执行异常"; }
        else if(queue==4 || latest==4) { state="completed";label="已完成"; }
        else if(queue==3 || latest==3) { state="answering";label="答题中"; }
        else if(queue==1 || latest==1) { state="running";label="进行中"; }
        else if(queue==2) { state="queued";label="排队中"; }
        else if(queue==0) { state="pending";label="等待中"; }
        task.put("display_status",state);task.put("status_label",label);
        Object value=task.get("current_progress");
        BigDecimal progress=value instanceof Number?new BigDecimal(value.toString()):null;
        if(progress!=null) progress=progress.max(BigDecimal.ZERO).min(new BigDecimal("100"));
        if("completed".equals(state)) progress=new BigDecimal("100");
        task.put("current_progress",progress);
    }
}
