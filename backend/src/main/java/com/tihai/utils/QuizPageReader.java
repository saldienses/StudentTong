package com.tihai.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.*;

/** Read editable quizzes separately from submitted review pages. Never invent submission IDs. */
public final class QuizPageReader {
    private QuizPageReader() {}
    public static Map<String,Object> read(String html) throws Exception {
        Document doc=Jsoup.parse(html);
        Map<String,Object> result=new LinkedHashMap<>();
        boolean editable=!doc.select("input[name^=answertype],textarea[name^=answer],input[name^=answer]:not([type=hidden])").isEmpty();
        boolean complete=HomeworkRetake.submitted(html)||!doc.select(".testTit_status_complete").isEmpty()
                && doc.select(".testTit_status_complete").text().contains("已完成")
                && !doc.select(".achievement,.newAnswerBx,.myAnswer").isEmpty();
        if(complete&&!editable){result.put("alreadySubmitted",true);result.put("questions",Collections.emptyList());return result;}
        Element form=null;
        for(Element f:doc.select("form"))if(!f.select(".singleQuesId,input[name=answerwqbid],input[name^=answertype]").isEmpty()){form=f;break;}
        if(form==null){
            // Retain support for older simple forms, but never parse report-error forms as answer forms.
            if(doc.select("form").size()==1&&!doc.selectFirst("form").id().startsWith("questionError"))form=doc.selectFirst("form");
            else throw new IOException("未找到可作答的测验表单；若平台已交卷请核对成绩页，未提交任何答案");
        }
        for(Element input:form.select("input[name]")){
            String name=input.attr("name");
            if(!name.startsWith("answer")&&!"radio".equals(input.attr("type"))&&!"checkbox".equals(input.attr("type")))result.put(name,input.val());
        }
        Elements nodes=form.select(".singleQuesId");
        if(nodes.isEmpty()){
            for(Element field:form.select("input[name^=answertype]")){
                String id=field.attr("name").substring("answertype".length());
                if(id.matches("[0-9]+")){Element question=form.getElementById("question"+id);if(question!=null&&!nodes.contains(question))nodes.add(question);}
            }
        }
        if(nodes.isEmpty())throw new IOException("未找到题目及题号，无法安全匹配截图结果，本次小测未提交");
        FontDecoder decoder=null;
        try{decoder=new FontDecoder(html);}catch(Exception ignored){/* Render original font for visual fallback. */}
        List<Map<String,Object>> questions=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(Element node:nodes){
            String id=node.attr("data");
            if(id.isEmpty())id=node.id().replaceFirst("^question","");
            if(!id.matches("[0-9]+")||!ids.add(id))throw new IOException("题号缺失或重复，无法安全匹配截图结果，本次小测未提交");
            Element typeInput=form.selectFirst("input[name=answertype"+id+"]");
            Element typeNode=node.hasClass("TiMu")&&node.hasAttr("data-type")?node:node.selectFirst(".TiMu[data]");
            String code=typeInput!=null?typeInput.val():typeNode!=null?typeNode.attr(typeNode.hasAttr("data-type")?"data-type":"data"):node.attr("data-type");
            String type=typeName(code);
            Element titleNode=node.selectFirst(".mark_name,.Zy_TItle,.question-title,.qtContent");
            String title=titleNode==null?"":titleNode.text();
            if(titleNode!=null&&titleNode.hasClass("mark_name"))title=title.replaceFirst("^\\s*[0-9]+[.、．]\\s*","");
            List<String> options=new ArrayList<>();
            Elements modernOptions=node.select(".stem_answer .answerBg");
            if(!modernOptions.isEmpty()){
                for(Element option:modernOptions){
                    Element label=option.selectFirst(".num_option,.num_option_dx"),body=option.selectFirst(".answer_p");
                    String letter=label==null?"":label.text().trim().replaceFirst("[.、．)）]$","");
                    if(letter.matches("[A-Za-z]")&&body!=null)options.add(letter.toUpperCase(Locale.ROOT)+". "+body.text());
                    else options.add(option.text());
                }
            }else{
                Elements li=node.select("ul li");
                for(Element e:li)options.add(e.text());
            }
            boolean vision=decoder==null||title.trim().isEmpty()||!node.select("img,canvas,svg").isEmpty();
            if(decoder!=null){String decoded=decoder.decode(title);if(decoded.length()!=title.length())vision=true;title=decoded;for(int i=0;i<options.size();i++){String text=decoder.decode(options.get(i));if(text.length()!=options.get(i).length())vision=true;options.set(i,text);}}
            if(title.trim().isEmpty()||title.matches("(?s).*[\\uE000-\\uF8FF].*"))vision=true;
            if(("single".equals(type)||"multiple".equals(type))&&(options.size()<2||options.stream().anyMatch(v->v.trim().isEmpty())))vision=true;
            Map<String,Object> q=new LinkedHashMap<>();q.put("id",id);q.put("title",title);q.put("options",String.join("\n",options));q.put("type",type);q.put("needsVision",vision);
            Map<String,Object> fields=new LinkedHashMap<>();fields.put("answer"+id,"");fields.put("answertype"+id,code);q.put("answerField",fields);questions.add(q);
        }
        result.put("questions",questions);result.put("answerwqbid",String.join(",",idsInOrder(questions))+",");return result;
    }
    private static List<String> idsInOrder(List<Map<String,Object>> qs){List<String> ids=new ArrayList<>();for(Map<String,Object> q:qs)ids.add((String)q.get("id"));return ids;}
    private static String typeName(String code)throws IOException{
        switch(code){case "0":return "single";case "1":return "multiple";case "2":return "fill";case "3":return "judgement";case "4":return "short";default:throw new IOException("题型无法识别，未提交任何答案");}
    }
}
