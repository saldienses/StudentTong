$ErrorActionPreference='Stop'
$framework='C:\Windows\Microsoft.NET\Framework64\v4.0.30319'
$wpf="$framework\WPF"
$src=Join-Path $PSScriptRoot 'src'
$bin=Join-Path $PSScriptRoot 'bin'
New-Item -ItemType Directory -Force $bin | Out-Null
Copy-Item "$src\Student.ico" "$bin\StudyDesk.ico" -Force
& "$framework\csc.exe" /nologo /target:library /optimize+ /out:"$bin\StudyDesk.Contracts.dll" "$src\Contracts.cs"
if($LASTEXITCODE -ne 0){throw 'Contract compilation failed'}
& "$framework\csc.exe" /nologo /target:winexe /platform:anycpu /optimize+ /win32icon:"$bin\StudyDesk.ico" /win32manifest:"$src\app.manifest" /out:"$bin\StudyDesk.exe" "/resource:$src\Shell.xaml,Shell.xaml" "/resource:$src\Brand.png,Brand.png" /reference:"$bin\StudyDesk.Contracts.dll" /reference:"$wpf\PresentationFramework.dll" /reference:"$wpf\PresentationCore.dll" /reference:"$wpf\WindowsBase.dll" /reference:"$framework\System.Xaml.dll" /reference:"$framework\System.Net.Http.dll" /reference:"$framework\System.Web.Extensions.dll" "$src\Service.cs" "$src\App.cs" "$src\ReviewUi.cs" "$src\SecurityUi.cs" "$src\SigninUi.cs" "$src\LocationMap.cs" "$src\PlatformUi.cs"
if($LASTEXITCODE -ne 0){throw 'Desktop compilation failed'}
Copy-Item "$src\StudyDesk.exe.config","$src\modules.json" $bin -Force
'BUILD_OK'
