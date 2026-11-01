using System;using System.Collections.Generic;using System.Threading;using System.Threading.Tasks;using StudyDesk;using StudyDesk.Contracts;
namespace Assessments {
 public abstract class Provider:IStudyModuleProvider {
  IServiceClient client;public abstract string Id{get;}public void Initialize(IServiceClient value){client=value;}
  public async Task<ModuleSnapshot> LoadAsync(CancellationToken c){var r=await client.RequestAsync(Id=="exams"?"/local/exams/list":"/local/assessments/"+Id,null,c);var data=Data.Map(r,"data");var tasks=new List<StudyTask>();
   foreach(var item in Data.Rows(data,"items")){
    var job=Data.Map(item,"job");string state=Data.Text(job,"state"),label=Data.Text(item,"status");var actions=new List<string>();if(Id=="homework"){actions.Add("view");if(job==null||state=="completed"||state=="failed"||state=="stopped")actions.Add("retake");if(job==null||state=="failed"||state=="stopped")actions.Add("answer");if(state=="generating")actions.Add("answer-stop");if(state=="ready")actions.Add("submit");if(job!=null&&state!="generating"&&state!="submitting")actions.Add("draft");}
    if(Id=="exams"){actions.Add("exam-notes");if(Data.Flag(item,"actionable")&&(job==null||state=="failed"||state=="stopped"||state=="interrupted"))actions.Add("exam-answer");if(job!=null){actions.Add("draft");if(state=="ready")actions.Add("submit");if(state=="queued"||state=="starting"||state=="verification"||state=="generating"||state=="ready")actions.Add("answer-stop");}}
    if(job!=null)label=state=="verification"?"等待安全验证":state=="starting"?"正在进入考试":state=="queued"?"排队中":state=="interrupted"?"已中断":state=="generating"?"自动答题中":state=="ready"?"待提交":state=="submitting"?"提交中":state=="completed"?"已完成":state=="uncertain"?"待平台确认":state=="stopped"?"已停止":"执行异常";
    Dictionary<string,object> live=null;if(job!=null)live=new Dictionary<string,object>{{"active",state=="generating"||state=="submitting"},{"phase_label",Data.Text(job,"message")},{"question_index",Data.Number(job,"done")},{"question_total",Data.Number(job,"total")},{"prepared_questions",Data.Number(job,"done")},{"answer_source",Data.Flag(job,"autoSubmit")?"本次允许自动提交":"本次仅生成草稿"}};
    tasks.Add(new StudyTask{Id=Data.Text(item,"id"),Title=Data.Text(item,"title"),State=label,Detail=job==null?Data.Text(item,"detail"):Data.Text(job,"message"),Actions=actions.ToArray(),Live=live});
   }
   return new ModuleSnapshot{Available=true,Message=Data.Text(data,"message")+(Id=="exams"?" · 支持自动作答 / 可选自动交卷 / 人工安全验证":" · 自动答题 / 草稿核对 / 可选自动尝试提交"),Tasks=tasks};
  }
  static string OptionText(Dictionary<string,object> q){object v;if(!q.TryGetValue("options",out v)||v==null)return "";if(v is string)return (string)v;var items=v as System.Collections.IEnumerable;if(items==null)return Convert.ToString(v);var values=new List<string>();foreach(object item in items)values.Add(Convert.ToString(item));return string.Join("\n",values);}

 public async Task<string> ExecuteExam(string action,string id,CancellationToken c){var body=new Dictionary<string,object>{{"id",id}};string route;
  switch(action){case "exam-notes":route="/notes";break;case "exam-auto":case "exam-manual":route="/start";body["confirmed"]=true;body["autoSubmit"]=action=="exam-auto";break;case "answer-stop":route="/stop";break;case "draft":route="/draft";break;case "submit":route="/submit";body["confirmed"]=true;break;default:throw new InvalidOperationException("未知考试操作");}
  var r=await client.RequestAsync("/local/exams"+route,body,c);if(action=="exam-notes")return Data.Text(Data.Map(r,"data"),"text");if(action!="draft")return Data.Text(r,"msg");var data=Data.Map(r,"data");var text=new System.Text.StringBuilder();text.AppendLine(Data.Text(data,"message"));int n=0;foreach(var q in Data.Rows(data,"questions")){text.AppendLine((++n)+". "+Data.Text(q,"title"));text.AppendLine(OptionText(q));text.AppendLine("作答："+Data.Text(q,"answer")+" · "+Data.Text(q,"answerSource"));text.AppendLine();}return text.ToString();
 }

 public async Task<string> ExecuteAsync(string action,string id,CancellationToken c){
   if(Id=="exams")return await ExecuteExam(action,id,c);
   var body=new Dictionary<string,object>{{"id",id}};string route;
   switch(action){
    case "view":route="/detail";break;case "draft":route="/draft";break;
    case "answer-auto":case "answer-manual":route="/answer";body["autoSubmit"]=action=="answer-auto";break;
    case "retake-auto":case "retake-manual":route="/retake";body["confirmed"]=true;body["autoSubmit"]=action=="retake-auto";break;
    case "answer-stop":route="/answer/stop";break;case "submit":route="/submit";body["confirmed"]=true;break;
    default:throw new InvalidOperationException("未知作业操作");
   }
   var r=await client.RequestAsync("/local/assessments"+route,body,c);
   if(action!="view"&&action!="draft")return Data.Text(r,"msg");
   var data=Data.Map(r,"data");
   var text=new System.Text.StringBuilder();int n=0;foreach(var q in Data.Rows(data,"questions")){text.AppendLine((++n)+". "+Data.Text(q,"title"));text.AppendLine(OptionText(q));if(Data.Flag(data,"alreadySubmitted"))text.AppendLine("平台公布答案："+(Data.Text(q,"platformAnswer").Length>0?Data.Text(q,"platformAnswer"):"未公布"));if(action=="draft")text.AppendLine("准备的答案："+Data.Text(q,"answer"));if(Data.Flag(q,"needsVision"))text.AppendLine("[图片题或文字需要视觉识别]");text.AppendLine();}
   return "共 "+n+" 题 · "+(action=="draft"?Data.Text(data,"message"):Data.Flag(data,"alreadySubmitted")?"已提交作业 · 平台公布答案":"题目预览，尚未作答")+"\n\n"+text.ToString();
  }
 }
 public class HomeworkProvider:Provider{public override string Id{get{return "homework";}}}
 public class ExamProvider:Provider{public override string Id{get{return "exams";}}}
}
