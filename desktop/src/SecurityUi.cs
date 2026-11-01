using System;using System.Collections.Generic;using System.IO;using System.Threading.Tasks;using System.Web.Script.Serialization;using System.Windows;using System.Windows.Controls;
namespace StudyDesk {
 public sealed class SecurityReminderGate {
  public bool Enabled=true;readonly HashSet<string> seen=new HashSet<string>();
  public bool Accept(string id){return Enabled&&!string.IsNullOrEmpty(id)&&seen.Add(id);}
 }
 public partial class Desktop {
  bool securityPopupEnabled=true,securityAlertBusy;readonly SecurityReminderGate securityGate=new SecurityReminderGate();
  string SecurityPreferencePath{get{return Path.Combine(Root,"config","client-preferences.json");}}
  void LoadSecurityPreference(){try{if(File.Exists(SecurityPreferencePath)){var value=new JavaScriptSerializer().Deserialize<Dictionary<string,object>>(File.ReadAllText(SecurityPreferencePath));if(value.ContainsKey("securityPopupEnabled"))securityPopupEnabled=Data.Flag(value,"securityPopupEnabled");}}catch{securityPopupEnabled=true;}securityGate.Enabled=securityPopupEnabled;}
  void SaveSecurityPreference(bool enabled){try{var value=new Dictionary<string,object>();if(File.Exists(SecurityPreferencePath))value=new JavaScriptSerializer().Deserialize<Dictionary<string,object>>(File.ReadAllText(SecurityPreferencePath));value["securityPopupEnabled"]=enabled;File.WriteAllText(SecurityPreferencePath,new JavaScriptSerializer().Serialize(value));securityPopupEnabled=enabled;securityGate.Enabled=enabled;Message(enabled?"已开启平台安全验证弹窗提醒。":"已关闭弹窗提醒；等待验证状态仍会显示，验证仍需人工完成。");}catch(Exception){Message("安全提醒偏好保存失败，请检查本机配置目录权限。");}}
  async Task CheckSecurityAlerts(){if(securityAlertBusy||!securityPopupEnabled||verificationDir!=null)return;securityAlertBusy=true;try{var r=await client.RequestAsync("/local/security/alerts",null,lifetime.Token);foreach(var alert in Data.Rows(r,"data")){if(!securityGate.Accept(Data.Text(alert,"eventId")))continue;var dialog=new Window{Owner=window,Title="平台安全验证提醒",Width=560,Height=280,ResizeMode=ResizeMode.NoResize,WindowStartupLocation=WindowStartupLocation.CenterOwner,Background=Color("#F3F5FA"),Resources=window.Resources,Topmost=true};var panel=new StackPanel{Margin=new Thickness(24)};panel.Children.Add(Text(Data.Text(alert,"title"),18,"#293B58"));panel.Children.Add(Text(Data.Text(alert,"message"),14,"#AA6250"));panel.Children.Add(Text("关闭提醒不等于通过验证。平台考试可能仍在计时，请及时处理。",13,"#78869B"));panel.Children.Add(Button("知道了",()=>{dialog.DialogResult=true;},false));dialog.Content=panel;dialog.ShowDialog();}}catch{}finally{securityAlertBusy=false;}}
 }
}
