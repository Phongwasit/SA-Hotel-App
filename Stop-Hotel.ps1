param([switch]$IncludeDatabase)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'tools\Local-Database.ps1')
$expectedJar = Join-Path $PSScriptRoot 'target\hotel-frontdesk-0.1.0.jar'
# Oracle's javapath launcher can create a second java.exe with another PID.
# Stop only JVMs whose command line contains THIS project's absolute JAR path.
$owned = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($expectedJar) })
foreach ($process in $owned) {
    Stop-Process -Id $process.ProcessId -ErrorAction SilentlyContinue
}
if ($owned.Count -gt 0) { Write-Output 'Hotel application stopped.' }
if ($IncludeDatabase -and (Test-Path -LiteralPath (Join-Path $script:Runtime 'mysql-admin.cnf'))) {
    $databaseProcesses = @(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" | Where-Object { $_.ExecutablePath -and $_.ExecutablePath.StartsWith($script:MysqlBase + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) })
    & (Join-Path $script:MysqlBase 'bin\mysqladmin.exe') "--defaults-extra-file=$(Join-Path $script:Runtime 'mysql-admin.cnf')" shutdown
    if ($LASTEXITCODE -ne 0) { throw 'Database shutdown failed.' }
    # On Windows mysqladmin may return before InnoDB has released its files.
    foreach ($databaseProcess in $databaseProcesses) {
        $running = Get-Process -Id $databaseProcess.ProcessId -ErrorAction SilentlyContinue
        if ($running -and -not $running.WaitForExit(30000)) { throw 'MySQL is still shutting down. Wait before starting it again.' }
    }
    Write-Output 'Local hotel database stopped.'
}
