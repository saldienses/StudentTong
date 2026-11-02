using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Markup;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using StudyDesk.Contracts;

namespace StudyDesk {
 public partial class Desktop {
  public static string Root=Path.GetFullPath(Environment.GetEnvironmentVariable("STUDYDESK_ROOT")??Path.Combine(AppDomain.CurrentDomain.BaseDirectory,".."));
 static string InstanceMutex(){using(var hash=System.Security.Cryptography.SHA256.Create())return "Local\\StudentTongDesktop-"+BitConverter.ToString(hash.ComputeHash(System.Text.Encoding.UTF8.GetBytes(Root.ToUpperInvariant()))).Replace("-","").Substring(0,20);}
  Window window;StackPanel content,taskPanel;TextBlock notice,indicator;
  readonly StudyDesk.Contracts.IServiceClient client;
  public Desktop():this(new ServiceClient()){}
  public Desktop(StudyDesk.Contracts.IServiceClient service){client=service;}readonly CancellationTokenSource lifetime=new CancellationTokenSource();
  readonly Dictionary<string,IStudyModuleProvider> providers=new Dictionary<string,IStudyModuleProvider>();
  readonly Dictionary<string,string> moduleErrors=new Dictionary<string,string>();
  string page="chapters";bool refreshing,actionBusy,ready;int lastTaskCount;ModuleSnapshot snapshot;
  DispatcherTimer timer;TextBox account;PasswordBox password,key;ComboBox courses,models;CheckBox aiEnabled,vision,learningTongEnabled;
  List<Dictionary<string,object>> courseRows=new List<Dictionary<string,object>>();string queriedAccount,queriedPassword;
  bool hasActiveTasks;int networkWrites;string verificationDir;
  [DllImport("user32.dll")]static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")]static extern bool ShowWindow(IntPtr h,int command);
  [STAThread]public static void Main(string[] args){
   bool verify=args.Length==2&&args[0]=="--verify";bool created;
   using(var mutex=new Mutex(true,InstanceMutex(),out created)){
    if(!verify&&!created){foreach(var process in Process.GetProcessesByName("StudyDesk"))if(process.Id!=Process.GetCurrentProcess().Id&&process.MainWindowHandle!=IntPtr.Zero&&process.MainModule.FileName.Equals(Assembly.GetExecutingAssembly().Location,StringComparison.OrdinalIgnoreCase)){ShowWindow(process.MainWindowHandle,9);SetForegroundWindow(process.MainWindowHandle);break;}return;}
    var app=new Application();app.DispatcherUnhandledException+=(s,e)=>{MessageBox.Show("操作未完成，请重试。运行日志位于安装目录 logs 文件夹。","学生通");e.Handled=true;};
    try{new Desktop().Run(app,verify?args[1]:null);}catch(Exception){MessageBox.Show("学生通启动失败，请检查 client 目录是否完整。","学生通");Environment.ExitCode=1;}
   }
  }
  void Run(Application application,string verify){
   verificationDir=verify;
   using(var stream=Assembly.GetExecutingAssembly().GetManifestResourceStream("Shell.xaml"))window=(Window)XamlReader.Load(stream);
   using(var brand=Assembly.GetExecutingAssembly().GetManifestResourceStream("Brand.png")){var image=new BitmapImage();image.BeginInit();image.CacheOption=BitmapCacheOption.OnLoad;image.StreamSource=brand;image.EndInit();image.Freeze();Find<Image>("BrandImage").Source=image;window.Icon=image;}
   content=Find<StackPanel>("PageContent");notice=Find<TextBlock>("Notice");indicator=Find<TextBlock>("ServiceIndicator");
   providers.Add("chapters",new ChaptersProvider());LoadExtensions();foreach(var provider in providers.Values)provider.Initialize(client);
   Find<Button>("NavChapters").Click+=(s,e)=>Navigate("chapters");Find<Button>("NavHomework").Click+=(s,e)=>Navigate("homework");Find<Button>("NavExams").Click+=(s,e)=>Navigate("exams");Find<Button>("NavSignin").Click+=(s,e)=>Navigate("signin");Find<Button>("NavReview").Click+=(s,e)=>Navigate("review");Find<Button>("NavSettings").Click+=(s,e)=>Navigate("settings");
   Find<Button>("RefreshButton").Click+=async(s,e)=>{if(!ready)await Connect();else if(page=="settings")await LoadAi();else if(platform=="yuketang"&&page!="review")await LoadRainTasks();else if(page=="signin")await LoadSignin();else await Refresh();};
   SetupWorkspace();LoadSecurityPreference();
   window.Closed+=(s,e)=>{lifetime.Cancel();if(timer!=null)timer.Stop();queriedPassword=null;};
   window.Loaded+=async(s,e)=>{Navigate("chapters");await Connect();if(verificationDir!=null)await Verify();};
   timer=new DispatcherTimer {Interval=TimeSpan.FromSeconds(3)};timer.Tick+=async(s,e)=>{if(ready)await CheckSecurityAlerts();if(ready&&platform=="yuketang"&&page!="review"&&page!="settings")await LoadRainTasks();else if(ready&&page=="signin")await LoadSignin();else if(ready&&page=="review")await LoadReview();else if(ready&&page!="settings")await Refresh();};timer.Start();
   application.Run(window);
  }
  T Find<T>(string name) where T:class{return window.FindName(name) as T;}
  static SolidColorBrush Color(string color){return new SolidColorBrush((System.Windows.Media.Color)ColorConverter.ConvertFromString(color));}
  static TextBlock Text(string text,double size,string color){return new TextBlock{Text=text,FontSize=size,Foreground=Color(color),TextWrapping=TextWrapping.Wrap,Margin=new Thickness(0,0,0,8)};}
  static StackPanel Column(){return new StackPanel();}
  static Border Card(UIElement child){return new Border{Background=Brushes.White,CornerRadius=new CornerRadius(11),Padding=new Thickness(23),Margin=new Thickness(0,0,0,16),Child=child};}
  Button Button(string text,Action action,bool secondary){var b=new Button{Content=text,Margin=new Thickness(0,6,10,0)};if(secondary){b.Background=Color("#EDF2FB");b.Foreground=Color("#47658D");}b.Click+=(s,e)=>action();return b;}
  void Message(string text){notice.Text=text;}
  void LoadExtensions(){
   string file=Path.Combine(Root,"client","modules.json");if(!File.Exists(file))return;
   try{var data=new JavaScriptSerializer().Deserialize<Dictionary<string,object>>(File.ReadAllText(file));foreach(var entry in Data.Rows(data,"modules")){
    string id=Data.Text(entry,"id");if((id!="homework"&&id!="exams")||!Data.Flag(entry,"enabled"))continue;
    try{string folder=Path.GetFullPath(Path.Combine(Root,"client","plugins"))+Path.DirectorySeparatorChar;string dll=Path.GetFullPath(Path.Combine(folder,Data.Text(entry,"assembly")));
     if(!dll.StartsWith(folder,StringComparison.OrdinalIgnoreCase)||!File.Exists(dll))throw new InvalidOperationException();
     var type=Assembly.LoadFrom(dll).GetType(Data.Text(entry,"type"),true);var provider=Activator.CreateInstance(type) as IStudyModuleProvider;
     if(provider==null||provider.Id!=id)throw new InvalidOperationException();providers.Add(id,provider);
    }catch(Exception){moduleErrors[id]="扩展模块加载失败，请检查 modules.json 和插件版本。";}
   }}catch(Exception){moduleErrors["homework"]=moduleErrors["exams"]="扩展配置无法读取，请检查 modules.json 格式。";}
  }
  async Task Connect(){
   ready=false;Message("正在连接本机服务…");
   try{
    bool needStart=false;
    try{await client.RequestAsync("/local/status",null,lifetime.Token);}catch(Exception){needStart=true;}
    if(needStart){
     if(verificationDir!=null)throw new InvalidOperationException("只读验证需要服务已经启动");
     Message("正在打开本机环境配置窗口，首次启动需要联网下载环境…");
     var info=new ProcessStartInfo(Path.Combine(Root,"学生通.exe"),"--auto --configure-only"){UseShellExecute=true,WorkingDirectory=Root};
     using(var process=Process.Start(info)){await Task.Run(()=>{if(!process.WaitForExit(1200000))throw new InvalidOperationException("配置尚未完成，请查看环境配置窗口与logs文件夹");if(process.ExitCode!=0)throw new InvalidOperationException("后台启动失败，请查看环境配置窗口或logs文件夹");});}
    }
    await client.RequestAsync("/local/status",null,lifetime.Token);ready=true;await LoadWorkspace();Navigate(page);indicator.Text="●  本机服务已连接";indicator.Foreground=Color("#8ED9B1");await Refresh();
   }catch(Exception){indicator.Text="●  服务未连接";Message("无法连接本机服务。点击右上角刷新重试，或在设置中打开运行日志。");}
  }
  void Navigate(string id){
   if(practiceChecking){Message("正在判分，请稍候再切换页面。");return;}practiceActive=false;
   page=id;content.Children.Clear();taskPanel=null;Find<ScrollViewer>("PageScroll").ScrollToTop();
   string title=id=="chapters"?"章节":id=="homework"?"作业":id=="exams"?"考试":id=="signin"?"签到":id=="review"?"复习":"设置";
   Find<TextBlock>("PageTitle").Text=title;
   Find<TextBlock>("PageSubtitle").Text=id=="chapters"?"课程、视频与章节小测，一处查看和管理。":id=="settings"?"AI、识别策略和本机服务设置。":"独立的"+title+"模块，与章节任务分开管理。";
   foreach(string item in new[]{"Chapters","Homework","Exams","Signin","Review","Settings"})Find<Button>("Nav"+item).Background=Color(item.ToLowerInvariant()==id?"#316DE6":"#27344B");
   if(platform=="yuketang"&&id!="settings"&&id!="review"){BuildRainPage(id);return;}
   if(id=="signin"){Find<TextBlock>("PageSubtitle").Text="普通签到自动处理，手机位置签到与常用位置管理。";BuildSignin();return;}
   if(id=="review"){Find<TextBlock>("PageSubtitle").Text="课程题目归纳、Word 导出与平台答案自测。";BuildReview();return;}
   if(id=="settings"){BuildSettings();if(ready)LoadAi().ContinueWith(t=>{},TaskScheduler.Default);return;}
   if(id=="chapters")BuildCourseForm();
   taskPanel=Column();content.Children.Add(taskPanel);
   if(!providers.ContainsKey(id)){var panel=Column();panel.Children.Add(Text(title+"模块 · 尚未接入",23,"#293B58"));panel.Children.Add(Text("现有后端没有独立"+title+"接口。这里不会把章节小测冒充为"+title+"，也不会自动提交内容。",14,"#78869B"));panel.Children.Add(Text("已预留模块接口：任务列表、实时进度、开始 / 停止及后续操作。接入相应服务后，可启用独立模块，无需改动主界面。",14,"#78869B"));
    if(moduleErrors.ContainsKey(id))panel.Children.Add(Text(moduleErrors[id],14,"#A65645"));
    panel.Children.Add(Button("查看扩展开发文档",()=>Open(Path.Combine(Root,"client","开发接口.md")),true));taskPanel.Children.Add(Card(panel));Message(title+"导航已建立 · 等待接入服务接口");
   }else if(ready){var unused=Refresh();}
  }
  void BuildCourseForm(){
   var panel=Column();panel.Children.Add(Text("添加课程任务",18,"#293B58"));
   var fields=new Grid();fields.ColumnDefinitions.Add(new ColumnDefinition());fields.ColumnDefinitions.Add(new ColumnDefinition());
   var left=Column();left.Margin=new Thickness(0,0,12,0);left.Children.Add(Text("学习通账号",12,"#76859C"));account=new TextBox();left.Children.Add(account);
   var right=Column();right.Children.Add(Text("学习通密码",12,"#76859C"));password=new PasswordBox();right.Children.Add(password);Grid.SetColumn(right,1);fields.Children.Add(left);fields.Children.Add(right);panel.Children.Add(fields);
   var row=new WrapPanel{Margin=new Thickness(0,10,0,0)};var query=Button("查询课程",async()=>await QueryCourses(),false);row.Children.Add(query);courses=new ComboBox{Width=350,MinHeight=39,Margin=new Thickness(0,6,10,0),DisplayMemberPath="Title"};row.Children.Add(courses);row.Children.Add(Button("添加任务",async()=>await SubmitCourse(),true));panel.Children.Add(row);
   panel.Children.Add(Text("账号用于本机服务登录；后台会保存登录信息。添加任务后可能同步学习进度并提交章节小测。",11,"#8793A6"));
   var expander=new Expander{Header="＋ 添加课程",Content=panel,Margin=new Thickness(0,0,0,18),IsExpanded=false};content.Children.Add(Card(expander));
   content.Children.Add(Text("本机任务",18,"#293B58"));
  }
  public class CourseItem {public string Title{get;set;}public Dictionary<string,object> Value{get;set;}}
  async Task QueryCourses(){if(!ready||actionBusy)return;
   if(string.IsNullOrWhiteSpace(account.Text)||password.Password.Length==0){Message("请填写账号和密码。");return;}
   actionBusy=true;Message("正在查询课程…");try{
    queriedAccount=account.Text.Trim();queriedPassword=password.Password;
    var result=await Write("/chaoxing/course",new Dictionary<string,object>{{"account",queriedAccount},{"password",queriedPassword}});
    await LoadWorkspace();if(taskPanel!=null)taskPanel.Children.Clear();courseRows=Data.Rows(result,"data").ToList();courses.Items.Clear();foreach(var c in courseRows)courses.Items.Add(new CourseItem{Title=Data.Text(c,"title"),Value=c});if(courses.Items.Count>0)courses.SelectedIndex=0;Message("已查询到 "+courseRows.Count+" 门课程。");
   }catch(Exception e){queriedPassword=null;Message(Friendly(e));}finally{actionBusy=false;}}
  async Task SubmitCourse(){if(!ready||actionBusy)return;var selected=courses.SelectedItem as CourseItem;
   if(selected==null||account.Text.Trim()!=queriedAccount||password.Password!=queriedPassword){Message("请先查询课程；更改账号或密码后需要重新查询。");return;}
   if(!Confirm("添加课程“"+selected.Title+"”？任务会立即执行，并可能提交章节小测。"))return;
   actionBusy=true;try{var c=selected.Value;var r=await Write("/chaoxing",new Dictionary<string,object>{{"loginAccount",queriedAccount},{"password",queriedPassword},{"courseId",Data.Text(c,"courseId")},{"courseName",selected.Title}});await Refresh();Message(Data.Text(r,"msg"));}catch(Exception e){Message(Friendly(e));}finally{actionBusy=false;}}
  async Task<Dictionary<string,object>> Write(string path,Dictionary<string,object> body){networkWrites++;return await client.RequestAsync(path,body,lifetime.Token);}
  async Task Refresh(){
   if(platform!="chaoxing"||refreshing||!ready||taskPanel==null||!providers.ContainsKey(page))return;refreshing=true;string requested=page;int requestedScope=scopeVersion;
   try{var loaded=await providers[requested].LoadAsync(lifetime.Token);if(page!=requested||requestedScope!=scopeVersion||platform!="chaoxing")return;snapshot=loaded;lastTaskCount=loaded.Tasks==null?0:loaded.Tasks.Count;
    if(page=="chapters")hasActiveTasks=loaded.Tasks!=null&&loaded.Tasks.Any(t=>t.State=="进行中"||t.State=="答题中"||t.State=="正在停止"||t.State=="排队中");
    indicator.Text="●  本机服务已连接";indicator.Foreground=Color("#8ED9B1");taskPanel.Children.Clear();Message(loaded.Message);if(!loaded.Available){taskPanel.Children.Add(Card(Text(loaded.Message,16,"#78869B")));return;}
    if(lastTaskCount==0)taskPanel.Children.Add(Card(Text(page=="chapters"?"暂无任务。添加课程后，将在这里显示进度。":page=="exams"?"当前考试安排入口未返回条目；这不代表所有课程都没有考试。课程考试入口仍需核对。":"当前已添加课程未返回作业条目。",16,"#78869B")));
    else foreach(var task in loaded.Tasks)taskPanel.Children.Add(TaskCard(task,requested));
   }catch(Exception e){Message(Friendly(e)+" · 已有进度暂时保留");indicator.Text="●  服务连接异常";}finally{refreshing=false;}
  }
  UIElement TaskCard(StudyTask task,string module){
   var panel=Column();var heading=new DockPanel();var state=Text(task.State,13,task.State=="已完成"?"#368664":"#5274A8");DockPanel.SetDock(state,Dock.Right);heading.Children.Add(state);heading.Children.Add(Text(task.Title,18,"#293B58"));panel.Children.Add(heading);
   var progress=task.Progress;if(module=="chapters"){panel.Children.Add(Text(progress.HasValue?"完成进度  "+progress.Value.ToString("0.##")+"%":"等待进度记录",13,"#7C8AA0"));panel.Children.Add(new ProgressBar{Minimum=0,Maximum=100,Value=Math.Max(0,Math.Min(100,progress??0))});}
   var live=task.Live;if(live!=null){bool active=Data.Flag(live,"active");panel.Children.Add(Text(active?Data.Text(live,"phase_label"):"最近执行记录 · "+task.State,14,"#5274A8"));string chapter=Data.Text(live,"chapter_title");if(chapter.Length>0)panel.Children.Add(Text((active?"当前章节":"最近章节")+" · "+chapter,12,"#78869B"));
    if(live.ContainsKey("video_duration_seconds")){double duration=Data.Number(live,"video_duration_seconds"),position=Data.Number(live,"video_estimated_seconds");panel.Children.Add(Text("视频 / 音频   "+Time(position)+" / "+Time(duration)+"   · 最近上报 "+Time(Data.Number(live,"video_reported_seconds")),14,"#5274A8"));panel.Children.Add(new ProgressBar{Maximum=100,Value=duration>0?Math.Min(100,position/duration*100):0});panel.Children.Add(Text("媒体时间为本机估算；平台完成情况以学习通为准。",11,"#8793A6"));}
    if(live.ContainsKey("question_total"))panel.Children.Add(Text((module=="chapters"?"小测第 ":module=="exams"?"考试答题 ":"作业答题 ")+Data.Text(live,"question_index")+" / "+Data.Text(live,"question_total")+" 题 · 已准备 "+Data.Text(live,"prepared_questions")+" 题 · "+Data.Text(live,"answer_source"),14,"#5274A8"));
    if(Data.Text(live,"recognition_source").Length>0)panel.Children.Add(Text("读取方式 · "+Data.Text(live,"recognition_source"),12,"#78869B"));
   }else if(module=="chapters")panel.Children.Add(Text("视频计时、小测题号和识别状态将在任务运行时显示。",12,"#8793A6"));
   if(!string.IsNullOrEmpty(task.Detail)&&(task.State=="执行异常"||module!="chapters"))panel.Children.Add(Text(task.Detail,12,"#AA6250"));
   var controls=new WrapPanel();foreach(var action in task.Actions??new string[0]){string selected=action;string label=ActionLabel(action);controls.Children.Add(Button(label,async()=>await Act(module,task,selected),true));}panel.Children.Add(controls);return Card(panel);
  }
  static string ActionLabel(string a){return a=="exam-notes"?"考试说明":a=="exam-answer"?"开始自动答题":a=="retake"?"平台答案重测":a=="answer"?"自动答题":a=="draft"?"查看答案草稿":a=="submit"?"确认提交":a=="answer-stop"?"停止答题":a=="view"?"查看题目":a=="stop"?"停止任务":a=="resume"?"继续任务":a=="rescan"?"重新检查":a=="delete"?"删除任务":a;}
  async Task Act(string module,StudyTask task,string action){
   if(actionBusy)return;
   if(action=="view"||action=="draft"||action=="exam-notes"){actionBusy=true;try{string preview=await providers[module].ExecuteAsync(action,task.Id,lifetime.Token);var dialog=new Window{Owner=window,Title=task.Title+" · 题目预览",Width=900,Height=700,WindowStartupLocation=WindowStartupLocation.CenterOwner};dialog.Content=new TextBox{Text=preview,IsReadOnly=true,TextWrapping=TextWrapping.Wrap,VerticalScrollBarVisibility=ScrollBarVisibility.Auto,Margin=new Thickness(18),FontSize=15};dialog.ShowDialog();}catch(Exception e){Message(Friendly(e));}finally{actionBusy=false;}return;}
   if(action=="exam-answer"){bool? auto=AskExam(task);if(!auto.HasValue)return;action=auto.Value?"exam-auto":"exam-manual";}
   if(action=="retake"){
    actionBusy=true;try{var response=await client.RequestAsync("/local/assessments/retake/preview",new Dictionary<string,object>{{"id",task.Id}},lifetime.Token);var plan=Data.Map(response,"data");if(!Data.Flag(plan,"canRetake")){Message("平台公布答案 "+Data.Text(plan,"available")+" / "+Data.Text(plan,"total")+" 题；答案不完整、题型不支持或无重做次数，未重做。");return;}
     bool? auto=AskRetake(task,plan);if(!auto.HasValue)return;action=auto.Value?"retake-auto":"retake-manual";
    }catch(Exception e){Message(Friendly(e));return;}finally{actionBusy=false;}
   }
   if(action=="answer"){bool? auto=AskAutoAnswer(task,false);if(!auto.HasValue)return;action=auto.Value?"answer-auto":"answer-manual";}
   string explanation=action=="submit"?"将尝试提交本机答案草稿。请先查看答案，确认后才会向平台发送请求；提交结果不明时不会重试。":action=="answer-stop"?(module=="exams"?"停止自动操作；平台考试计时仍可能继续，已暂存答案不会撤回。":"停止生成答案，保留当前后台内的部分草稿。"):action=="delete"?"仅删除本机任务行，保留日志，不删除平台课程。":action=="rescan"?"从第一章重新检查，跳过平台已完成的任务。":action=="resume"?"从上次章节继续执行，可能同步进度和提交章节小测。":"请求停止当前任务，已同步的平台进度保留。";
   if(action!="exam-auto"&&action!="exam-manual"&&action!="retake-auto"&&action!="retake-manual"&&action!="answer-auto"&&action!="answer-manual"&&!Confirm(ActionLabel(action)+"“"+task.Title+"”？\n\n"+explanation))return;
   actionBusy=true;try{networkWrites++;string message=await providers[module].ExecuteAsync(action,task.Id,lifetime.Token);await Refresh();Message(message);}catch(Exception e){Message(Friendly(e));}finally{actionBusy=false;}
  }
  bool? AskExam(StudyTask task){var dialog=new Window{Owner=window,Title="开始考试 · "+task.Title,Width=580,Height=340,ResizeMode=ResizeMode.NoResize,WindowStartupLocation=WindowStartupLocation.CenterOwner,Background=Color("#F3F5FA"),Resources=window.Resources};var panel=new StackPanel{Margin=new Thickness(24)};
   panel.Children.Add(Text("进入后平台立即计时，关闭软件或停止自动答题可能不会停止计时。题库 / AI 答案仅供参考，最终成绩以平台为准。",15,"#AA6250"));panel.Children.Add(Text("程序会在平台窗口逐题填写答案。遇到滑块等安全验证，请在该窗口完成。未勾选自动交卷时，完成后等待你在学生通确认交卷。",14,"#526680"));var auto=new CheckBox{Content="全部作答完成后自动核验并交卷",IsChecked=false,Margin=new Thickness(0,16,0,12)};panel.Children.Add(auto);var row=new WrapPanel();row.Children.Add(Button("确认进入并自动答题",()=>{dialog.DialogResult=true;},false));row.Children.Add(Button("取消",()=>{dialog.DialogResult=false;},true));panel.Children.Add(row);dialog.Content=panel;return dialog.ShowDialog()==true?(bool?)(auto.IsChecked==true):null;}
  bool? AskRetake(StudyTask task,Dictionary<string,object> plan){
   var dialog=new Window{Owner=window,Title="平台答案重测 · "+task.Title,Width=600,Height=340,ResizeMode=ResizeMode.NoResize,WindowStartupLocation=WindowStartupLocation.CenterOwner,Background=Color("#F3F5FA"),Resources=window.Resources};var panel=new StackPanel{Margin=new Thickness(24)};
   panel.Children.Add(Text("平台公布答案 "+Data.Text(plan,"available")+" / "+Data.Text(plan,"total")+" 题 · 当前成绩 "+Data.Text(plan,"score")+" · 剩余重做次数 "+(plan.ContainsKey("remaining")?Data.Text(plan,"remaining"):"以平台为准"),15,"#293B58"));
   panel.Children.Add(Text("确认后将使用平台允许的重做入口，覆盖上次作答记录和成绩，并消耗一次重做机会。仅使用已公布的平台答案，逐题匹配题目与选项；最终成绩以平台为准。",14,"#AA6250"));
   var auto=new CheckBox{Content="匹配完成后自动提交并核验平台状态",IsChecked=true,Margin=new Thickness(0,16,0,14)};panel.Children.Add(auto);var buttons=new WrapPanel();buttons.Children.Add(Button("确认重测（覆盖原成绩）",()=>{dialog.DialogResult=true;},false));buttons.Children.Add(Button("取消",()=>{dialog.DialogResult=false;},true));panel.Children.Add(buttons);dialog.Content=panel;return dialog.ShowDialog()==true?(bool?)(auto.IsChecked==true):null;
  }
  bool verifiedAutoSubmitDefaultOff;
  bool? AskAutoAnswer(StudyTask task,bool verify){
    var dialog=new Window{Owner=window,Title="自动答题 · "+task.Title,Width=520,Height=260,ResizeMode=ResizeMode.NoResize,WindowStartupLocation=WindowStartupLocation.CenterOwner};var panel=new StackPanel{Margin=new Thickness(24),Background=Color("#F3F5FA")};dialog.Resources=window.Resources;dialog.Background=Color("#F3F5FA");
    panel.Children.Add(Text("先查题库，再用学习通 AI / DeepSeek 后备，可能产生 API 费用。未勾选自动提交时，只生成本机草稿。关闭后台会丢失未提交草稿。",14,"#526680"));
    var auto=new CheckBox{Content="答案准备完成后自动提交并核验平台状态（默认关闭）",Margin=new Thickness(0,12,0,15),IsChecked=false};panel.Children.Add(auto);panel.Children.Add(Button("开始自动答题",()=>{dialog.DialogResult=true;},false));dialog.Content=panel;
    if(verify){dialog.Loaded+=async(sender,args)=>{verifiedAutoSubmitDefaultOff=auto.IsChecked==false;await Task.Delay(150);dialog.UpdateLayout();var bmp=new RenderTargetBitmap((int)panel.ActualWidth,(int)panel.ActualHeight,96,96,PixelFormats.Pbgra32);var drawing=new DrawingVisual();using(var dc=drawing.RenderOpen()){dc.DrawRectangle(dialog.Background,null,new Rect(0,0,panel.ActualWidth,panel.ActualHeight));dc.DrawRectangle(new VisualBrush(panel),null,new Rect(0,0,panel.ActualWidth,panel.ActualHeight));}bmp.Render(drawing);var encoder=new PngBitmapEncoder();encoder.Frames.Add(BitmapFrame.Create(bmp));using(var f=File.Create(Path.Combine(verificationDir,"auto-submit-default.png")))encoder.Save(f);dialog.DialogResult=false;};}
    return dialog.ShowDialog()==true?(bool?)(auto.IsChecked==true):null;
  }
  void BuildSettings(){
   var ai=Column();ai.Children.Add(Text("AI 与题目识别",20,"#293B58"));ai.Children.Add(Text("先读取网页，再处理题库；图片题或文字无法解码时使用截图备用识别。",13,"#78869B"));
   ai.Children.Add(Text("DeepSeek 后备模型",12,"#78869B"));models=new ComboBox{ItemsSource=new[]{"deepseek-flash","deepseek-v4-pro"},SelectedIndex=0,MinHeight=36,Margin=new Thickness(0,0,0,12)};ai.Children.Add(models);
   ai.Children.Add(Text("DeepSeek API Key · 留空保留现有密钥",12,"#78869B"));key=new PasswordBox();ai.Children.Add(key);
   aiEnabled=new CheckBox{Content="启用 DeepSeek 后备答题",Margin=new Thickness(0,15,0,8)};vision=new CheckBox{Content="网页读取失败或图片题时，启用截图备用识别",Margin=new Thickness(0,3,0,12)};ai.Children.Add(aiEnabled);ai.Children.Add(vision);learningTongEnabled=new CheckBox{Content="启用学习通AI讲解作为答题后备来源",Margin=new Thickness(0,6,0,12)};ai.Children.Add(learningTongEnabled);ai.Children.Add(Text("答题先查题库，再用学习通AI；不可用时回退到已启用的DeepSeek。学习通AI也属于参考答案。",12,"#8793A6"));
   ai.Children.Add(Text("截图只发送题目区域至 DeepSeek Flash，产生额外 API 费用。题号、选项无法校验时不提交。密钥不回显。",12,"#8793A6"));
   var buttons=new WrapPanel();buttons.Children.Add(Button("保存配置",async()=>await SaveAi(false),false));buttons.Children.Add(Button("测试DeepSeek",async()=>await SaveAi(true),true));buttons.Children.Add(Button("测试学习通AI",async()=>await TestLearningTongAi(),true));ai.Children.Add(buttons);content.Children.Add(Card(ai));
   var safety=Column();safety.Children.Add(Text("平台安全提示",20,"#293B58"));var popup=new CheckBox{Content="需要人工处理平台安全验证时弹窗提醒",IsChecked=securityPopupEnabled,Margin=new Thickness(0,10,0,12)};popup.Checked+=(s,e)=>SaveSecurityPreference(true);popup.Unchecked+=(s,e)=>SaveSecurityPreference(false);safety.Children.Add(popup);safety.Children.Add(Text("同一次验证只提醒一次。关闭弹窗后，等待验证状态仍会保留，不会跳过平台验证。",13,"#78869B"));content.Children.Add(Card(safety));
   var service=Column();service.Children.Add(Text("本机服务",20,"#293B58"));service.Children.Add(Text("关闭客户端会保留后台任务。需要停止某门课程，请在章节页点击“停止任务”。",13,"#78869B"));var row=new WrapPanel();row.Children.Add(Button("打开运行日志",()=>Open(Path.Combine(Root,"logs")),true));row.Children.Add(Button("打开桌面指南",()=>Open(Path.Combine(Root,"client","桌面使用指南.md")),true));row.Children.Add(Button("停止全部后台服务",async()=>await StopService(),true));service.Children.Add(row);content.Children.Add(Card(service));
   var ext=Column();ext.Children.Add(Text("模块扩展",20,"#293B58"));ext.Children.Add(Text("章节 / 作业 / 考试分别使用独立模块。提供 IStudyModuleProvider 接口、共享服务客户端及显式插件配置。",13,"#78869B"));ext.Children.Add(Button("打开开发文档",()=>Open(Path.Combine(Root,"client","开发接口.md")),true));content.Children.Add(Card(ext));
  }
  async Task LoadAi(){if(page!="settings")return;try{var r=await client.RequestAsync("/local/ai/config",null,lifetime.Token);if(page!="settings")return;var data=Data.Map(r,"data");models.SelectedItem=Data.Text(data,"model");aiEnabled.IsChecked=Data.Flag(data,"enabled");vision.IsChecked=Data.Flag(data,"visionFallback");learningTongEnabled.IsChecked=Data.Flag(data,"learningTongEnabled");key.Clear();Message(Data.Flag(data,"configured")?"已保存密钥 · 配置已加载，密钥不会回显。":"尚未配置 API Key。");}catch(Exception e){Message(Friendly(e));}}
  async Task SaveAi(bool test){if(!ready||actionBusy)return;actionBusy=true;try{var body=new Dictionary<string,object>{{"apiKey",key.Password.Trim()},{"model",Convert.ToString(models.SelectedItem)},{"enabled",aiEnabled.IsChecked==true},{"visionFallback",vision.IsChecked==true},{"learningTongEnabled",learningTongEnabled.IsChecked==true}};Message(test?"正在验证官方接口…":"正在保存配置…");var r=await Write(test?"/local/ai/test":"/local/ai/config",body);if(!test)key.Clear();Message(Data.Text(r,"msg"));}catch(Exception e){Message(Friendly(e));}finally{actionBusy=false;}}
  async Task TestLearningTongAi(){if(!ready||actionBusy)return;actionBusy=true;try{Message("正在使用学习通AI回答一道连通测试题，请稍候…");var r=await Write("/local/ai/learningtong-test",new Dictionary<string,object>());Message(Data.Text(r,"msg"));}catch(Exception e){Message(Friendly(e));}finally{actionBusy=false;}}
  async Task StopService(){if(actionBusy||!Confirm("停止全部后台服务？所有正在运行的课程会先收到停止请求；客户端将保持打开。"))return;actionBusy=true;try{var s=await providers["chapters"].LoadAsync(lifetime.Token);foreach(var task in s.Tasks)if((task.Actions??new string[0]).Contains("stop")){networkWrites++;await providers["chapters"].ExecuteAsync("stop",task.Id,lifetime.Token);}var info=new ProcessStartInfo(Path.Combine(Root,"学生通.exe"),"--stop"){UseShellExecute=true,WorkingDirectory=Root};using(var p=Process.Start(info)){await Task.Run(()=>{if(!p.WaitForExit(60000)||p.ExitCode!=0)throw new InvalidOperationException("停止服务未完成，请查看原生配置窗口或日志。");});}ready=false;indicator.Text="●  服务已停止";Message("服务已停止。点击刷新可以重新启动服务。");}catch(Exception e){Message(Friendly(e));}finally{actionBusy=false;}}
  bool Confirm(string message){return MessageBox.Show(window,message,"学生通",MessageBoxButton.YesNo,MessageBoxImage.Question)==MessageBoxResult.Yes;}
  static string Time(double seconds){int s=(int)Math.Max(0,seconds);return (s/60).ToString("00")+":"+(s%60).ToString("00");}
  static string Friendly(Exception e){return e is InvalidOperationException?e.Message:"操作未完成，请检查本机服务或网络后重试。";}
  static void Open(string path){if(File.Exists(path)||Directory.Exists(path))Process.Start(new ProcessStartInfo(path){UseShellExecute=true});}
  void Capture(string name){window.UpdateLayout();var visual=(FrameworkElement)window.Content;var bmp=new RenderTargetBitmap((int)visual.ActualWidth,(int)visual.ActualHeight,96,96,PixelFormats.Pbgra32);bmp.Render(visual);var encoder=new PngBitmapEncoder();encoder.Frames.Add(BitmapFrame.Create(bmp));using(var file=File.Create(Path.Combine(verificationDir,name+".png")))encoder.Save(file);}
  async Task Verify(){
   try{if(!ready)throw new InvalidOperationException("Service unavailable");Directory.CreateDirectory(verificationDir);await Refresh();await Task.Delay(500);int count=lastTaskCount;Capture("desktop-chapters");Navigate("homework");while(refreshing)await Task.Delay(100);await Refresh();await Task.Delay(150);Capture("desktop-homework");Navigate("exams");while(refreshing)await Task.Delay(100);await Refresh();await Task.Delay(150);Capture("desktop-exams");Navigate("signin");while(signinLoading)await Task.Delay(100);await LoadSignin();await LoadLocations();await Task.Delay(150);Capture("desktop-signin");string savedPlatform=platform,savedAccount=selectedAccount;platform="yuketang";selectedAccount=Data.Text(selectedByPlatform,"yuketang");scopeVersion++;ApplyScope();Navigate("chapters");while(rainLoading)await Task.Delay(100);await LoadRainTasks();Capture("desktop-yuketang");platform=savedPlatform;selectedAccount=savedAccount;scopeVersion++;ApplyScope();Navigate("settings");await LoadAi();await Task.Delay(300);Capture("desktop-settings");Navigate("review");await LoadReview();await Task.Delay(150);Capture("desktop-review");if(reviewUiFixture)await VerifyReviewPractice();Navigate("settings");await LoadAi();
    AskAutoAnswer(new StudyTask{Title="自动答题选项预览"},true);
    if((networkWrites!=0&&!reviewUiFixture)||key.Password.Length!=0||(!File.Exists(Path.Combine(Root,"distribution.json"))&&count==0)||!verifiedAutoSubmitDefaultOff||(File.Exists(Path.Combine(Root,"distribution.json"))&&(!providers.ContainsKey("homework")||!providers.ContainsKey("exams"))))throw new InvalidOperationException("Verification invariant failed");
    File.WriteAllText(Path.Combine(verificationDir,"verification.json"),new JavaScriptSerializer().Serialize(new{applicationName=window.Title,autoSubmitDefaultOff=verifiedAutoSubmitDefaultOff,nativeDesktop=true,serviceConnected=ready,taskCount=count,navigationPages=6,platformSwitchAvailable=true,accountSwitchAvailable=true,reviewPracticeFixturePassed=reviewFixturePassed,mutationRequests=networkWrites,apiKeyEmpty=key.Password.Length==0,homeworkAvailable=providers.ContainsKey("homework"),examsAvailable=providers.ContainsKey("exams")}));
   }catch(Exception e){Directory.CreateDirectory(verificationDir);File.WriteAllText(Path.Combine(verificationDir,"verification-error.txt"),e.GetType().Name+": "+e.Message);Environment.ExitCode=1;}finally{window.Close();}
  }
 }
}



