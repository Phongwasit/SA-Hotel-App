$ErrorActionPreference = 'Stop'
$script:AppRoot = Split-Path -Parent $PSScriptRoot
$script:Runtime = Join-Path $script:AppRoot '.runtime'
$script:SettingsFile = Join-Path $script:Runtime 'local-settings.json'
$script:MysqlBase = Join-Path $script:Runtime 'mysql-8.4.13-winx64'

function New-LocalPassword {
    $bytes = New-Object byte[] 24
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}

function Get-LocalSettings {
    if (-not (Test-Path -LiteralPath $script:SettingsFile)) {
        if (Test-Path -LiteralPath (Join-Path $script:Runtime 'mysql-data\mysql')) {
            throw 'Database exists but local-settings.json is missing. Restore that file; existing data will not be reinitialized.'
        }
        New-Item -ItemType Directory -Path $script:Runtime -Force | Out-Null
        $settings = [ordered]@{ port=3307; rootPassword=(New-LocalPassword); appPassword=(New-LocalPassword); staff01Password=(New-LocalPassword); staff02Password=(New-LocalPassword) }
        $settings | ConvertTo-Json | Set-Content -LiteralPath $script:SettingsFile -Encoding UTF8
        @('Local demonstration accounts (identical permissions).', ('reception01 : ' + $settings.staff01Password), ('reception02 : ' + $settings.staff02Password), 'Do not share this file or commit .runtime.') | Set-Content -LiteralPath (Join-Path $script:Runtime 'demo-accounts.txt') -Encoding UTF8
    }
    return Get-Content -LiteralPath $script:SettingsFile -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Test-LocalMysqlPort {
    param([int]$Port)
    $client = New-Object Net.Sockets.TcpClient
    try { return $client.ConnectAsync('127.0.0.1', $Port).Wait(300) -and $client.Connected } catch { return $false } finally { $client.Dispose() }
}

function Start-LocalDatabase {
    $settings = Get-LocalSettings
    $mysqld = Join-Path $script:MysqlBase 'bin\mysqld.exe'
    if (-not (Test-Path -LiteralPath $mysqld)) {
        & py -3.12 -X utf8 (Join-Path $PSScriptRoot 'download_mysql.py')
        if ($LASTEXITCODE -ne 0) { throw 'MySQL download did not finish.' }
    }
    $data = Join-Path $script:Runtime 'mysql-data'
    $config = Join-Path $script:Runtime 'mysql.ini'
    $admin = Join-Path $script:Runtime 'mysql-admin.cnf'
    $initFile = Join-Path $script:Runtime 'mysql-first-start.sql'
    $configText = @"
[mysqld]
basedir="$($script:MysqlBase.Replace('\','/'))"
datadir="$($data.Replace('\','/'))"
port=$($settings.port)
bind-address=127.0.0.1
mysqlx=0
default-time-zone=+07:00
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
log-error="$((Join-Path $script:Runtime 'mysql-error.log').Replace('\','/'))"
pid-file="$((Join-Path $script:Runtime 'mysql.pid').Replace('\','/'))"
"@
    [IO.File]::WriteAllText($config, $configText, (New-Object Text.UTF8Encoding($false)))
    [IO.File]::WriteAllText($admin, "[client]`nuser=root`npassword=$($settings.rootPassword)`nhost=127.0.0.1`nport=$($settings.port)`n", (New-Object Text.UTF8Encoding($false)))
    if (-not (Test-Path -LiteralPath (Join-Path $data 'mysql'))) {
        & $mysqld "--defaults-file=$config" --initialize-insecure
        if ($LASTEXITCODE -ne 0) { throw 'Database initialization failed. See .runtime/mysql-error.log.' }
        [IO.File]::WriteAllText($initFile, "ALTER USER 'root'@'localhost' IDENTIFIED BY '$($settings.rootPassword)';`n", (New-Object Text.UTF8Encoding($false)))
    }
    if (-not (Test-LocalMysqlPort $settings.port)) {
        $arguments = @("--defaults-file=`"$config`"")
        if (Test-Path -LiteralPath $initFile) { $arguments += "--init-file=`"$initFile`"" }
        $databaseProcess = Start-Process -FilePath $mysqld -ArgumentList $arguments -WorkingDirectory $script:AppRoot -WindowStyle Hidden -PassThru
        $ready = $false
        for ($attempt=0; $attempt -lt 120; $attempt++) {
            if (Test-LocalMysqlPort $settings.port) { $ready=$true; break }
            if ($databaseProcess.HasExited) { throw 'MySQL stopped during startup. See .runtime/mysql-error.log.' }
            Start-Sleep -Milliseconds 500
        }
        if (-not $ready) { throw 'MySQL did not start within 60 seconds. See .runtime/mysql-error.log.' }
    }
    # Use a private defaults file, not passwords in command-line arguments.
    $mysql = Join-Path $script:MysqlBase 'bin\mysql.exe'
    $bootstrap = @"
CREATE DATABASE IF NOT EXISTS hotel_frontdesk CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS hotel_frontdesk_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'hotel_app'@'127.0.0.1' IDENTIFIED BY '$($settings.appPassword)';
GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON hotel_frontdesk.* TO 'hotel_app'@'127.0.0.1';
GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON hotel_frontdesk_test.* TO 'hotel_app'@'127.0.0.1';
"@
    $bootstrap | & $mysql "--defaults-extra-file=$admin" --default-character-set=utf8mb4
    if ($LASTEXITCODE -ne 0) { throw 'Cannot provision the local databases. Existing settings/data were preserved.' }
    return $settings
}
