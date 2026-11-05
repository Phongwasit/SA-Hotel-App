param([switch]$Rebuild)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'tools\Local-Database.ps1')
. (Join-Path $PSScriptRoot 'tools\Invoke-Maven.ps1')
$settings = Start-LocalDatabase
$jar = Join-Path $PSScriptRoot 'target\hotel-frontdesk-0.1.0.jar'
Push-Location $PSScriptRoot
try {
    if (Test-LocalMysqlPort 8080) { throw 'Port 8080 is already in use. Run Stop-Hotel.ps1 before rebuilding; the existing process and JAR were left untouched.' }
    if ($Rebuild -or -not (Test-Path -LiteralPath $jar)) {
        Invoke-HotelMaven -Goals @('-DskipTests','package')
    }
    $environment = @{
        HOTEL_DB_PASSWORD=$settings.appPassword; HOTEL_DB_USER='hotel_app';
        HOTEL_DB_URL="jdbc:mysql://127.0.0.1:$($settings.port)/hotel_frontdesk?connectionTimeZone=%2B07:00&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true";
        HOTEL_BOOTSTRAP_ENABLED='true'; HOTEL_STAFF01_PASSWORD=$settings.staff01Password; HOTEL_STAFF02_PASSWORD=$settings.staff02Password
    }
    $previous = @{}
    try {
        foreach ($key in $environment.Keys) { $previous[$key]=[Environment]::GetEnvironmentVariable($key,'Process'); [Environment]::SetEnvironmentVariable($key,$environment[$key],'Process') }
        $javaExecutable = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { (Get-Command java).Source }
        $process = Start-Process -FilePath $javaExecutable -ArgumentList @('-jar', "`"$jar`"") -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $script:Runtime 'app.log') -RedirectStandardError (Join-Path $script:Runtime 'app-error.log')
        $process.Id | Set-Content -LiteralPath (Join-Path $script:Runtime 'app.pid')
    } finally { foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key,$previous[$key],'Process') } }
    $ready = $false
    $lastStartupError = ''
    for ($attempt=0; $attempt -lt 120; $attempt++) {
        if ($process.HasExited) { throw 'Application stopped during startup. See .runtime/app.log and app-error.log.' }
        try { $response=Invoke-WebRequest -Uri 'http://127.0.0.1:8080/login' -UseBasicParsing -TimeoutSec 2; if ($response.StatusCode -eq 200) { $ready=$true; break } } catch { $lastStartupError=$_.Exception.Message }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "Application is not ready after 60 seconds. $lastStartupError See .runtime/app.log." }
    Write-Output 'Hotel application: http://127.0.0.1:8080'
    Write-Output 'Demo account passwords are in .runtime/demo-accounts.txt (not printed to logs).'
} finally { Pop-Location }
