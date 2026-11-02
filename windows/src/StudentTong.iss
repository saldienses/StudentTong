#ifndef Payload
 #define Payload "..\payload"
#endif
#ifndef Output
 #define Output "..\release"
#endif
[Setup]
AppId=StudentTong
AppName=学生通
AppVersion=0.3.1
AppPublisher=StudentTong Community
AppPublisherURL=https://github.com/saldienses/StudentTong
DefaultDirName=D:\tool\qita\StudentTong
DisableDirPage=no
UsePreviousAppDir=no
DefaultGroupName=学生通
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
OutputDir={#Output}
OutputBaseFilename=StudentTong-Windows-0.3.1-Setup
Compression=none
SolidCompression=no
SetupIconFile={#Payload}\client\StudyDesk.ico
UninstallDisplayIcon={app}\client\StudyDesk.ico
WizardStyle=modern
[Messages]
SetupWindowTitle=学生通 0.3.1 安装
WelcomeLabel1=欢迎使用学生通安装程序
WelcomeLabel2=安装程序将准备学生通文件，完成后可在原生配置窗口中下载并校验官方运行环境。首次需要联网。建议安装在D盘的新目录，旧版数据不会自动迁移或清空。
SelectDirLabel3=请选择学生通的安装目录。
SelectDirBrowseLabel=推荐 D:\tool\qita\StudentTong。下载、缓存和数据库均放在所选目录。
FinishedHeadingLabel=学生通安装完成
FinishedLabel=以后可通过桌面快捷方式启动。若环境配置失败，请检查网络并双击安装目录中的“学生通.exe”重试。
ButtonNext=下一步(&N) >
ButtonBack=< 上一步(&B)
ButtonCancel=取消
ButtonInstall=安装(&I)
ButtonFinish=完成(&F)
ButtonBrowse=浏览(&R)...
[Tasks]
Name: desktopicon; Description: 创建桌面“学生通”快捷方式
[Files]
Source: "{#Payload}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
[Icons]
Name: "{userdesktop}\{code:DesktopLinkName}"; Filename: "{app}\学生通.exe"; WorkingDir: "{app}"; IconFilename: "{app}\client\StudyDesk.ico"; Tasks: desktopicon
Name: "{group}\学生通"; Filename: "{app}\学生通.exe"; WorkingDir: "{app}"; IconFilename: "{app}\client\StudyDesk.ico"
[Run]
Filename: "{app}\学生通.exe"; Description: 打开学生通环境配置窗口; Flags: postinstall skipifsilent nowait unchecked
[Code]
function DesktopLinkName(Param: String): String;
begin
 if FileExists(ExpandConstant('{userdesktop}\学生通.lnk')) then Result := '学生通0.3.1'
 else Result := '学生通';
end;
function NextButtonClick(CurPageID: Integer): Boolean;
begin
 Result := True;
 if CurPageID = wpSelectDir then begin
  if Length(WizardDirValue) > 110 then begin
   MsgBox('安装路径过长，请选择较短目录。', mbError, MB_OK); Result := False;
  end else if DirExists(WizardDirValue) and not FileExists(WizardDirValue + '\distribution.json') then begin
   // Existing nonempty folders must never be overwritten.
   if DirExists(WizardDirValue + '\data') or FileExists(WizardDirValue + '\Start-SuperAutoStudy.ps1') then begin
    MsgBox('检测到其他版本或数据，请选择一个新的空目录。', mbError, MB_OK); Result := False;
   end;
  end;
 end;
end;
function PrepareToInstall(var NeedsRestart: Boolean): String;
var Found: TFindRec; HasFiles: Boolean; MarkerText: AnsiString;
begin
 Result := ''; HasFiles := False;
 if FindFirst(WizardDirValue + '\*', Found) then begin
  try
   repeat
    if (Found.Name <> '.') and (Found.Name <> '..') then HasFiles := True;
   until not FindNext(Found);
  finally FindClose(Found); end;
 end;
 if HasFiles then begin
  if not LoadStringFromFile(WizardDirValue + '\distribution.json', MarkerText) then
   Result := '目录不为空且不是本安装器创建的学生通目录，请另选新目录。'
  else if Pos('StudentTong', String(MarkerText)) = 0 then
   Result := '安装目录标识不匹配，请另选新目录。';
 end;
end;
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var Code: Integer;
begin
 if CurUninstallStep = usUninstall then
  Exec(ExpandConstant('{app}\学生通.exe'), '--stop', ExpandConstant('{app}'), SW_SHOW, ewWaitUntilTerminated, Code);
end;
