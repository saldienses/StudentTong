param([Parameter(Mandatory=$true)][string]$JavaHome,[Parameter(Mandatory=$true)][string]$MavenCommand,[Parameter(Mandatory=$true)][string]$InnoCompiler,[string]$MavenRepository='')
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent;$out=Join-Path $PSScriptRoot 'build';$payload=Join-Path $out 'payload';$env:JAVA_HOME=$JavaHome
New-Item -ItemType Directory -Force $out|Out-Null
if(Test-Path $payload){throw 'Use a clean windows/build directory; existing payload is never cleared automatically'}
$args=@('-B','-f',(Join-Path $repo 'backend\pom.xml'),'package');if($MavenRepository){$args+=('-Dmaven.repo.local='+$MavenRepository)}
& $MavenCommand @args;if($LASTEXITCODE -ne 0){throw 'Backend build failed'}
& (Join-Path $repo 'desktop\build.ps1');if($LASTEXITCODE -ne 0){throw 'Desktop build failed'}
foreach($dir in @('client','client\plugins','target','setup','config','licenses')){New-Item -ItemType Directory -Force (Join-Path $payload $dir)|Out-Null}
Copy-Item (Join-Path $repo 'desktop\bin\*') (Join-Path $payload 'client') -Force
Copy-Item (Join-Path $repo 'docs\*.md') (Join-Path $payload 'client')
Copy-Item (Join-Path $repo 'backend\target\SuperAuto-1.0-SNAPSHOT.jar') (Join-Path $payload 'target')
Copy-Item (Join-Path $PSScriptRoot 'setup\*') (Join-Path $payload 'setup')
Copy-Item (Join-Path $repo 'bridges\*.cjs'),(Join-Path $repo 'bridges\*.js') (Join-Path $payload 'config')
Copy-Item (Join-Path $repo 'licenses\*') (Join-Path $payload 'licenses') -Recurse
Copy-Item (Join-Path $repo 'LICENSE'),(Join-Path $repo 'UPSTREAM.md') $payload
Copy-Item (Join-Path $PSScriptRoot '安装说明.md'),(Join-Path $PSScriptRoot 'runtime-manifest.json') $payload
[IO.File]::WriteAllText((Join-Path $payload 'distribution.json'),'{"product":"StudentTong","version":"0.3.1","type":"native-environment-setup"}',(New-Object Text.UTF8Encoding($false)))
$f=Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319'
& "$f\csc.exe" /nologo /target:library /optimize+ "/reference:$payload\client\StudyDesk.Contracts.dll" "/reference:$payload\client\StudyDesk.exe" "/out:$payload\client\plugins\Assessments.Provider.dll" "$repo\desktop\sdk\examples\AssessmentProvider.cs"
if($LASTEXITCODE -ne 0){throw 'Module compilation failed'}
& "$f\csc.exe" /nologo /target:winexe /platform:x64 /optimize+ /reference:System.Windows.Forms.dll /reference:System.Drawing.dll /reference:System.IO.Compression.dll /reference:System.IO.Compression.FileSystem.dll /reference:System.Web.Extensions.dll /reference:System.Management.dll "/win32icon:$payload\client\StudyDesk.ico" "/out:$payload\学生通.exe" "$PSScriptRoot\src\EnvironmentApp.cs"
if($LASTEXITCODE -ne 0){throw 'Native bootstrap compilation failed'}
& $InnoCompiler ("/DPayload=$payload") ("/DOutput=$out") (Join-Path $PSScriptRoot 'src\StudentTong.iss')
if($LASTEXITCODE -ne 0){throw 'Installer compilation failed'}
Get-FileHash (Join-Path $out 'StudentTong-Windows-0.3.1-Setup.exe') -Algorithm SHA256
