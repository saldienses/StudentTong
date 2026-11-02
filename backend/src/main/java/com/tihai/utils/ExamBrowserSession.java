package com.tihai.utils;
import java.util.*;import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import com.fasterxml.jackson.databind.ObjectMapper;
public interface ExamBrowserSession extends AutoCloseable {
 void send(Map<String,Object> command)throws IOException;Map<String,Object> read()throws IOException;void close();
 class ProcessSession implements ExamBrowserSession {
  private final Process process;private final BufferedReader reader;private final Writer writer;private final ObjectMapper json=new ObjectMapper();private final Path temp;
  public ProcessSession(Path root,String id)throws IOException{this(root,id,"exam-bridge.cjs");}
  public ProcessSession(Path root,String id,String script)throws IOException{if(!script.matches("[a-z-]+\\.cjs"))throw new IOException("无效的浏览器桥接脚本");temp=Files.createTempDirectory(root.resolve("temp"),"exam-browser-");ProcessBuilder b=new ProcessBuilder(root.resolve("runtime/node/node.exe").toString(),root.resolve("config").resolve(script).toString());b.directory(root.toFile());b.environment().put("TEMP",temp.toString());b.environment().put("TMP",temp.toString());b.environment().put("STUDYDESK_ROOT",root.toString());b.redirectError(root.resolve("logs/exam-bridge-"+id+".err").toFile());process=b.start();reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8));writer=new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8);}
  public synchronized void send(Map<String,Object> command)throws IOException{writer.write(json.writeValueAsString(command));writer.write('\n');writer.flush();}
  public Map<String,Object> read()throws IOException{String line=reader.readLine();if(line==null)throw new IOException("考试浏览器已关闭；平台考试可能继续计时，请核对平台");return json.readValue(line,Map.class);}
  public void close(){try{send(Collections.singletonMap("op","close"));if(!process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS))process.destroy();}catch(Exception ignored){process.destroy();}try(java.util.stream.Stream<Path> paths=Files.walk(temp)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}catch(Exception ignored){}}
 }
}
