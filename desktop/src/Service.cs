using System;
using System.Collections;
using System.Collections.Generic;
using System.Net.Http;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using StudyDesk.Contracts;
namespace StudyDesk {
 public class ServiceClient : IServiceClient {
  public string Platform="chaoxing",AccountId="";
  readonly HttpClient http=new HttpClient(new HttpClientHandler {UseProxy=false});
  public ServiceClient(){http.BaseAddress=new Uri("http://127.0.0.1:3030");http.Timeout=TimeSpan.FromSeconds(210);}
  public async Task<Dictionary<string,object>> RequestAsync(string path,Dictionary<string,object> body,CancellationToken cancel){
   if(!path.StartsWith("/")||path.StartsWith("//")||path.Contains(":")||path.Contains("\\"))throw new ArgumentException("仅允许本机服务路径");
   string platform=Platform,accountId=AccountId;if(platform=="yuketang"&&path=="/local/review/collect")path="/local/yuketang/review/collect";
   var json=new JavaScriptSerializer {MaxJsonLength=8*1024*1024};
   using(var request=new HttpRequestMessage(body==null?HttpMethod.Get:HttpMethod.Post,path)){
    request.Headers.Add("X-Study-Platform",platform);if(!string.IsNullOrEmpty(accountId))request.Headers.Add("X-Study-Account",accountId);
    if(body!=null)request.Content=new StringContent(json.Serialize(body),Encoding.UTF8,"application/json");
    using(var response=await http.SendAsync(request,cancel)){
     if(!response.IsSuccessStatusCode)throw new InvalidOperationException("本机服务请求失败，HTTP "+(int)response.StatusCode);
     var result=json.Deserialize<Dictionary<string,object>>(await response.Content.ReadAsStringAsync());
     if(result==null)throw new InvalidOperationException("服务返回内容为空");
     if(result.ContainsKey("code")&&Convert.ToInt32(result["code"])!=200)throw new InvalidOperationException(Data.Text(result,"msg"));
     return result;
    }
   }
  }
 }
 public static class Data {
  public static string Text(IDictionary<string,object> d,string key){object v;return d!=null&&d.TryGetValue(key,out v)&&v!=null?Convert.ToString(v):"";}
  public static Dictionary<string,object> Map(IDictionary<string,object> d,string key){object v;return d!=null&&d.TryGetValue(key,out v)?v as Dictionary<string,object>:null;}
  public static bool Flag(IDictionary<string,object> d,string key){return Text(d,key).Equals("True",StringComparison.OrdinalIgnoreCase);}
  public static double Number(IDictionary<string,object> d,string key){double n;return double.TryParse(Text(d,key),out n)?n:0;}
  public static IEnumerable<Dictionary<string,object>> Rows(IDictionary<string,object> d,string key){object v;if(d!=null&&d.TryGetValue(key,out v)&&v is IEnumerable)foreach(object row in (IEnumerable)v){var m=row as Dictionary<string,object>;if(m!=null)yield return m;}}
 }
 public class ChaptersProvider : IStudyModuleProvider {
  IServiceClient client;public string Id {get{return "chapters";}}
  public void Initialize(IServiceClient service){client=service;}
  public async Task<ModuleSnapshot> LoadAsync(CancellationToken c){
   var status=await client.RequestAsync("/local/status",null,c);var tasks=new List<StudyTask>();
   foreach(var row in Data.Rows(status,"tasks")){
    var state=Data.Text(row,"display_status");var execution=Data.Text(row,"execution_state");var actions=new List<string>();
    if(execution=="running"||execution=="queued")actions.Add("stop");
    if(execution=="idle"&&(state=="stopped"||state=="failed"))actions.Add("resume");
    if(execution=="idle"&&(state=="stopped"||state=="failed"||state=="completed")){actions.Add("rescan");actions.Add("delete");}
    object raw;double? progress=row.TryGetValue("current_progress",out raw)&&raw!=null?(double?)Data.Number(row,"current_progress"):null;
    tasks.Add(new StudyTask {Id=Data.Text(row,"task_id"),Title=Data.Text(row,"course_name"),State=Data.Text(row,"status_label"),Progress=progress,Detail=Data.Text(row,"error_message"),Live=Data.Map(row,"live_progress"),Actions=actions.ToArray()});
   }
   return new ModuleSnapshot{Available=true,Tasks=tasks,Message="服务已连接 · 每 3 秒刷新 · "+tasks.Count+" 项任务"};
  }
  public async Task<string> ExecuteAsync(string action,string id,CancellationToken c){
   if(Array.IndexOf(new[]{"stop","resume","rescan","delete"},action)<0)throw new ArgumentException("未知任务操作");
   var r=await client.RequestAsync("/local/tasks/"+Uri.EscapeDataString(id)+"/"+action,new Dictionary<string,object>(),c);return Data.Text(r,"msg");
  }
 }
}
