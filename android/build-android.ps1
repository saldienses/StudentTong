$ErrorActionPreference='Stop'
$stage=$PSScriptRoot;$javaHome='D:\tool\PyCharm 2025.1.2\jbr';$tools="$stage\tools\android-14";$androidJar="$stage\tools\android-34-ext12\android.jar"
$env:JAVA_HOME=$javaHome;$env:TEMP="$stage\temp";$env:TMP=$env:TEMP
New-Item -ItemType Directory -Force "$stage\build\classes","$stage\build\dex","$stage\temp","$stage\private","$stage\release"|Out-Null
function Check($label){if($LASTEXITCODE -ne 0){throw "$label failed"}}
$sources=Get-ChildItem "$stage\app\src" -Recurse -Filter '*.java'|ForEach-Object {$_.FullName}
& "D:\tool\qita\SuperAutoStudy\runtime\jdk8u504-b01\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -bootclasspath "$androidJar;$tools\core-lambda-stubs.jar" -d "$stage\build\classes" $sources
Check 'Java compilation'
Add-Type -AssemblyName System.IO.Compression.FileSystem
if(Test-Path "$stage\build\classes.jar"){Remove-Item -LiteralPath "$stage\build\classes.jar"}
[System.IO.Compression.ZipFile]::CreateFromDirectory("$stage\build\classes","$stage\build\classes.jar")
Check 'Class packaging'
& "$javaHome\bin\java.exe" "-Djava.io.tmpdir=$stage\temp" -cp "$tools\lib\d8.jar" com.android.tools.r8.D8 --release --min-api 28 --lib $androidJar --output "$stage\build\dex" "$stage\build\classes.jar"
Check 'DEX compilation'
& "$tools\aapt2.exe" compile --dir "$stage\app\res" -o "$stage\build\resources.zip"
Check 'Resource compilation'
& "$tools\aapt2.exe" link -I $androidJar --manifest "$stage\app\AndroidManifest.xml" -A "$stage\app\assets" --min-sdk-version 28 --target-sdk-version 34 -o "$stage\build\unsigned.apk" "$stage\build\resources.zip"
Check 'APK linking'
$zip=[System.IO.Compression.ZipFile]::Open("$stage\build\unsigned.apk",[System.IO.Compression.ZipArchiveMode]::Update)
try{[void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$stage\build\dex\classes.dex","classes.dex")}finally{$zip.Dispose()}
Check 'DEX packaging'
& "$tools\zipalign.exe" -f -p 4 "$stage\build\unsigned.apk" "$stage\build\aligned.apk"
Check 'APK alignment'
$signingHome=if($env:STUDENTTONG_SIGNING_HOME){$env:STUDENTTONG_SIGNING_HOME}else{"$stage\private"};New-Item -ItemType Directory -Force $signingHome|Out-Null
$passwordFile="$signingHome\signing-password.txt";$keystore="$signingHome\studenttong-release.jks"
if(!(Test-Path $passwordFile)){[byte[]]$random=New-Object byte[] 32;[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($random);[Convert]::ToBase64String($random)|Set-Content $passwordFile -Encoding ascii -NoNewline}
$env:STUDENTTONG_SIGN_PASS=(Get-Content $passwordFile -Raw).Trim()
try {
 if(!(Test-Path $keystore)){& "$javaHome\bin\keytool.exe" -genkeypair -keystore $keystore -alias studenttong -storepass:env STUDENTTONG_SIGN_PASS -keypass:env STUDENTTONG_SIGN_PASS -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=StudentTong Local Release,OU=StudentTong,O=StudentTong,C=CN';Check 'Signing key generation'}
 $apk="$stage\release\学生通-Android-0.2.0.apk"
 & "$javaHome\bin\java.exe" -jar "$tools\lib\apksigner.jar" sign --ks $keystore --ks-key-alias studenttong --ks-pass env:STUDENTTONG_SIGN_PASS --key-pass env:STUDENTTONG_SIGN_PASS --out $apk "$stage\build\aligned.apk"
 Check 'APK signing'
 & "$javaHome\bin\java.exe" -jar "$tools\lib\apksigner.jar" verify --verbose --print-certs $apk
 Check 'APK verification'
 & "$tools\zipalign.exe" -c -v 4 $apk *> "$stage\release\alignment-verification.txt"
 Check 'Alignment verification'
 Get-FileHash $apk -Algorithm SHA256|Select-Object Algorithm,Hash|ConvertTo-Json|Set-Content "$stage\release\apk-sha256.json" -Encoding UTF8
 'ANDROID_RELEASE_APK_BUILT_AND_VERIFIED'
}finally{Remove-Item Env:STUDENTTONG_SIGN_PASS -ErrorAction SilentlyContinue}



