package com.tihai.utils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tihai.common.Question;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class QuizReadingFallbackTest {
    private final ObjectMapper json=new ObjectMapper();
    private String question(String title){return "<div class='singleQuesId' data='123'><div class='Zy_TItle'>"+title+"</div><div class='TiMu' data='0'></div><ul><li>A. Alpha</li><li>B. Beta</li></ul></div>";}
    private Map<String,Object> first(Map<String,Object> page){return ((List<Map<String,Object>>)page.get("questions")).get(0);}
    @Test void skipsCompletedReviewInsteadOfParsingReportForms()throws Exception{
        String html="<div class='testTit_status_complete'>已完成</div><span class='achievement'>本次成绩80分</span><form id='questionErrorForm1'></form>"+question("review");
        Map<String,Object> page=Decode.decodeQuestionsInfo(html);assertEquals(true,page.get("alreadySubmitted"));assertTrue(((List)page.get("questions")).isEmpty());
    }
    @Test void completedWordsInQuestionAreNotACompletionSignal()throws Exception{
        Map<String,Object> page=Decode.decodeQuestionsInfo("<form>"+question("如何判断已完成？")+"</form>");assertNull(page.get("alreadySubmitted"));assertEquals(false,first(page).get("needsVision"));
    }
    @Test void reportFormBeforeAnswerFormDoesNotHideQuestions()throws Exception{
        Map<String,Object> page=Decode.decodeQuestionsInfo("<form id='questionErrorForm1'></form><form><input name='enc' value='token'>"+question("A normal question")+"</form>");assertEquals("A normal question",first(page).get("title"));assertEquals("token",page.get("enc"));assertEquals("123,",page.get("answerwqbid"));
    }
    @Test void editableFieldsOverrideCompletedLabel()throws Exception{
        Map<String,Object> page=Decode.decodeQuestionsInfo("<div class='testTit_status_complete'>已完成</div><span class='achievement'>80</span><form><input name='answertype123' value='0'>"+question("New attempt")+"</form>");assertNull(page.get("alreadySubmitted"));assertEquals("single",first(page).get("type"));
    }
    @Test void imageQuestionRequestsVisionAndPlainTextDoesNot()throws Exception{
        assertEquals(true,first(Decode.decodeQuestionsInfo("<form>"+question("<img src='diagram.png'>")+"</form>")).get("needsVision"));
        assertEquals(false,first(Decode.decodeQuestionsInfo("<form>"+question("Text only")+"</form>")).get("needsVision"));
    }
    @Test void missingDuplicateOrUnknownIdsNeverBecomeSubmittable(){
        assertThrows(IOException.class,()->Decode.decodeQuestionsInfo("<form>"+question("Question").replace("data='123'","data=''")+"</form>"));
        assertThrows(IOException.class,()->Decode.decodeQuestionsInfo("<form>"+question("One")+question("Two")+"</form>"));
        assertThrows(IOException.class,()->Decode.decodeQuestionsInfo("<form>"+question("Question").replace("data='0'","data='99'")+"</form>"));
    }
    @Test void unknownPageDoesNotUseReportForm(){assertThrows(IOException.class,()->Decode.decodeQuestionsInfo("<form id='questionErrorForm1'></form><p>登录后查看</p>"));}
    @Test void missingOldContainerClassStillUsesVerifiedAnswerFieldIds()throws Exception{
        String html="<form><input name='answertype123' value='0'><div id='question123'><img src='question.png'></div></form>";
        Map<String,Object> q=first(Decode.decodeQuestionsInfo(html));assertEquals("123",q.get("id"));assertEquals("single",q.get("type"));assertEquals(true,q.get("needsVision"));
    }
    @Test void plainWebQuestionsDoNotInvokeVision()throws Exception{
        QuizVisionService service=new QuizVisionService();service.fill("",Decode.decodeQuestionsInfo("<form>"+question("Text only")+"</form>"));
    }
    @Test void disabledVisionFailsBeforeAnyScreenshotOrSubmission()throws Exception{
        QuizVisionService service=new QuizVisionService();DeepSeekClient ai=org.mockito.Mockito.mock(DeepSeekClient.class);org.springframework.test.util.ReflectionTestUtils.setField(service,"ai",ai);
        assertThrows(IOException.class,()->service.fill("",Decode.decodeQuestionsInfo("<form>"+question("<img src='x.png'>")+"</form>")));org.mockito.Mockito.verify(ai).isVisionEnabled();org.mockito.Mockito.verifyNoMoreInteractions(ai);
    }
    @Test void judgementAcceptsChineseAndRealJsonBooleansButNeverAmbiguity()throws Exception{
        Question q=new Question();q.setType("judgement");
        assertEquals("正确",DeepSeekClient.normalize(q,json.readTree("[true]")));
        assertEquals("错误",DeepSeekClient.normalize(q,json.readTree("[false]")));
        assertEquals("正确",DeepSeekClient.normalize(q,json.readTree("[\"正确\"]")));
        assertEquals("错误",DeepSeekClient.normalize(q,json.readTree("[\"错\"]")));
        assertThrows(IOException.class,()->DeepSeekClient.normalize(q,json.readTree("[\"可能正确\"]")));
        assertThrows(IOException.class,()->DeepSeekClient.normalize(q,json.readTree("[1]")));
    }
    @Test void visionMustMatchTypeAndCompleteAllOptionLetters()throws Exception{
        Map<String,Object> q=new HashMap<>();q.put("id","123");q.put("type","single");q.put("options","A.\nB.");
        QuizVisionService.apply(q,json.readTree("{\"complete\":true,\"type\":\"single\",\"title\":\"Visible question\",\"options\":[\"A. Alpha\",\"B. Beta\"]}"));assertEquals("Visible question",q.get("title"));assertEquals(false,q.get("needsVision"));assertEquals("123",q.get("id"));
        assertThrows(IOException.class,()->QuizVisionService.apply(q,json.readTree("{\"complete\":false,\"type\":\"single\",\"title\":\"x\",\"options\":[\"A. x\",\"B. y\"]}")));
        assertThrows(IOException.class,()->QuizVisionService.apply(q,json.readTree("{\"complete\":true,\"type\":\"multiple\",\"title\":\"x\",\"options\":[\"A. x\",\"B. y\"]}")));
        assertThrows(IOException.class,()->QuizVisionService.apply(q,json.readTree("{\"complete\":true,\"type\":\"single\",\"title\":\"x\",\"options\":[\"A. x\",\"C. y\"]}")));
        assertThrows(IOException.class,()->QuizVisionService.apply(q,json.readTree("{\"complete\":true,\"type\":\"single\",\"title\":\"x\",\"options\":[\"A. x\",\"B. y\",\"C. z\"]}")));
    }
}
