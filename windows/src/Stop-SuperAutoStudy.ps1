$ErrorActionPreference='Stop'
$base=[IO.Path]::GetFullPath($PSScriptRoot);$utf8=New-Object Text.UTF8Encoding($false)
$statePath=Join-Path $base 'config\portable-state.json'
if(-not(Test-Path -LiteralPath $statePath)){exit 0}
$state=Get-Content -LiteralPath $statePath -Raw|ConvertFrom-Json
$pidFile=Join-Path $base 'logs\application.pid'
if(Test-Path -LiteralPath $pidFile){$processId=[int]([IO.File]::ReadAllText($pidFile).Trim());$p=Get-Process -Id $processId -ErrorAction SilentlyContinue;if($p -and $p.Path -eq (Join-Path $base 'runtime\java\bin\java.exe')){$record=Get-CimInstance Win32_Process -Filter "ProcessId=$processId";if($record.CommandLine -and $record.CommandLine.Contains($base.Replace('\','/'))){Stop-Process -Id $processId;$p.WaitForExit(15000)|Out-Null}}}
$db=Join-Path $base 'runtime\database\bin';$client=Join-Path $db 'mariadb.exe'
if(Test-Path -LiteralPath $client){
 $argsFile='--defaults-extra-file='+$base.Replace('\','/')+'/config/root-client.ini'
 $info=New-Object Diagnostics.ProcessStartInfo;$info.FileName=$client;$info.Arguments='"'+$argsFile+'" --connect-timeout=2 --default-character-set=utf8mb4 --raw --batch --skip-column-names "--execute=SELECT @@datadir"';$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true;$info.StandardOutputEncoding=$utf8;$info.StandardErrorEncoding=$utf8;$probe=New-Object Diagnostics.Process;$probe.StartInfo=$info;[void]$probe.Start();$out=$probe.StandardOutput.ReadToEndAsync();$err=$probe.StandardError.ReadToEndAsync();$probe.WaitForExit();$data=$out.Result
 if($probe.ExitCode -eq 0 -and ([IO.Path]::GetFullPath($data.Trim().Replace('/','\')).TrimEnd('\') -eq [IO.Path]::GetFullPath((Join-Path $base 'data\database')).TrimEnd('\'))){& (Join-Path $db 'mariadb-admin.exe') $argsFile 'shutdown' *> (Join-Path $base 'logs\db-shutdown.log');if($LASTEXITCODE -ne 0){throw '本目录数据库未确认停止'}}else{if(Test-Path (Join-Path $base 'logs\database.pid')){$dbPid=[int]([IO.File]::ReadAllText((Join-Path $base 'logs\database.pid')).Trim());$dbProcess=Get-Process -Id $dbPid -ErrorAction SilentlyContinue;if($dbProcess -and $dbProcess.Path -eq (Join-Path $db 'mariadbd.exe')){throw '本目录数据库仍在运行，身份核验失败，已保留进程；请查看日志'}}}

}
Write-Output 'STOPPED'
