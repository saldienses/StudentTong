package com.tihai.controller;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.*;

@RestController
public class LocalStatusController {
    @org.springframework.beans.factory.annotation.Autowired
    private com.tihai.service.superstar.impl.SuperStarTaskServiceImpl taskService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.tihai.utils.LiveProgressTracker liveProgress;
    private final JdbcTemplate db;
    private final long applicationStartedAt=System.currentTimeMillis();
    public LocalStatusController(JdbcTemplate db) { this.db=db; }
    @org.springframework.web.bind.annotation.PostMapping(value="/local/tasks/{taskId}/resume",consumes="application/json")
    public com.tihai.common.R resume(@org.springframework.web.bind.annotation.PathVariable String taskId) throws com.alibaba.nacos.api.exception.NacosException {
        try { return com.tihai.common.R.ok(taskService.resumeTask(taskId)); }
        catch(com.tihai.exception.BusinessException e) { return com.tihai.common.R.error(e.getCode(),e.getMessage()); }
    }
    @org.springframework.web.bind.annotation.PostMapping(value="/local/tasks/{taskId}/stop",consumes="application/json")
    public com.tihai.common.R stop(@org.springframework.web.bind.annotation.PathVariable String taskId){try{return com.tihai.common.R.ok(taskService.stopTask(taskId));}catch(com.tihai.exception.BusinessException e){return com.tihai.common.R.error(e.getCode(),e.getMessage());}}
    @org.springframework.web.bind.annotation.PostMapping(value="/local/tasks/{taskId}/delete",consumes="application/json")
    public com.tihai.common.R delete(@org.springframework.web.bind.annotation.PathVariable String taskId){try{return com.tihai.common.R.ok(taskService.deleteTask(taskId));}catch(com.tihai.exception.BusinessException e){return com.tihai.common.R.error(e.getCode(),e.getMessage());}}
    @org.springframework.web.bind.annotation.PostMapping(value="/local/tasks/{taskId}/rescan",consumes="application/json")
    public com.tihai.common.R rescan(@org.springframework.web.bind.annotation.PathVariable String taskId) throws com.alibaba.nacos.api.exception.NacosException {try{return com.tihai.common.R.ok(taskService.rescanTask(taskId));}catch(com.tihai.exception.BusinessException e){return com.tihai.common.R.error(e.getCode(),e.getMessage());}}
    @GetMapping("/local/status")
    public Map<String,Object> status() {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("service","SuperAutoStudy");
        result.put("database",db.queryForObject("SELECT 1",Integer.class)==1?"ok":"error");
        List<Map<String,Object>> tasks=db.queryForList(
            "SELECT t.id AS task_id,t.course_name,t.status,t.retry_count,t.creat_time,"+
            "l.status AS log_status,l.current_progress,l.current_chapter_index,l.current_job,"+
            "l.error_message,l.start_time,l.end_time FROM th_wk_queue t "+
            "LEFT JOIN th_wk_log l ON l.id=(SELECT MAX(x.id) FROM th_wk_log x "+
            "WHERE x.login_account=t.login_account AND x.course_name=t.course_name AND x.start_time>=t.creat_time) "+
            "WHERE (?='' OR t.login_account=?) ORDER BY t.creat_time DESC,t.id DESC LIMIT 30",com.tihai.utils.AccountScope.scoped()?"scoped":"",com.tihai.utils.AccountScope.account());
        tasks.forEach(task->{
            task.put("live_progress",liveProgress.snapshot(String.valueOf(task.get("task_id"))));
            task.put("execution_state",taskService.executionState(String.valueOf(task.get("task_id"))));
            TaskStatusView.enrich(task,applicationStartedAt);
            Map<String,Object> live=(Map<String,Object>)task.get("live_progress");
            if("running".equals(task.get("execution_state")) && live!=null && Arrays.asList("quiz","answering","answer_ready","submitting","quiz_response").contains(live.get("phase"))) { task.put("display_status","answering");task.put("status_label","答题中"); }
        });
        result.put("tasks",tasks);
        result.put("logs",db.queryForList("SELECT course_name,current_chapter_index,current_progress,status,error_message,start_time,end_time FROM th_wk_log WHERE (?='' OR login_account=?) ORDER BY id DESC LIMIT 30",com.tihai.utils.AccountScope.scoped()?"scoped":"",com.tihai.utils.AccountScope.account()));
        return result;
    }
}
