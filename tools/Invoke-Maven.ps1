function Invoke-HotelMaven {
    param([string[]]$Goals)
    $appRoot=Split-Path -Parent $PSScriptRoot
    $runtime=Join-Path $appRoot '.runtime'
    $runner=Join-Path $appRoot 'tools\run_maven.py'
    $stdout=Join-Path $runtime 'maven-runner-output.log'
    $stderr=Join-Path $runtime 'maven-runner-error.log'
    # A hidden process avoids the PowerShell host's native-console title error.
    # Maven itself is a direct java.exe child of the Python runner.
    $arguments=@('-3.12','-X','utf8',"`"$runner`"")+$Goals
    $process=Start-Process -FilePath (Get-Command py).Source -ArgumentList $arguments -WorkingDirectory $appRoot -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    Get-Content -Encoding UTF8 -LiteralPath $stdout
    if($process.ExitCode -ne 0) {
        if(Test-Path -LiteralPath $stderr){Get-Content -Encoding UTF8 -LiteralPath $stderr}
        throw "Maven failed with exit code $($process.ExitCode). See .runtime/maven-test.log or maven-build.log."
    }
}
