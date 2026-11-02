package com.tihai.utils;
import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class AssignmentMarkupTest {
 private String question(String id,String code,String title,String options){return "<input type='hidden' name='answertype"+id+"' value='"+code+"'><div class='questionLi fontLabel singleQuesId' data='"+id+"'><h3 class='mark_name workTextWrap'>"+title+"</h3><div class='stem_answer qtDetail'>"+options+"</div></div>";}
 private String option(String label,String text){return "<div class='clearfix answerBg workTextWrap'><span class='num_option fl'>"+label+"</span><div class='fl answer_p'>"+text+"</div></div>";}
 private List<Map<String,Object>> read(String html)throws Exception{return (List<Map<String,Object>>)QuizPageReader.read("<form id='submitForm'>"+html+"</form>").get("questions");}
 @Test void plainAssignmentContentIsReadableWithoutVision()throws Exception{
  Map<String,Object> q=read(question("123","0","1. (单选题) [1分]选择正确的说法。",option("A","第一种说法")+option("B","第二种说法"))).get(0);
  assertEquals("(单选题) [1分]选择正确的说法。",q.get("title"));assertEquals("A. 第一种说法\nB. 第二种说法",q.get("options"));assertEquals(false,q.get("needsVision"));assertEquals("single",q.get("type"));
 }
 @Test void mixedMarkupAndDifferentQuestionsKeepTheirOwnOptions()throws Exception{
  String modern=question("123","1","1. 多选题干",option("A、","甲")+option("B","乙"));
  String old="<div class='singleQuesId' data='456'><div class='Zy_TItle'>旧版题干</div><div class='TiMu' data='0'></div><ul><li>A. 丙</li><li>B. 丁</li></ul></div>";
  List<Map<String,Object>> qs=read(modern+old);assertEquals(2,qs.size());assertEquals("A. 甲\nB. 乙",qs.get(0).get("options"));assertEquals("A. 丙\nB. 丁",qs.get(1).get("options"));assertEquals("multiple",qs.get(0).get("type"));
 }
 @Test void realImagesStillRequireVisionAndAreNotMislabelledAsPlainText()throws Exception{
  Map<String,Object> q=read(question("123","0","1. <img src='figure.png'>",option("A","甲")+option("B","乙"))).get(0);assertEquals(true,q.get("needsVision"));
 }
 @Test void questionAndOptionFormattingSupportsAnswerMapping()throws Exception{
  Map<String,Object> q=read(question("123","0","1. 题干",option("A","选项带空格")+option("B","另一选项"))).get(0);
  assertEquals("A",AnswerMapper.map("single","选项带空格",Arrays.asList(((String)q.get("options")).split("\n"))));
 }
}
