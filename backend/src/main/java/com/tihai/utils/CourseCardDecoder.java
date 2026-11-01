package com.tihai.utils;
import com.tihai.common.*;
import org.json.*;
import java.util.*;
import java.util.regex.*;

public final class CourseCardDecoder {
    private CourseCardDecoder(){}
    static String arguments(String html){
        Matcher m=Pattern.compile("\\bmArg\\s*=\\s*\\{").matcher(html);if(!m.find())return null;
        int start=html.indexOf('{',m.start()),depth=0;char quote=0;boolean escaped=false;
        for(int i=start;i<html.length();i++){char c=html.charAt(i);if(quote!=0){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c==quote)quote=0;continue;}if(c=='\"'||c=='\''){quote=c;continue;}if(c=='{')depth++;if(c=='}'&&--depth==0)return html.substring(start,i+1);}
        throw new IllegalArgumentException("章节任务参数不完整");
    }
    private static String first(String... values){for(String v:values)if(v!=null&&!v.trim().isEmpty())return v;return "";}
    public static Pair<List<Job>,JobInfo> decode(String html){return decode(html,false);}
    public static Pair<List<Job>,JobInfo> decode(String html,boolean includeCompleted){
        List<Job> jobs=new ArrayList<>();JobInfo info=new JobInfo();info.setCardPresent(false);
        if(html.contains("章节未开放")){info.setNotOpen(true);info.setCardPresent(true);return new Pair<>(jobs,info);}
        String raw=arguments(html);if(raw==null)return new Pair<>(jobs,info);
        JSONObject page=new JSONObject(raw),d=page.optJSONObject("defaults");if(d==null)d=new JSONObject();info.setCardPresent(true);info.setNotOpen(false);
        info.setKtoken(d.optString("ktoken"));info.setMtEnc(d.optString("mtEnc"));info.setReportTimeInterval(d.optInt("reportTimeInterval"));info.setDefenc(d.optString("defenc"));info.setCardid(d.optString("cardid"));info.setCpi(d.optString("cpi"));info.setQnenc(d.optString("qnenc"));info.setKnowledgeid(d.optString("knowledgeid"));
        JSONArray attachments=page.optJSONArray("attachments");if(attachments==null)return new Pair<>(jobs,info);
        for(int i=0;i<attachments.length();i++){
            JSONObject a=attachments.getJSONObject(i);if(!includeCompleted&&a.optBoolean("isPassed",false))continue;
            String type=a.optString("type");JSONObject p=a.optJSONObject("property");if(p==null)p=new JSONObject();
            boolean media="video".equals(type)||"audio".equals(type),quiz="workid".equals(type);
            boolean assigned=a.optBoolean("job",false)||a.optInt("job",0)==1;
            if(!media&&!quiz&&!assigned&&!"read".equals(type))continue;
            if(!Arrays.asList("video","audio","workid","read","document").contains(type))continue;
            if("read".equals(type)&&p.optBoolean("read",false))continue;
            Job job=new Job();job.setType(type);job.setCardInfo(info);
            job.setTitle(first(p.optString("title"),p.optString("name"),a.optString("title"),type));job.setName(first(p.optString("name"),job.getTitle()));
            job.setJobId(first(a.optString("jobid"),p.optString("jobid"),p.optString("_jobid")));
            if(quiz&&job.getJobId().isEmpty()){String work=first(p.optString("workid"),p.optString("workId"));if(!work.isEmpty())job.setJobId(work.startsWith("work-")?work:"work-"+work);}
            job.setId(first(p.optString("id"),job.getJobId(),a.optString("aid")));
            job.setObjectId(first(a.optString("objectId"),a.optString("objectid"),p.optString("objectid"),p.optString("objectId")));
            job.setJToken(a.optString("jtoken"));job.setMid(a.optString("mid"));job.setOtherInfo(a.optString("otherInfo"));job.setEnc(a.optString("enc"));job.setAid(a.optString("aid"));jobs.add(job);
        }
        return new Pair<>(jobs,info);
    }
}
