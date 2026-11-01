package com.tihai.utils;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import com.fasterxml.jackson.databind.*;

@Service
public class AiSettingsService {
    private final Path file;
    private final OkHttpClient http;
    public AiSettingsService(){this(Paths.get("D:/tool/qita/SuperAutoStudy/config/deepseek.properties"),new OkHttpClient.Builder().callTimeout(25,TimeUnit.SECONDS).build());}
    AiSettingsService(Path file,OkHttpClient http){this.file=file;this.http=http;}
    private Properties read() throws IOException {Properties p=new Properties();if(Files.exists(file))try(Reader r=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(r);}return p;}
    private String model(String requested,Properties existing){String m=requested==null?existing.getProperty("model","deepseek-flash"):requested;if(!Arrays.asList("deepseek-flash","deepseek-v4-pro").contains(m))throw new IllegalArgumentException("不支持的模型");return m;}
    private String key(String requested,Properties existing){String k=requested==null||requested.trim().isEmpty()?existing.getProperty("api-key",""):requested.trim();if(!k.isEmpty()&&!k.matches("[A-Za-z0-9_-]{15,512}"))throw new IllegalArgumentException("API Key 格式不正确");return k;}
    private Map<String,Object> metadata(Properties p){Map<String,Object> m=new LinkedHashMap<>();m.put("learningTongEnabled",Boolean.parseBoolean(p.getProperty("learningtong-enabled","false")));m.put("visionFallback",Boolean.parseBoolean(p.getProperty("vision-fallback","true")));m.put("visionModel","deepseek-flash");m.put("provider","DeepSeek");m.put("model",p.getProperty("model","deepseek-flash"));m.put("enabled",Boolean.parseBoolean(p.getProperty("enabled","false")));m.put("configured",!p.getProperty("api-key","").trim().isEmpty());m.put("endpoint","https://api.deepseek.com");return m;}
    public synchronized Map<String,Object> current() throws IOException {return metadata(read());}
    public synchronized Map<String,Object> save(String requestedKey,String requestedModel,boolean enabled) throws IOException {
        return save(requestedKey,requestedModel,enabled,null);
    }
    public synchronized Map<String,Object> save(String requestedKey,String requestedModel,boolean enabled,Boolean vision) throws IOException {
        return save(requestedKey,requestedModel,enabled,vision,null);
    }
    public synchronized Map<String,Object> save(String requestedKey,String requestedModel,boolean enabled,Boolean vision,Boolean learningTong) throws IOException {
        Properties p=read();if(vision!=null)p.setProperty("vision-fallback",String.valueOf(vision));if(learningTong!=null)p.setProperty("learningtong-enabled",String.valueOf(learningTong));String k=key(requestedKey,p),m=model(requestedModel,p);if(enabled&&k.isEmpty())throw new IllegalArgumentException("请先填写 API Key");
        p.setProperty("api-key",k);p.setProperty("model",m);p.setProperty("enabled",String.valueOf(enabled));Files.createDirectories(file.getParent());Path temp=file.resolveSibling(file.getFileName()+".new");
        try(Writer w=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){p.store(w,"SuperAutoStudy local AI settings");}
        try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}return metadata(p);
    }
    public synchronized Map<String,Object> test(String requestedKey,String requestedModel) throws IOException {
        Properties p=read();String k=key(requestedKey,p),m=model(requestedModel,p);if(k.isEmpty())throw new IllegalArgumentException("请填写 API Key 或先保存密钥");
        Request r=new Request.Builder().url("https://api.deepseek.com/models").header("Authorization","Bearer "+k).get().build();
        try(Response response=http.newCall(r).execute()){
            if(!response.isSuccessful())throw new IOException(response.code()==401?"密钥无效或没有访问权限":"DeepSeek 连接失败，HTTP "+response.code());
            if(response.body()==null)throw new IOException("DeepSeek 未返回模型列表");JsonNode data=new ObjectMapper().readTree(response.body().string()).path("data");boolean found=false;for(JsonNode item:data)if(m.equals(item.path("id").asText()))found=true;
            if(!found)throw new IOException("账户暂不支持所选模型");Map<String,Object> result=new LinkedHashMap<>();result.put("connected",true);result.put("model",m);return result;
        }
    }
}
