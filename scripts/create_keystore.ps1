# Windows PowerShell. Run on YOUR machine only. Creates the release signing key for one app.
# Finds keytool by itself (Android Studio, JAVA_HOME, installed JDKs). openssl (Git for Windows) is optional.
param([Parameter(Mandatory=$true)][string]$App, [string]$Out = "$HOME\app-keystores\$App")
$ErrorActionPreference = "Stop"

function Find-Tool([string]$name, [string[]]$candidates) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($c in $candidates) {
        foreach ($p in (Resolve-Path $c -ErrorAction SilentlyContinue)) { if (Test-Path $p) { return $p.Path } }
    }
    return $null
}

$keytool = Find-Tool "keytool" @(
    "$env:JAVA_HOME\bin\keytool.exe",
    "$env:ProgramFiles\Android\Android Studio*\jbr\bin\keytool.exe",
    "$env:ProgramFiles\Android\Android Studio*\jre\bin\keytool.exe",
    "$env:LOCALAPPDATA\Programs\Android Studio*\jbr\bin\keytool.exe",
    "$env:ProgramFiles\Eclipse Adoptium\*\bin\keytool.exe",
    "$env:ProgramFiles\Java\*\bin\keytool.exe",
    "$env:ProgramFiles\Microsoft\jdk*\bin\keytool.exe"
)
if (-not $keytool) {
    Write-Host "keytool not found. Install a JDK once, then run this script again:" -ForegroundColor Yellow
    Write-Host "  winget install -e --id EclipseAdoptium.Temurin.17.JDK" -ForegroundColor Yellow
    exit 1
}
$openssl = Find-Tool "openssl" @("$env:ProgramFiles\Git\usr\bin\openssl.exe", "$env:ProgramFiles\Git\mingw64\bin\openssl.exe")
Write-Host "keytool: $keytool"

New-Item -ItemType Directory -Force -Path $Out | Out-Null
$ks = Join-Path $Out "$App.p12"
if (Test-Path $ks) { throw "Keystore already exists: $ks (never create a second key for the same app)" }

$sec = Read-Host "Keystore password (min 12 chars)" -AsSecureString
$pass = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
if ($pass.Length -lt 12) { throw "Password too short" }
$sec2 = Read-Host "Repeat password" -AsSecureString
$pass2 = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec2))
if ($pass -ne $pass2) { throw "Passwords do not match" }

$alias = "$App-release"
& $keytool -genkeypair -storetype PKCS12 -keystore $ks -alias $alias -keyalg RSA -keysize 4096 -validity 36500 `
    -storepass $pass -keypass $pass -dname "CN=$App, O=Moh, C=DE"
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $ks)) { throw "keytool failed" }

@"
storePassword=$pass
keyPassword=$pass
keyAlias=$alias
storeFile=$($ks -replace '\\','/')
"@ | Set-Content (Join-Path $Out "key.properties")

[Convert]::ToBase64String([IO.File]::ReadAllBytes($ks)) | Set-Content "$ks.b64" -NoNewline

if ($openssl) {
    & $openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -salt -in $ks -out "$ks.enc" -pass "pass:$pass"
    if ($LASTEXITCODE -eq 0) { Write-Host "Encrypted backup: $ks.enc" }
} else {
    Write-Host "openssl not found: back up $ks itself (it is protected by your password)." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "OK. Key created: $ks" -ForegroundColor Green
Write-Host "Back up the folder $Out in 2 safe places (USB stick + password manager / cloud). Never commit it."
Write-Host "GitHub secrets: KEYSTORE_B64 (from $ks.b64), KEYSTORE_PASSWORD, KEY_PASSWORD (same password), KEY_ALIAS=$alias"
