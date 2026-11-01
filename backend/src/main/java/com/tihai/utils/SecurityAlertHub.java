package com.tihai.utils;
import org.springframework.stereotype.Service;import java.util.*;import java.util.concurrent.ConcurrentHashMap;
@Service public class SecurityAlertHub {
 private final Map<String,Map<String,Object>> alerts=new ConcurrentHashMap<>();
 public synchronized void raise(String module,String task,String title,String message){String key=module+":"+task;if(alerts.containsKey(key))return;Map<String,Object> a=new LinkedHashMap<>();a.put("eventId",UUID.randomUUID().toString());a.put("module",module);a.put("task",task);a.put("title",title);a.put("message",message);alerts.put(key,a);}
 public void resolve(String module,String task){alerts.remove(module+":"+task);}
 public List<Map<String,Object>> snapshot(){List<Map<String,Object>> rows=new ArrayList<>();for(Map<String,Object> a:alerts.values())rows.add(new LinkedHashMap<>(a));return rows;}
 public boolean detect(String module,String task,String title,String text){if(text!=null&&text.matches("(?s).*(安全验证|滑块|验证码|人脸验证|访问过于频繁|异常登录|账号异常|需要.*专用客户端).*")){raise(module,task,title,"平台安全提示需要人工处理，请核对平台页面。自动流程不会跳过安全验证。");return true;}return false;}
}
