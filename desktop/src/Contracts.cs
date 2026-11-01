using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
namespace StudyDesk.Contracts {
 public interface IServiceClient {
  Task<Dictionary<string,object>> RequestAsync(string path, Dictionary<string,object> body, CancellationToken cancel);
 }
 public class StudyTask {
  public string Id {get;set;} public string Title {get;set;} public string State {get;set;}
  public double? Progress {get;set;} public string Detail {get;set;}
  public Dictionary<string,object> Live {get;set;}
  public string[] Actions {get;set;}
 }
 public class ModuleSnapshot {
  public string Message {get;set;} public bool Available {get;set;}
  public List<StudyTask> Tasks {get;set;}
 }
 // Implement this interface in a managed DLL; register it explicitly in modules.json.
 public interface IStudyModuleProvider {
  string Id {get;}
  void Initialize(IServiceClient service);
  Task<ModuleSnapshot> LoadAsync(CancellationToken cancel);
  Task<string> ExecuteAsync(string action,string taskId,CancellationToken cancel);
 }
}
