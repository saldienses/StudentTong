using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using StudyDesk;
using StudyDesk.Contracts;
class TestClient : IServiceClient {
 public string LastPath;public Dictionary<string,object> Body;public int Writes;
 public Task<Dictionary<string,object>> RequestAsync(string path,Dictionary<string,object> body,CancellationToken cancel){LastPath=path;Body=body;if(body!=null){Writes++;return Task.FromResult(new Dictionary<string,object>{{"msg","OK"}});}
  return Task.FromResult(new Dictionary<string,object>{{"tasks",new object[]{
   new Dictionary<string,object>{{"task_id","1"},{"course_name","Course A"},{"display_status","running"},{"status_label","进行中"},{"execution_state","running"},{"current_progress",12.5}},
   new Dictionary<string,object>{{"task_id","2"},{"display_status","completed"},{"status_label","已完成"},{"execution_state","idle"},{"current_progress",100}},
   new Dictionary<string,object>{{"task_id","3"},{"display_status","stopped"},{"execution_state","idle"},{"current_progress",null}},
   new Dictionary<string,object>{{"task_id","4"},{"display_status","failed"},{"execution_state","idle"},{"current_progress",0}},
   new Dictionary<string,object>{{"task_id","5"},{"display_status","stopping"},{"execution_state","stopping"}}
  }}});
 }
}
class Tests {
 static int count;static void Check(bool value){count++;if(!value)throw new Exception("Assertion "+count);}
 static void Main(){Run().GetAwaiter().GetResult();}
 static async Task Run(){var fake=new TestClient();var p=new ChaptersProvider();p.Initialize(fake);var s=await p.LoadAsync(CancellationToken.None);
  Check(s.Available&&s.Tasks.Count==5);Check(s.Tasks[0].Actions.SequenceEqual(new[]{"stop"}));Check(s.Tasks[0].Progress==12.5);
  Check(s.Tasks[1].Actions.SequenceEqual(new[]{"rescan","delete"}));Check(!s.Tasks[1].Actions.Contains("resume"));
  Check(s.Tasks[2].Progress==null);Check(s.Tasks[2].Actions.Contains("resume"));Check(s.Tasks[3].Progress==0);Check(s.Tasks[4].Actions.Length==0);Check(fake.Writes==0);
  Check(await p.ExecuteAsync("stop","id /?",CancellationToken.None)=="OK");Check(fake.LastPath=="/local/tasks/id%20%2F%3F/stop"&&fake.Body.Count==0);
  bool rejected=false;try{await p.ExecuteAsync("arbitrary","1",CancellationToken.None);}catch(ArgumentException){rejected=true;}Check(rejected&&fake.Writes==1);
  var service=new ServiceClient();foreach(var path in new[]{"http://elsewhere/","//elsewhere/","/\\elsewhere"}){bool invalid=false;try{await service.RequestAsync(path,null,CancellationToken.None);}catch(ArgumentException){invalid=true;}Check(invalid);}
  var plugin=(IStudyModuleProvider)Activator.CreateInstance(System.Reflection.Assembly.LoadFrom("Example.Provider.dll").GetType("Example.HomeworkProvider"));plugin.Initialize(fake);var ext=await plugin.LoadAsync(CancellationToken.None);Check(!ext.Available&&ext.Tasks.Count==0&&plugin.Id=="homework");
  Console.WriteLine("DESKTOP_CONTRACT_CHECKS="+count+" PASS");
 }
}
