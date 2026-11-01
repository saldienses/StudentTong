package com.tihai.utils;
import org.springframework.stereotype.Service;import org.springframework.beans.factory.annotation.Autowired;import com.tihai.common.Question;import java.util.*;
@Service public class ReviewReferenceService {
 @Autowired private ReviewStore store;@Autowired private Query query;@Autowired private LiveProgressTracker progress;@Autowired private LearningTongAiClient learningTongAi;@Autowired private org.springframework.jdbc.core.JdbcTemplate db;
 private final Map<String,Object> locks=new java.util.concurrent.ConcurrentHashMap<>();
 public void ensure(String course,String id)throws Exception{
  Object lock=locks.computeIfAbsent(id,k->new Object());synchronized(lock){Map<String,Object> q=store.question(course,id);if(Boolean.TRUE.equals(q.get("gradeable"))||Boolean.TRUE.equals(q.get("referenceAvailable")))return;if(!ReviewStore.eligible(q))throw new java.io.IOException("题目内容或题型不支持参考判分");
   String trace="review-reference:"+id;progress.begin(trace);progress.quiz("复习参考答案",1);
   String oldAccount=null;boolean bound=false;if(learningTongAi!=null&&db!=null){List<String> accounts=db.queryForList("SELECT login_account FROM th_wk_queue WHERE id=?",String.class,course);if(!accounts.isEmpty()){oldAccount=learningTongAi.bindAccount(accounts.get(0));bound=true;}}
   try{Question question=new Question();question.setId(String.valueOf(q.get("platformQuestionId")));question.setType((String)q.get("type"));question.setTitle((String)q.get("title"));question.setTKOptions((List<String>)q.get("options"));question.setOptions(String.join("\n",question.getTKOptions()));String answer=query.queryAnswerSync(question);Map<String,Object> state=progress.snapshot(trace);String source=String.valueOf(state.get("answer_source"));String label=source.equals("题库已返回")?"题库参考答案":source.equals("DeepSeek 已返回")?"AI参考答案":source.equals("学习通 AI 已返回")?"学习通AI参考答案":"";if(label.isEmpty())throw new java.io.IOException("无法确认参考答案来源");store.reference(course,id,q,answer,label);}finally{if(bound)learningTongAi.restoreAccount(oldAccount);progress.release();}
  }
 }
}
