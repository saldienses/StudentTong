package com.tihai.utils;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class QuizVisionService {
    private static final Path BASE=Paths.get("D:/tool/qita/SuperAutoStudy");
    @Autowired private DeepSeekClient ai;
    @Autowired private LiveProgressTracker progress;
    private final ObjectMapper json=new ObjectMapper();
    public void fill(String html,Map<String,Object> page)throws IOException{
        List<Map<String,Object>> qs=(List<Map<String,Object>>)page.get("questions");
        List<String> ids=new ArrayList<>();for(Map<String,Object> q:qs)if(Boolean.TRUE.equals(q.get("needsVision")))ids.add((String)q.get("id"));
        if(ids.isEmpty())return;
        if(!ai.isVisionEnabled())throw new IOException("网页题目含图片或无法解码，请在 AI 设置开启截图备用识别，本次未提交");
        if(ids.size()>100)throw new IOException("待截图题目超过100道，请分批核对，本次未提交");
        progress.recognizing(0,ids.size());
        Path dir=Files.createTempDirectory(BASE.resolve("temp"),"quiz-vision-");
        Process child=null;
        try{
            Path out=dir.resolve("result.json"),err=dir.resolve("render.err");
            Map<String,Object> input=new LinkedHashMap<>();input.put("html",html);input.put("ids",ids);input.put("outputDir",dir.toString());
            String programFiles=System.getenv("ProgramFiles(x86)");Path edge=Paths.get(programFiles==null?"C:/Program Files (x86)":programFiles,"Microsoft/Edge/Application/msedge.exe");
            input.put("edge",edge.toString());
            ProcessBuilder pb=new ProcessBuilder(BASE.resolve("runtime/vision-node/node.exe").toString(),BASE.resolve("config/quiz-render.cjs").toString());
            pb.directory(BASE.toFile());pb.environment().put("TEMP",dir.toString());pb.environment().put("TMP",dir.toString());pb.redirectOutput(out.toFile());pb.redirectError(err.toFile());
            child=pb.start();try(OutputStream stream=child.getOutputStream()){stream.write(json.writeValueAsBytes(input));}
            if(!child.waitFor(55,TimeUnit.SECONDS)){child.destroyForcibly();throw new IOException("题目截图超时，本次未提交");}
            progress.checkCancelled();
            if(child.exitValue()!=0)throw new IOException("题目截图失败（图片未加载或题号无法匹配），本次未提交");
            JsonNode items=json.readTree(Files.readAllBytes(out)).path("items");
            if(!items.isArray()||items.size()!=ids.size())throw new IOException("题目截图数量不完整，本次未提交");
            Map<String,Path> images=new HashMap<>();
            for(JsonNode item:items){String id=item.path("id").asText();if(!ids.contains(id)||!item.path("file").asText().equals(id+".png")||images.put(id,dir.resolve(id+".png"))!=null)throw new IOException("截图题号校验失败，本次未提交");}
            int n=0;
            for(Map<String,Object> q:qs)if(Boolean.TRUE.equals(q.get("needsVision"))){
                progress.checkCancelled();progress.recognizing(++n,ids.size());
                JsonNode read=ai.readQuestionImage(Files.readAllBytes(images.get((String)q.get("id"))),(String)q.get("type"));
                apply(q,read);progress.checkCancelled();
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.util.concurrent.CancellationException("Task stopped");}
        finally{
            // Let the bounded renderer close its browser after cancellation; it never clicks or submits.
            final Process pending=child;final Path cleanup=dir;
            if(pending!=null&&pending.isAlive()){Thread reaper=new Thread(()->{try{if(!pending.waitFor(50,TimeUnit.SECONDS))pending.destroyForcibly();}catch(InterruptedException ignored){pending.destroyForcibly();}clean(cleanup);},"quiz-render-cleanup");reaper.setDaemon(true);reaper.start();}
            else clean(dir);
        }
    }
    static void apply(Map<String,Object> q,JsonNode read)throws IOException{
        String type=(String)q.get("type"),title=read.path("title").asText("").trim();
        if(!read.path("complete").asBoolean(false)||!type.equals(read.path("type").asText())||title.isEmpty())throw new IOException("截图识别不完整或题型不一致，本次未提交");
        JsonNode options=read.path("options");List<String> lines=new ArrayList<>();
        if(!options.isArray())throw new IOException("截图选项格式错误，本次未提交");
        Set<String> labels=new HashSet<>();
        for(JsonNode o:options){String v=o.asText("").trim();if(v.isEmpty())throw new IOException("截图存在空选项，本次未提交");lines.add(v);}
        if("single".equals(type)||"multiple".equals(type)){
            if(lines.size()<2||lines.size()>26)throw new IOException("截图选项数量异常，本次未提交");
            for(int i=0;i<lines.size();i++)if(!lines.get(i).matches("^"+(char)('A'+i)+"[.、．)）\\s].+"))throw new IOException("截图选项字母缺失或不连续，本次未提交");
            String original=String.valueOf(q.get("options")).trim();if(!original.isEmpty()){
                int existing=original.split("\\n").length;if(existing!=lines.size())throw new IOException("网页与截图选项数量不一致，本次未提交");
            }
        }
        q.put("title",title);q.put("options",String.join("\n",lines));q.put("needsVision",false);q.put("recognitionSource","截图视觉识别");
    }
    private static void clean(Path dir){try(java.util.stream.Stream<Path> paths=Files.walk(dir)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}catch(IOException ignored){}}
}
