package com.tihai.utils;
import com.tihai.common.Question;
import java.util.*;
import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import okhttp3.*;

/** Draft validation is independent of network calls and never invents form fields. */
public final class AssignmentDraft {
 private AssignmentDraft(){}
 public static String signature(List<Map<String,Object>> questions){StringBuilder s=new StringBuilder();for(Map<String,Object> q:questions)s.append(q.get("id")).append('|').append(q.get("type")).append('|').append(q.get("title")).append('|').append(q.get("options")).append('\n');return s.toString();}
 public static String answer(Query query,Map<String,Object> row)throws IOException{
  Question q=new Question();q.setId((String)row.get("id"));q.setTitle((String)row.get("title"));q.setType((String)row.get("type"));q.setOptions((String)row.get("options"));q.setTKOptions(options(row));
  return AnswerMapper.map(q.getType(),query.queryAnswerSync(q),q.getTKOptions());
 }
 public static List<String> options(Map<String,Object> row){String text=String.valueOf(row.get("options"));return text.trim().isEmpty()?Collections.emptyList():Arrays.asList(text.trim().matches("(?s)^[A-Z][.、．)）].*")?text.split("\\n(?=\\s*[A-Z][.、．)）]\\s*)"):text.split("\\n+"));}
 public static Request request(String html,String source,Map<String,Object> fresh,List<Map<String,Object>> draft)throws Exception{
  List<Map<String,Object>> current=(List<Map<String,Object>>)fresh.get("questions");
  if(!signature(current).equals(signature(draft)))throw new IOException("题目已变化，请重新生成答案");
  Document doc=Jsoup.parse(html,source);Element form=doc.selectFirst("form#submitForm");
  if(form==null||!"post".equalsIgnoreCase(form.attr("method")))throw new IOException("独立作业提交表单不受支持");
  HttpUrl target=HttpUrl.parse(form.absUrl("action"));HttpUrl origin=HttpUrl.parse(source);
  if(target==null||origin==null||!target.isHttps()||!target.host().equals(origin.host())||!"/mooc-ans/work/addStudentWorkNewWeb".equals(target.encodedPath()))throw new IOException("独立作业提交地址无法验证");
  // Current native page explicitly adds these parameters for saving/submitting answers.
  target=target.newBuilder().setQueryParameter("ua","pc").setQueryParameter("formType","post").setQueryParameter("saveStatus","1").setQueryParameter("version","1").build();
  Map<String,String> metadata=new LinkedHashMap<>();
  for(Element input:form.select("input[type=hidden][name]")){String name=input.attr("name");if(!name.matches("(?:answer|answertype)[0-9]+.*"))metadata.put(name,input.val());}
  for(Map.Entry<String,Object> e:fresh.entrySet())if(!Arrays.asList("questions","alreadySubmitted").contains(e.getKey()))metadata.put(e.getKey(),String.valueOf(e.getValue()));
  if(metadata.containsKey("pyFlag"))metadata.put("pyFlag","");
  if(metadata.containsKey("questionIds")){List<String> ids=new ArrayList<>();for(Map<String,Object> q:draft)ids.add(String.valueOf(q.get("id")));metadata.put("questionIds",String.join(",",ids)+",");}
  FormBody.Builder fields=new FormBody.Builder(java.nio.charset.StandardCharsets.UTF_8);
  for(Map.Entry<String,String> e:metadata.entrySet())fields.add(e.getKey(),e.getValue());
  for(Map<String,Object> q:draft){String answer=(String)q.get("answer");if(answer==null||answer.trim().isEmpty())throw new IOException("存在空答案，未提交");String type=(String)q.get("type");
   if(!Arrays.asList("single","multiple","judgement").contains(type))throw new IOException("当前自动提交仅验证了选择题和判断题，其他题型请核对后处理");
   // Answers here are already normalized to labels / true-false during generation or editing.
   if(("single".equals(type)&&!answer.matches("[A-Z]"))||("multiple".equals(type)&&!answer.matches("[A-Z]+"))||("judgement".equals(type)&&!Arrays.asList("true","false").contains(answer)))throw new IOException("答案格式不合法，未提交");
   fields.add("answer"+q.get("id"),answer);fields.add("answertype"+q.get("id"),String.valueOf(((Map)q.get("answerField")).get("answertype"+q.get("id"))));
  }
  return new Request.Builder().url(target).header("Referer",source).header("Origin",origin.scheme()+"://"+origin.host()).header("X-Requested-With","XMLHttpRequest").post(fields.build()).build();
 }
}
