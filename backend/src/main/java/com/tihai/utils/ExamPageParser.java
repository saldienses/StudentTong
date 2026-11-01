package com.tihai.utils;
import org.jsoup.Jsoup;import org.jsoup.nodes.*;import java.util.*;import java.io.*;import java.util.regex.*;
public final class ExamPageParser {
 private ExamPageParser(){}
 public static List<Map<String,Object>> questions(String html)throws Exception{
  Document d=Jsoup.parse(html);FontDecoder decoder=new FontDecoder(html);List<Map<String,Object>> out=new ArrayList<>();Set<String> ids=new HashSet<>();
  for(Element node:d.select(".singleQuesId")){String id=node.attr("data");if(!id.matches("[0-9]+")||!ids.add(id))throw new IOException("考试题号缺失或重复");Element t=node.selectFirst("input[name=type"+id+"],input[name=answertype"+id+"]");if(t==null)throw new IOException("考试题型无法识别");String type;switch(t.val()){case "0":type="single";break;case "1":type="multiple";break;case "2":type="fill";break;case "3":type="judgement";break;case "4":type="short";break;default:throw new IOException("本次考试含暂未支持题型，未自动交卷");}
   Element title=node.selectFirst(".mark_name,.Zy_TItle");if(title==null)throw new IOException("考试题干未读取");String text=decoder.decode(title.text().replaceFirst("^\\s*[0-9]+[.、．]\\s*",""));List<String> opts=new ArrayList<>();boolean image=!title.select("img,canvas,svg").isEmpty();
   for(Element opt:node.select(".stem_answer .answerBg")){Element label=opt.selectFirst(".num_option,.num_option_dx"),body=opt.selectFirst(".answer_p");if(body==null)throw new IOException("考试选项未完整读取");image|=!body.select("img,canvas,svg").isEmpty();String letter=label==null?"":label.text().trim();opts.add(letter+". "+decoder.decode(body.text()));}
   if(Arrays.asList("single","multiple").contains(type)&&opts.size()<2)throw new IOException("考试选项数量不足");Map<String,Object> q=new LinkedHashMap<>();q.put("id",id);q.put("type",type);q.put("title",text);q.put("options",String.join("\n",opts));q.put("needsVision",image||text.matches("(?s).*[\\uE000-\\uF8FF].*"));out.add(q);
  }if(out.isEmpty())throw new IOException("未识别到考试题目");return out;
 }
 public static int total(String html)throws IOException{Document d=Jsoup.parse(html);Set<String> nav=new HashSet<>();for(Element e:d.select("li[onclick*=getTheQuestionByStart]")){Matcher m=Pattern.compile("getTheQuestionByStart\\((\\d+)").matcher(e.attr("onclick"));if(m.find())nav.add(m.group(1));}if(!nav.isEmpty())return nav.size();Matcher m=Pattern.compile("题量\\s*[:：]\\s*(\\d+)").matcher(d.text());if(m.find())return Integer.parseInt(m.group(1));throw new IOException("未能验证考试总题数");}
 public static int index(String html){Element e=Jsoup.parse(html).getElementById("start");try{return e==null?0:Integer.parseInt(e.val());}catch(Exception ignored){return 0;}}
 public static void identity(String html,String course,String clazz,String exam)throws IOException{Document d=Jsoup.parse(html);for(String[] p:new String[][]{{"courseId",course},{"classId",clazz},{"testPaperId",exam}}){Element e=d.getElementById(p[0]);if(e==null||!p[1].equals(e.val()))throw new IOException("考试身份发生变化，已停止自动操作");}}
}
