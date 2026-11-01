using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using StudyDesk.Contracts;
// This is an SDK example, not an implementation of the Chaoxing homework API.
namespace Example {
 public class HomeworkProvider : IStudyModuleProvider {
  IServiceClient service;public string Id {get{return "homework";}}
  public void Initialize(IServiceClient value){service=value;}
  public Task<ModuleSnapshot> LoadAsync(CancellationToken cancel){
   cancel.ThrowIfCancellationRequested();
   return Task.FromResult(new ModuleSnapshot{Available=false,Message="示例扩展已加载。请实现独立作业接口后启用任务操作。",Tasks=new List<StudyTask>()});
  }
  public Task<string> ExecuteAsync(string action,string id,CancellationToken cancel){throw new NotSupportedException("此示例没有作业提交能力。");}
 }
}
