package com.tihai.utils;
import java.util.*;import java.io.*;import java.nio.file.*;import java.util.regex.*;import org.jsoup.*;import org.jsoup.nodes.*;import okhttp3.*;import com.fasterxml.jackson.databind.*;import org.springframework.stereotype.Service;
@Service public class HomeworkRetake {
 private final Path directory;private final ObjectMapper json=new ObjectMapper();
 public HomeworkRetake(){this(Paths.get("D:/tool/qita/SuperAutoStudy/data/homework-retakes"));}public HomeworkRetake(Path p){directory=p;}
 public synchronized void save(String id,Map<String,Object> plan)throws IOException{Files.createDirectories(directory);Path p=directory.resolve(UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8))+".json"),t=p.resolveSibling(p.getFileName()+".new");Files.write(t,json.writeValueAsBytes(plan));Files.move(t,p,StandardCopyOption.REPLACE_EXISTING);}
 public synchronized Map<String,Object> load(String id)throws IOException{Path p=directory.resolve(UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8))+".json");return Files.exists(p)?json.readValue(Files.readAllBytes(p),Map.class):null;}
 public static boolean submitted(String html){Document d=Jsoup.parse(html);if(!d.select("form#submitForm input[name^=answertype]").isEmpty())return false;return (!d.select(".fanyaMarking .resultNum").isEmpty()&&!d.select(".singleQuesId").isEmpty())||(!d.select(".testTit_status_complete").isEmpty()&&d.select(".testTit_status_complete").text().contains("已完成")&&!d.select(".achievement,.newAnswerBx,.myAnswer").isEmpty())||(!d.select(".subTime").isEmpty()&&d.select(".subTime").text().contains("提交时间"));}
 public static Map<String,Object> read(String html,String source)throws Exception{
  Document d=Jsoup.parse(html,source);List<Map<String,Object>> rows=ReviewParser.read(html);int valid=0;Set<String> ids=new HashSet<>();for(Map<String,Object> q:rows){if(!ids.add(String.valueOf(q.get("platformQuestionId"))))throw new IOException("平台题号重复");if(Boolean.TRUE.equals(q.get("gradeable"))&&Arrays.asList("single","multiple","judgement").contains(q.get("type")))valid++;}
  Map<String,Object> p=new LinkedHashMap<>();p.put("questions",rows);p.put("total",rows.size());p.put("available",valid);p.put("title",d.select(".mark_title").text());p.put("score",d.select(".resultNum").text());p.put("submitted",submitted(html));p.put("redoAvailable",d.select(".redo[onclick*=redoWork]").stream().anyMatch(ReviewParser::visible)&&html.contains("/work/phone/redo"));
  Matcher times=Pattern.compile("var\\s+redoTimes\\s*=\\s*(\\d+)\\s*\\+\\s*(\\d+)\\s*-\\s*(\\d+)").matcher(html);if(times.find())p.put("remaining",Integer.parseInt(times.group(1))+Integer.parseInt(times.group(2))-Integer.parseInt(times.group(3)));
  return p;
 }
 public static void complete(Map<String,Object> p)throws IOException{if(((Number)p.get("total")).intValue()==0||!p.get("total").equals(p.get("available")))throw new IOException("平台答案未完整公布或题型暂不支持；不会重做，也不会使用AI补齐");}
 static String val(Document d,String id)throws IOException{Element e=d.getElementById(id);if(e==null||!e.val().matches("[0-9]+"))throw new IOException("作业身份无法验证");return e.val();}
 public static Request reset(String html,String source)throws Exception{
  Map<String,Object> p=read(html,source);complete(p);if(!Boolean.TRUE.equals(p.get("submitted"))||!Boolean.TRUE.equals(p.get("redoAvailable"))||((Number)p.getOrDefault("remaining",1)).intValue()<=0)throw new IOException("平台当前没有可用重做入口");
  Document d=Jsoup.parse(html);HttpUrl u=HttpUrl.parse(source);if(u==null||!u.isHttps()||!u.host().endsWith(".chaoxing.com"))throw new IOException("页面来源错误");HttpUrl.Builder b=u.newBuilder().encodedPath("/mooc-ans/work/phone/redo").query(null);
  for(String field:Arrays.asList("courseId","classId","cpi","workId","answerId")){String value=val(d,field);String original=u.queryParameter(field);if(original!=null&&!original.equals(value))throw new IOException("作业身份变化");b.addQueryParameter(field.equals("answerId")?"workAnswerId":field,value);}
  return new Request.Builder().url(b.build()).header("Referer",source).header("X-Requested-With","XMLHttpRequest").get().build();
 }
 public static String editableUrl(String html,String source)throws IOException{
  Matcher m=Pattern.compile("location\\.href\\s*=\\s*([^;]+);",Pattern.DOTALL).matcher(html);Document d=Jsoup.parse(html);
  while(m.find()){String expr=m.group(1);Matcher s=Pattern.compile("[\"']([^\"']*)[\"']").matcher(expr);StringBuilder value=new StringBuilder();while(s.find())value.append(s.group(1));if(!expr.replaceAll("[\"'][^\"']*[\"']","").matches("[\\s+]*"))continue;HttpUrl origin=HttpUrl.parse(source),url=origin.resolve(value.toString());if(url==null||!url.isHttps()||!url.host().equals(origin.host())||!url.encodedPath().equals("/mooc-ans/mooc2/work/dowork"))continue;boolean same=true;for(String key:Arrays.asList("courseId","classId","cpi","workId","answerId"))same&=val(d,key).equals(url.queryParameter(key));if(same&&url.queryParameter("enc")!=null)return url.toString();}
  throw new IOException("未取得平台提供的重做地址，不会猜测签名");
 }
 private static String clean(String s){return s.replaceAll("\\s+","").trim();}
 private static String title(Object s){return clean(String.valueOf(s).replaceFirst("^\\s*[0-9]+[.、．]\\s*",""));}
 private static Map<String,String> choices(List<String> options)throws IOException{Map<String,String> m=new LinkedHashMap<>();for(String option:options){Matcher match=Pattern.compile("^\\s*([A-Z])[.、．)）]\\s*(.*)$",Pattern.DOTALL).matcher(option);if(!match.matches())throw new IOException("选项标签无法匹配");String text=clean(match.group(2));if(m.containsValue(text))throw new IOException("选项内容重复");m.put(match.group(1),text);}return m;}
 public static void match(List<Map<String,Object>> published,List<Map<String,Object>> current)throws IOException{
  if(published.size()!=current.size())throw new IOException("重做试卷题量变化，未提交");Set<Map<String,Object>> used=Collections.newSetFromMap(new IdentityHashMap<Map<String,Object>,Boolean>());
  for(Map<String,Object> q:current){if(Boolean.TRUE.equals(q.get("needsVision")))throw new IOException("题目未完整读取");List<Map<String,Object>> matches=new ArrayList<>();List<String> now=AssignmentDraft.options(q);for(Map<String,Object> old:published){if(!Objects.equals(q.get("type"),old.get("type"))||!title(q.get("title")).equals(title(old.get("title"))))continue;List<String> opts=(List<String>)old.get("options");if("judgement".equals(q.get("type"))||new HashSet<>(choices(now).values()).equals(new HashSet<>(choices(opts).values())))matches.add(old);}
   if(matches.size()>1){List<Map<String,Object>> exact=new ArrayList<>();for(Map<String,Object> item:matches)if(Objects.equals(q.get("id"),item.get("platformQuestionId")))exact.add(item);if(exact.size()==1)matches=exact;}
   if(matches.size()!=1||!used.add(matches.get(0)))throw new IOException("题目匹配存在歧义，未提交");Map<String,Object> old=matches.get(0);String answer=String.valueOf(old.get("canonicalAnswer"));if(!Boolean.TRUE.equals(old.get("gradeable")))throw new IOException("平台标准答案缺失");
   if(!"judgement".equals(q.get("type"))){Map<String,String> before=choices((List<String>)old.get("options")),after=choices(now);TreeSet<String> mapped=new TreeSet<>();for(char c:answer.toCharArray()){String text=before.get(String.valueOf(c)),label=null;for(Map.Entry<String,String> e:after.entrySet())if(e.getValue().equals(text))label=e.getKey();if(label==null)throw new IOException("正确选项无法对应");mapped.add(label);}answer=String.join("",mapped);}
   q.put("answer",answer);q.put("answerSource","平台公布答案");
  }
 }
}
