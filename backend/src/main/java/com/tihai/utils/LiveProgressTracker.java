package com.tihai.utils;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Component
public class LiveProgressTracker {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private SecurityAlertHub security;
    private static class State {
        final Map<String,Object> data=new LinkedHashMap<>();
        long waitStart; double base,target; long waitMillis;
    }
    private final Map<String,State> tasks=new ConcurrentHashMap<>();
    private final ThreadLocal<String> current=new ThreadLocal<>();
    private final LongSupplier nanoClock,epochClock;
    public LiveProgressTracker(){this(System::nanoTime,System::currentTimeMillis);}
    LiveProgressTracker(LongSupplier nanoClock,LongSupplier epochClock){this.nanoClock=nanoClock;this.epochClock=epochClock;}
    private State state(){String id=current.get();return id==null?null:tasks.get(id);}
    private void phase(State s,String value,String label){s.data.put("phase",value);s.data.put("phase_label",label);s.data.put("updated_at_ms",epochClock.getAsLong());}
    private final Map<String,Set<okhttp3.Call>> calls=new ConcurrentHashMap<>();
    public String currentTaskId(){return current.get();}
    public void checkCancelled(){State s=state();if(Thread.currentThread().isInterrupted() || (s!=null && Boolean.TRUE.equals(snapshot(current.get()).get("cancel_requested"))))throw new java.util.concurrent.CancellationException("Task stopped");}
    public void registerCall(okhttp3.Call call){String id=current.get();if(id==null)return;calls.computeIfAbsent(id,k->ConcurrentHashMap.newKeySet()).add(call);try{checkCancelled();}catch(RuntimeException e){call.cancel();unregisterCall(call);throw e;}}
    public void unregisterCall(okhttp3.Call call){String id=current.get();if(id!=null){Set<okhttp3.Call> set=calls.get(id);if(set!=null)set.remove(call);}}
    public void cancel(String taskId){State s=tasks.get(taskId);if(s!=null)synchronized(s){s.data.put("cancel_requested",true);s.data.put("active",false);s.waitMillis=0;phase(s,"stopping","正在停止任务");}Set<okhttp3.Call> set=calls.get(taskId);if(set!=null)for(okhttp3.Call call:set)call.cancel();}
    public void begin(String taskId){
        State s=new State();s.data.put("active",true);phase(s,"loading","正在读取课程");tasks.put(taskId,s);current.set(taskId);
    }
    public void chapter(int index,int total,String title){State s=state();if(s==null)return;synchronized(s){
        s.data.put("chapter_index",index);s.data.put("chapter_total",total);s.data.put("chapter_title",title);phase(s,"chapter","正在读取章节任务");
    }}
    public void job(int index,int total,String title){State s=state();if(s==null)return;synchronized(s){
        s.data.keySet().removeIf(k->k.startsWith("video_")||k.startsWith("question_")||k.equals("prepared_questions")||k.equals("answer_source")||k.startsWith("recognition_"));
        s.waitMillis=0;s.data.put("job_index",index);s.data.put("job_total",total);s.data.put("job_title",title);phase(s,"preparing","正在准备任务点");
    }}
    public void video(String title,String kind,long duration){State s=state();if(s==null)return;synchronized(s){
        s.waitMillis=0;s.data.put("job_title",title);s.data.put("video_duration_seconds",Math.max(0,duration));s.data.put("video_reported_seconds",0L);s.data.put("video_estimated_seconds",0d);
        phase(s,"Audio".equals(kind)?"audio":"video","Audio".equals(kind)?"正在处理音频":"正在处理视频");
    }}
    public void reported(long position){State s=state();if(s==null)return;synchronized(s){
        s.waitMillis=0;long duration=((Number)s.data.getOrDefault("video_duration_seconds",0L)).longValue();long value=Math.min(Math.max(0,position),duration);
        s.data.put("video_reported_seconds",value);s.data.put("video_estimated_seconds",(double)value);s.data.put("updated_at_ms",epochClock.getAsLong());
    }}
    public void waiting(long base,long target,long millis){State s=state();if(s==null)return;synchronized(s){
        s.base=Math.max(0,base);s.target=Math.max(s.base,target);s.waitMillis=Math.max(1,millis);s.waitStart=nanoClock.getAsLong();
    }}
    public void videoEnded(boolean reachedEnd){State s=state();if(s==null)return;synchronized(s){s.waitMillis=0;
        if(reachedEnd){long duration=((Number)s.data.get("video_duration_seconds")).longValue();s.data.put("video_estimated_seconds",(double)duration);phase(s,"video_done","媒体计时已结束");}
    }}
    public void quiz(String title,int total){State s=state();if(s==null)return;synchronized(s){
        s.data.keySet().removeIf(k->k.startsWith("video_"));s.waitMillis=0;s.data.put("job_title",title);s.data.put("question_total",Math.max(0,total));s.data.put("question_index",0);s.data.put("prepared_questions",0);phase(s,"quiz","正在读取小测题目");
    }}
    public void recognizing(int index,int total){State s=state();if(s==null)return;synchronized(s){s.data.put("recognition_source","截图视觉识别");s.data.put("recognition_index",index);s.data.put("recognition_total",total);phase(s,"recognizing","正在截图识别题目 "+index+" / "+total);}}
    public void recognitionSource(String source){State s=state();if(s==null)return;synchronized(s){s.data.put("recognition_source",source);}}
    public void quizReviewed(){State s=state();if(s==null)return;synchronized(s){phase(s,"quiz_reviewed","平台显示小测已完成，跳过重复作答");}}
    public void question(int index,String type){State s=state();if(s==null)return;synchronized(s){s.data.put("question_index",index);s.data.put("question_type",type);s.data.put("answer_source","题库查询中");phase(s,"answering","正在处理题目");}}
    public void answerSource(String source){State s=state();if(s==null)return;synchronized(s){if(s.data.containsKey("question_total")){s.data.put("answer_source",source);s.data.put("updated_at_ms",epochClock.getAsLong());}}}
    public void prepared(int count){State s=state();if(s==null)return;synchronized(s){s.data.put("prepared_questions",count);phase(s,"answer_ready","答案已准备");}}
    public void submitting(){State s=state();if(s==null)return;synchronized(s){phase(s,"submitting","正在提交本次小测");}}
    public void submitted(boolean httpOk){State s=state();if(s==null)return;synchronized(s){phase(s,httpOk?"quiz_response":"error",httpOk?"小测提交接口已响应":"小测提交请求失败");}}
    public void failed(String message){if(security!=null&&current.get()!=null)security.detect("chapters",current.get(),"学习任务安全提示",message);State s=state();if(s==null)return;synchronized(s){s.waitMillis=0;phase(s,"error",message);}}
    public void completed(){State s=state();if(s==null)return;synchronized(s){s.waitMillis=0;phase(s,"completed","课程处理已结束");}}
    public void release(){State s=state();if(s!=null)synchronized(s){s.data.put("active",false);s.waitMillis=0;}calls.remove(current.get());current.remove();}
    public Map<String,Object> snapshot(String taskId){State s=tasks.get(taskId);if(s==null)return null;synchronized(s){
        Map<String,Object> out=new LinkedHashMap<>(s.data);out.put("sampled_at_ms",epochClock.getAsLong());
        double estimated=((Number)s.data.getOrDefault("video_estimated_seconds",0d)).doubleValue();double rate=0,target=estimated;
        if(s.waitMillis>0){double elapsed=Math.max(0,(nanoClock.getAsLong()-s.waitStart)/1_000_000d);estimated=Math.min(s.target,s.base+(s.target-s.base)*Math.min(1,elapsed/s.waitMillis));rate=elapsed<s.waitMillis?(s.target-s.base)/(s.waitMillis/1000d):0;target=s.target;}
        if(s.data.containsKey("video_duration_seconds")){double duration=((Number)s.data.get("video_duration_seconds")).doubleValue();out.put("video_estimated_seconds",Math.max(0,Math.min(duration,estimated)));out.put("video_target_seconds",Math.min(duration,target));out.put("video_rate",Boolean.TRUE.equals(s.data.get("active"))?rate:0);}
        return out;
    }}
}
