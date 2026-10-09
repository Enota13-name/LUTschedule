param(
    [string]$SdkPath = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$JdkPath = $env:JAVA_HOME,
    [string]$SigningKey = ''
)
$ErrorActionPreference='Stop'
$taskRoot=$PSScriptRoot
$taskBuild=Join-Path $taskRoot 'runtime-test-build'
$taskTools=Join-Path $SdkPath 'build-tools\36.0.0'
if (-not $JdkPath) { throw 'Pass -JdkPath or set JAVA_HOME.' }
$taskJdk=Join-Path $JdkPath 'bin'
$taskJar=Join-Path $SdkPath 'platforms\android-37.0\android.jar'
if (-not (Test-Path -LiteralPath $taskJar)) { $taskJar=Join-Path $SdkPath 'platforms\android-37\android.jar' }
$taskKey=$SigningKey
if (-not $taskKey) {
    $taskKey=Join-Path $taskRoot '..\..\signing\preview-signing.p12'
    if (-not (Test-Path -LiteralPath $taskKey)) { $taskKey=Join-Path $taskRoot '..\..\build\offline\preview-signing.p12' }
}
if (-not (Test-Path -LiteralPath $taskKey)) { throw 'Build the App first or pass its matching -SigningKey.' }
New-Item -ItemType Directory -Force -Path $taskBuild,(Join-Path $taskBuild 'classes'),(Join-Path $taskBuild 'dex') | Out-Null
function Run([string]$p,[string[]]$a){& $p @a;if($LASTEXITCODE -ne 0){throw "Test build failed: $p"}}
Run (Join-Path $taskJdk 'javac.exe') @('--release','8','-encoding','UTF-8','-classpath',$taskJar,'-d',(Join-Path $taskBuild 'classes'),(Join-Path $taskRoot 'RuntimeSmoke.java'),(Join-Path $taskRoot 'RuntimeV016.java'),(Join-Path $taskRoot 'RuntimeV017.java'),(Join-Path $taskRoot 'RuntimeV019.java'))
Run (Join-Path $taskJdk 'jar.exe') @('cf',(Join-Path $taskBuild 'classes.jar'),'-C',(Join-Path $taskBuild 'classes'),'.')
Run (Join-Path $taskJdk 'java.exe') @('-cp',(Join-Path $taskTools 'lib\d8.jar'),'com.android.tools.r8.D8','--min-api','26','--lib',$taskJar,'--output',(Join-Path $taskBuild 'dex'),(Join-Path $taskBuild 'classes.jar'))
Run (Join-Path $taskTools 'aapt2.exe') @('link','-o',(Join-Path $taskBuild 'unsigned.apk'),'--manifest',(Join-Path $taskRoot 'runtime-test-manifest.xml'),'-I',$taskJar)
Run (Join-Path $taskJdk 'jar.exe') @('uf',(Join-Path $taskBuild 'unsigned.apk'),'-C',(Join-Path $taskBuild 'dex'),'classes.dex')
Run (Join-Path $taskTools 'zipalign.exe') @('-f','4',(Join-Path $taskBuild 'unsigned.apk'),(Join-Path $taskBuild 'aligned.apk'))
Run (Join-Path $taskJdk 'java.exe') @('-jar',(Join-Path $taskTools 'lib\apksigner.jar'),'sign','--ks',$taskKey,'--ks-pass','pass:android','--key-pass','pass:android','--out',(Join-Path $taskBuild 'runtime-test.apk'),(Join-Path $taskBuild 'aligned.apk'))
