param([Parameter(Mandatory=$true)][string]$JavaHome,[Parameter(Mandatory=$true)][string]$MavenCommand,[Parameter(Mandatory=$true)][string]$PlaywrightNodeModules,[string]$MavenRepository='',[string]$InnoCompiler='')
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent;$out=Join-Path $PSScriptRoot 'build';$payload=Join-Path $out 'payload';$env:JAVA_HOME=$JavaHome
# All build caches/temp should be placed on the intended data disk by caller.
New-Item -ItemType Directory -Force $out | Out-Null
if(Test-Path $payload){throw 'build/payload already exists; use a clean source checkout for a reproducible package'}
$args=@('-B','-f',(Join-Path $repo 'backend\pom.xml'),'package');if($MavenRepository){$args+=('-Dmaven.repo.local='+$MavenRepository)}
& $MavenCommand @args;if($LASTEXITCODE -ne 0){throw 'Backend build failed'}
& (Join-Path $repo 'desktop\build.ps1');if($LASTEXITCODE -ne 0){throw 'Desktop build failed'}
foreach($dir in @('client','client\plugins','target','setup','config','licenses','runtime\vision-node\node_modules')){New-Item -ItemType Directory -Force (Join-Path $payload $dir)|Out-Null}
Copy-Item (Join-Path $repo 'docs\*.md') (Join-Path $payload 'client')
Copy-Item (Join-Path $repo 'desktop\bin\*') (Join-Path $payload 'client') -Force
Copy-Item (Join-Path $repo 'backend\target\SuperAuto-1.0-SNAPSHOT.jar') (Join-Path $payload 'target')
Copy-Item (Join-Path $PSScriptRoot 'setup\*') (Join-Path $payload 'setup')
Copy-Item (Join-Path $repo 'bridges\*.cjs'),(Join-Path $repo 'bridges\*.js') (Join-Path $payload 'config')
Copy-Item (Join-Path $repo 'licenses\*') (Join-Path $payload 'licenses') -Recurse
Copy-Item (Join-Path $repo 'LICENSE'),(Join-Path $repo 'UPSTREAM.md') $payload
Copy-Item (Join-Path $PSScriptRoot '安装说明.md'),(Join-Path $PSScriptRoot 'runtime-manifest.json') $payload
Copy-Item (Join-Path $PSScriptRoot 'src\*.ps1') $payload
foreach($name in @('playwright','playwright-core')){$package=Join-Path $PlaywrightNodeModules $name;$version=(Get-Content (Join-Path $package 'package.json') -Raw|ConvertFrom-Json).version;if($version -ne '1.62.1'){throw 'Playwright version must be 1.62.1'};Copy-Item $package (Join-Path $payload 'runtime\vision-node\node_modules') -Recurse}
[IO.File]::WriteAllText((Join-Path $payload 'distribution.json'),'{"product":"StudentTong","version":"0.3.0","type":"network-bootstrap"}',(New-Object Text.UTF8Encoding($false)))
$f=Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319'
& "$f\csc.exe" /nologo /target:library /optimize+ "/reference:$payload\client\StudyDesk.Contracts.dll" "/reference:$payload\client\StudyDesk.exe" "/out:$payload\client\plugins\Assessments.Provider.dll" "$repo\desktop\sdk\examples\AssessmentProvider.cs"
if($LASTEXITCODE -ne 0){throw 'Module build failed'}
Get-ChildItem -LiteralPath (Join-Path $payload 'runtime\vision-node\node_modules\playwright-core\bin') -File | Where-Object {$_.Name -like 'reinstall_*' -or $_.Extension -in @('.sh','.ps1')} | Remove-Item -Force
Copy-Item (Join-Path $PSScriptRoot 'src\*.cmd') $payload
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=Join-Path $out 'StudentTong-Windows-0.3.0-AutoSetup.zip';[IO.Compression.ZipFile]::CreateFromDirectory($payload,$zip,[IO.Compression.CompressionLevel]::Optimal,$false)
if($InnoCompiler){& $InnoCompiler ("/DPayload=$payload") ("/DOutput=$out") (Join-Path $PSScriptRoot 'src\StudentTong.iss')
if($LASTEXITCODE -ne 0){throw 'Inno Setup compile failed'}
Get-FileHash -LiteralPath (Join-Path $out 'StudentTong-Windows-0.3.0-Setup.exe') -Algorithm SHA256}
Get-FileHash -LiteralPath $zip -Algorithm SHA256
