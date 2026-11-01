package com.tihai.utils;
import org.springframework.stereotype.Service;import java.util.*;import java.io.*;import java.nio.file.*;import com.fasterxml.jackson.databind.ObjectMapper;
@Service public class ExamStateStore {
 private final Path directory;private final ObjectMapper json=new ObjectMapper();public ExamStateStore(){this(Paths.get("D:/tool/qita/SuperAutoStudy/data/exams"));}public ExamStateStore(Path p){directory=p;}
 private Path file(String id)throws IOException{if(!id.matches("[a-zA-Z0-9-]+"))throw new IOException("考试记录标识无效");return directory.resolve(id+".json");}
 public synchronized void save(String id,Map<String,Object> data)throws IOException{Path f=file(id);Files.createDirectories(directory);Path t=f.resolveSibling(f.getFileName()+".new");Files.write(t,json.writeValueAsBytes(data));try{Files.move(t,f,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(t,f,StandardCopyOption.REPLACE_EXISTING);}}
 public synchronized Map<String,Object> load(String id)throws IOException{Path f=file(id);return Files.exists(f)?json.readValue(Files.readAllBytes(f),Map.class):null;}
 public static boolean mayHaveSubmitted(Map<String,Object> row){return row!=null&&(Boolean.TRUE.equals(row.get("submissionClaimed"))||Arrays.asList("submitting","uncertain","completed").contains(row.get("state")));}
}
