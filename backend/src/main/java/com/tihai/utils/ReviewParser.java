package com.tihai.utils;
import org.jsoup.Jsoup;import org.jsoup.nodes.*;import java.util.*;
public final class ReviewParser {
 private ReviewParser(){}
 public static List<Map<String,Object>> read(String html)throws Exception{
  Document doc=Jsoup.parse(html);FontDecoder fd=null;try{fd=new FontDecoder(html);}catch(Exception ignored){}
  List<Map<String,Object>> result=new ArrayList<>();
  for(Element node:doc.select(".singleQuesId")){
   String id=node.attr("data");if(id.isEmpty())id=node.id().replaceFirst("^question","");if(!id.matches("[0-9]+"))throw new java.io.IOException("题号无法识别，未按完整收录处理");
   Element title=node.selectFirst(".mark_name,.Zy_TItle,.question-title,.qtContent");
   String text=(title==null?"[题干未完整读取]":title.text()).replaceFirst("^\\s*[0-9]+[.、．]\\s*","");boolean incomplete=title==null||fd==null||!node.select("img,canvas,svg").isEmpty();
   if(fd!=null){String decoded=fd.decode(text);if(decoded.length()!=text.length())incomplete=true;text=decoded;}
   List<String> options=new ArrayList<>();org.jsoup.select.Elements modern=node.select(".stem_answer .answerBg");
   if(!modern.isEmpty())for(Element e:modern){Element label=e.selectFirst(".num_option,.num_option_dx"),body=e.selectFirst(".answer_p");String letter=label==null?"":label.text().replaceFirst("[.、．)）]$","");String value=letter.matches("[A-Za-z]")&&body!=null?letter.toUpperCase()+". "+body.text():e.text();options.add(fd==null?value:fd.decode(value));}
   else for(Element e:node.select("ul li"))options.add(fd==null?e.text():fd.decode(e.text()));
   Element typeInput=doc.selectFirst("input[name=answertype"+id+"]"),typeDiv=node.selectFirst(".TiMu[data]");
   String code=typeInput!=null?typeInput.val():typeDiv!=null?typeDiv.attr("data"):node.attr("data-type");
   String type="0".equals(code)?"single":"1".equals(code)?"multiple":"2".equals(code)?"fill":"3".equals(code)?"judgement":"4".equals(code)?"short":text.contains("多选题")?"multiple":text.contains("单选题")?"single":text.contains("判断题")?"judgement":text.contains("填空题")?"fill":text.contains("简答题")?"short":"unknown";
   if(Arrays.asList("single","multiple").contains(type)&&options.size()<2)incomplete=true;
   String official="";
   // Only explicit visible published answers. Scores and the student's answer are never substituted.
   for(Element answer:node.select(".rightAnswer,.correctAnswer,.rightAnswerContent,.right-answer,.referenceAnswer")){
    if(!visible(answer))continue;String raw=answer.text();boolean labelled=raw.matches("(?s).*(正确答案|参考答案|标准答案).*" );
    if(!labelled&&answer.parent()!=null&&answer.parent()!=node)labelled=answer.parent().text().matches("(?s).*(正确答案|参考答案|标准答案).*" );
    if(!labelled)continue;Element value=answer.selectFirst(".answerCon,.answerContent");if(value!=null&&visible(value))raw=value.text();else raw=raw.replaceFirst("(?s)^.*?(正确答案|参考答案|标准答案)\\s*[:：]?\\s*","");
    if(fd!=null)raw=fd.decode(raw);if(!raw.trim().isEmpty()){official=raw.trim();break;}
   }
   String student="";Element own=node.selectFirst(".myAnswer .answerCon,.myAnswer .answerContent");if(own!=null&&visible(own))student=fd==null?own.text():fd.decode(own.text());
   String explanation="";Element exp=node.selectFirst(".answerAnalysis,.analysisContent,.questionAnalysis");if(exp!=null&&visible(exp))explanation=fd==null?exp.text():fd.decode(exp.text());
   Map<String,Object> q=new LinkedHashMap<>();q.put("platformQuestionId",id);q.put("title",text);q.put("type",type);q.put("options",options);q.put("platformAnswer",official);q.put("studentAnswer",student);q.put("explanation",explanation);q.put("needsVision",incomplete||text.isEmpty());
   q.put("answerSource",official.isEmpty()?"平台未公布":"平台公布答案");String canonical="";
   if(!incomplete&&Arrays.asList("single","multiple","judgement","fill").contains(type)&&!official.isEmpty())try{canonical=canonical(type,official,options);}catch(Exception ignored){}
   q.put("canonicalAnswer",canonical);q.put("gradeable",!canonical.isEmpty());result.add(q);
  }return result;
 }
 static boolean visible(Element e){for(Element n=e;n!=null;n=n.parent()){String style=n.attr("style").toLowerCase().replace(" ","");if(n.hasAttr("hidden")||style.contains("display:none")||style.contains("visibility:hidden")||n.hasClass("hide")||n.hasClass("hidden"))return false;}return true;}
 public static String canonical(String type,String raw,List<String> options)throws java.io.IOException{
  String clean=raw.trim();if(Arrays.asList("single","multiple").contains(type)){String letters=clean.replaceAll("[,，、\\s]","").toUpperCase(Locale.ROOT);if(letters.matches("[A-Z]+"))clean=letters;}
  return AnswerMapper.map(type,clean,options);
 }
}
