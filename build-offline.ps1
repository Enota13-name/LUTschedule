param(
    [string]$SdkPath = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$JdkPath = 'D:\Android\jbr',
    [string]$BuildDir = '',
    [string]$SigningKey = '',
    [string]$VersionName = '0.1.8-preview',
    [int]$VersionCode = 9
)
$ErrorActionPreference = 'Stop'
$projectPath = $PSScriptRoot
if (-not $BuildDir) { $BuildDir = Join-Path $projectPath 'build\offline' }
if (-not $SigningKey) {
    $deliveredKey = Join-Path $projectPath 'signing\preview-signing.p12'
    $SigningKey = if (Test-Path -LiteralPath $deliveredKey) { $deliveredKey } else { Join-Path $BuildDir 'preview-signing.p12' }
}
$toolPath = Join-Path $SdkPath 'build-tools\36.0.0'
$androidJar = Join-Path $SdkPath 'platforms\android-37.0\android.jar'
if (-not (Test-Path -LiteralPath $androidJar)) { $androidJar = Join-Path $SdkPath 'platforms\android-37\android.jar' }
foreach ($required in @($androidJar, (Join-Path $JdkPath 'bin\javac.exe'), (Join-Path $toolPath 'aapt2.exe'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Required build tool missing: $required" }
}
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null
$classesDir = Join-Path $BuildDir 'classes'
$dexDir = Join-Path $BuildDir 'dex'
$testsDir = Join-Path $BuildDir 'tests'
New-Item -ItemType Directory -Force -Path $classesDir,$dexDir,$testsDir | Out-Null
function Run-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Build command failed: $Program (exit $LASTEXITCODE)" }
}
$javac = Join-Path $JdkPath 'bin\javac.exe'
$java = Join-Path $JdkPath 'bin\java.exe'
$jar = Join-Path $JdkPath 'bin\jar.exe'
$core = Join-Path $projectPath 'app\src\main\java\cn\lut\schedule\ScheduleCore.java'
$rules = Join-Path $projectPath 'app\src\main\java\cn\lut\schedule\AppRules.java'
$test = Join-Path $projectPath 'tests\CoreTests.java'
Run-Checked $javac @('--release','8','-encoding','UTF-8','-d',$testsDir,$core,$rules,$test)
Run-Checked $java @('-cp',$testsDir,'cn.lut.schedule.CoreTests')
Run-Checked $javac @('--release','8','-encoding','UTF-8','-d',$testsDir,(Join-Path $projectPath 'app\src\main\java\cn\lut\schedule\ReportRedactor.java'),(Join-Path $projectPath 'tests\ReportRedactorTests.java'))
Run-Checked $java @('-cp',$testsDir,'cn.lut.schedule.ReportRedactorTests')
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectPath 'app\src\main\java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
Run-Checked $javac (@('--release','8','-encoding','UTF-8','-classpath',$androidJar,'-d',$classesDir) + $sources)
$classesJar = Join-Path $BuildDir 'classes.jar'
Run-Checked $jar @('cf',$classesJar,'-C',$classesDir,'.')
Run-Checked $java @('-cp',(Join-Path $toolPath 'lib\d8.jar'),'com.android.tools.r8.D8','--release','--min-api','26','--lib',$androidJar,'--output',$dexDir,$classesJar)
$compiledDir = Join-Path $BuildDir 'compiled-resources'
New-Item -ItemType Directory -Force -Path $compiledDir | Out-Null
$resourceInputs = @(Get-ChildItem -LiteralPath (Join-Path $projectPath 'app\src\main\res') -Recurse -File)
foreach ($resource in $resourceInputs) {
    Run-Checked (Join-Path $toolPath 'aapt2.exe') @('compile',$resource.FullName,'-o',$compiledDir)
}
$compiledResources = @(Get-ChildItem -LiteralPath $compiledDir -Filter '*.flat' | ForEach-Object { $_.FullName })
$unsigned = Join-Path $BuildDir 'unsigned.apk'
Run-Checked (Join-Path $toolPath 'aapt2.exe') (@('link','-o',$unsigned,'--manifest',(Join-Path $projectPath 'app\src\main\AndroidManifest.xml'),'-I',$androidJar,'-A',(Join-Path $projectPath 'app\src\main\assets'),'--min-sdk-version','26','--target-sdk-version','36','--version-code',[string]$VersionCode,'--version-name',$VersionName) + $compiledResources)
Run-Checked $jar @('uf',$unsigned,'-C',$dexDir,'classes.dex')
$aligned = Join-Path $BuildDir 'aligned.apk'
Run-Checked (Join-Path $toolPath 'zipalign.exe') @('-f','-p','4',$unsigned,$aligned)
if (-not (Test-Path -LiteralPath $SigningKey)) {
    Run-Checked (Join-Path $JdkPath 'bin\keytool.exe') @('-genkeypair','-keystore',$SigningKey,'-storetype','PKCS12','-storepass','android','-keypass','android','-alias','preview','-keyalg','RSA','-keysize','2048','-validity','3650','-dname','CN=LUT Schedule Preview, OU=Noncommercial, O=Personal, C=CN')
}
$apk = Join-Path $projectPath "..\LUT-Schedule-$VersionName.apk"
Run-Checked $java @('-jar',(Join-Path $toolPath 'lib\apksigner.jar'),'sign','--ks',$SigningKey,'--ks-key-alias','preview','--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,$aligned)
Run-Checked $java @('-jar',(Join-Path $toolPath 'lib\apksigner.jar'),'verify','--verbose','--print-certs',$apk)
Run-Checked (Join-Path $toolPath 'aapt.exe') @('dump','badging',$apk)
Get-Item -LiteralPath $apk | Select-Object Name,Length
Get-FileHash -LiteralPath $apk -Algorithm SHA256
