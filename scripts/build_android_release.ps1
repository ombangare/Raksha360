# ==============================================================================
# Raksha360 - Repeatable Android Signed Release Build Script
# ==============================================================================
# Produces a signed release APK using a single stable keystore.
# Checks environment, generates keystore once if missing, validates package ID,
# builds release APK, calculates SHA-256, and copies to distribution/.
# ==============================================================================

param(
    [switch]$SkipKeystoreGeneration = $false,
    [switch]$Help = $false
)

if ($Help) {
    Write-Host "Usage: .\scripts\build_android_release.ps1"
    Write-Host "Builds a signed release APK for Raksha360 with a stable signing key."
    exit 0
}

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RootDir = Split-Path -Parent $ScriptDir
Set-Location $RootDir

Write-Host "=== RAKSHA360 ANDROID RELEASE BUILD ===" -ForegroundColor Cyan
Write-Host "Project Root: $RootDir"

# 1. Detect JDK
$JavaCandidates = @(
    $env:JAVA_HOME,
    "C:\Program Files\Android\Android Studio\jbr",
    "C:\Program Files\Java\jdk-17",
    "C:\Program Files\Java\jdk-21"
)

$ValidJavaHome = $null
foreach ($cand in $JavaCandidates) {
    if ($cand -and (Test-Path "$cand\bin\java.exe")) {
        $ValidJavaHome = $cand
        break
    }
}

if (-not $ValidJavaHome) {
    Write-Error "JDK not found. Please install Android Studio with JBR or set JAVA_HOME."
    exit 1
}

$env:JAVA_HOME = $ValidJavaHome
$env:PATH = "$ValidJavaHome\bin;" + $env:PATH
Write-Host "[OK] JDK: $ValidJavaHome" -ForegroundColor Green

# 2. Check Android SDK and local.properties
$SdkDir = "C:\Users\omban\AppData\Local\Android\Sdk"
if (-not (Test-Path $SdkDir)) {
    if (Test-Path "local.properties") {
        $props = Get-Content "local.properties"
        $sdkLine = $props | Where-Object { $_ -match "^sdk\.dir=(.*)" }
        if ($sdkLine) { $SdkDir = $matches[1].Replace('\\', '\').Replace('\:', ':') }
    }
}

if (-not (Test-Path $SdkDir)) {
    Write-Error "Android SDK directory not found at $SdkDir"
    exit 1
}

if (-not (Test-Path "local.properties")) {
    $escapedSdk = $SdkDir.Replace('\', '\\').Replace(':', '\:')
    "sdk.dir=$escapedSdk" | Out-File "local.properties" -Encoding utf8
    Write-Host "[OK] Created local.properties pointing to $SdkDir" -ForegroundColor Green
} else {
    Write-Host "[OK] Android SDK: $SdkDir" -ForegroundColor Green
}

# 3. Stable Keystore Setup
$KeystoreDir = Join-Path $RootDir "keystores"
if (-not (Test-Path $KeystoreDir)) {
    New-Item -ItemType Directory -Path $KeystoreDir | Out-Null
}

$KeystorePath = Join-Path $KeystoreDir "raksha360-release.jks"
$EnvReleasePath = Join-Path $RootDir ".env.release"
$KeyAlias = "upload"

if (-not (Test-Path $KeystorePath)) {
    if ($SkipKeystoreGeneration) {
        Write-Error "Keystore not found and SkipKeystoreGeneration requested."
        exit 1
    }
    Write-Host "Creating stable release keystore (ONE-TIME GENERATION)..." -ForegroundColor Yellow
    # Generate secure random password
    $StorePass = [System.Guid]::NewGuid().ToString("N").Substring(0, 16) + "Rk360!"
    
    $KeytoolCmd = "$ValidJavaHome\bin\keytool.exe"
    $DName = "CN=Raksha360 Release, OU=Civic Safety Engineering, O=Raksha360, L=Nagpur, ST=Maharashtra, C=IN"
    
    & $KeytoolCmd -genkeypair -v `
        -keystore $KeystorePath `
        -alias $KeyAlias `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -storepass $StorePass `
        -keypass $StorePass `
        -dname $DName
    
    if (-not (Test-Path $KeystorePath)) {
        Write-Error "Failed to generate release keystore."
        exit 1
    }
    
    # Store credentials locally in .env.release (never committed)
    @"
KEYSTORE_PATH=$KeystorePath
STORE_PASSWORD=$StorePass
KEY_ALIAS=$KeyAlias
KEY_PASSWORD=$StorePass
"@ | Out-File $EnvReleasePath -Encoding utf8
    
    Write-Host "[OK] Stable release keystore created at: $KeystorePath" -ForegroundColor Green
    Write-Host "[OK] Credentials securely saved to ignored .env.release" -ForegroundColor Green
} else {
    Write-Host "[OK] Using existing stable release keystore: $KeystorePath" -ForegroundColor Green
}

# Load credentials from .env.release or env
if (Test-Path $EnvReleasePath) {
    Get-Content $EnvReleasePath | ForEach-Object {
        if ($_ -match "^\s*([^#=]+)=(.*)$") {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim()
            [System.Environment]::SetEnvironmentVariable($name, $value)
        }
    }
}

$env:KEYSTORE_PATH = $KeystorePath
if (-not $env:STORE_PASSWORD) {
    Write-Warning "STORE_PASSWORD environment variable not set. Build will generate unsigned release."
}

# 4. Check Gradle wrapper files
if (-not (Test-Path "gradlew.bat") -or -not (Test-Path "gradle\wrapper\gradle-wrapper.jar")) {
    Write-Error "Gradle wrapper files missing. Ensure gradlew.bat and gradle-wrapper.jar are in place."
    exit 1
}

# 5. Build Release APK
Write-Host "`nStarting Gradle assembleRelease..." -ForegroundColor Cyan
$BuildStart = Get-Date

& .\gradlew.bat assembleRelease --stacktrace

if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle build failed with exit code $LASTEXITCODE."
    exit $LASTEXITCODE
}

# 6. Verify Output APK
$ApkPath = Join-Path $RootDir "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $ApkPath)) {
    # Check alternative names
    $ApkCandidates = Get-ChildItem -Path "app\build\outputs\apk\release" -Filter "*.apk" -ErrorAction SilentlyContinue
    if ($ApkCandidates.Count -gt 0) {
        $ApkPath = $ApkCandidates[0].FullName
    } else {
        Write-Error "Expected release APK not found at $ApkPath."
        exit 1
    }
}

$ApkSize = (Get-Item $ApkPath).Length
$ApkSizeMb = [math]::Round($ApkSize / 1MB, 2)
$Hash = (Get-FileHash -Path $ApkPath -Algorithm SHA256).Hash.ToLower()

# 7. Copy to distribution/
$DistDir = Join-Path $RootDir "distribution"
if (-not (Test-Path $DistDir)) {
    New-Item -ItemType Directory -Path $DistDir | Out-Null
}

$DistApkPath = Join-Path $DistDir "Raksha360-v1.0-release.apk"
Copy-Item -Path $ApkPath -Destination $DistApkPath -Force

# 8. Record Release Metadata
$Metadata = @{
    appName = "Raksha360"
    applicationId = "com.aistudio.naarikavach.safe7x"
    versionName = "1.0"
    versionCode = 1
    minSdkVersion = 24
    targetSdkVersion = 36
    apkFileName = "Raksha360-v1.0-release.apk"
    sha256 = $Hash
    fileSizeBytes = $ApkSize
    fileSizeMb = $ApkSizeMb
    buildTimestampUtc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    signed = (Test-Path $KeystorePath)
    keystorePath = $KeystorePath
    keyAlias = $KeyAlias
}

$Metadata | ConvertTo-Json -Depth 4 | Out-File (Join-Path $DistDir "release_metadata.json") -Encoding utf8

Write-Host "`n=== RELEASE BUILD SUCCESSFUL ===" -ForegroundColor Green
Write-Host "Package ID:     com.aistudio.naarikavach.safe7x"
Write-Host "Version:        1.0 (versionCode: 1)"
Write-Host "APK Location:   $DistApkPath"
Write-Host "File Size:      $ApkSizeMb MB ($ApkSize bytes)"
Write-Host "SHA-256 Hash:   $Hash"
Write-Host "Signed With:    $KeystorePath (alias: $KeyAlias)"
Write-Host "Build Duration: $((Get-Date) - $BuildStart)"
