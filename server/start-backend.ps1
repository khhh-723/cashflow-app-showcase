$ErrorActionPreference = 'Stop'

$serverRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $serverRoot

$javaExe = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$jarPath = Join-Path $serverRoot 'build\libs\cashflow-server-1.0.0-SNAPSHOT.jar'
$logPath = Join-Path $serverRoot 'backend-manual.log'
$errPath = Join-Path $serverRoot 'backend-manual.err.log'

"[$(Get-Date -Format o)] Starting cashflow server" | Out-File -FilePath $logPath -Append -Encoding utf8

try {
    & $javaExe -jar $jarPath *>> $logPath
} catch {
    $_ | Out-File -FilePath $errPath -Append -Encoding utf8
    throw
}
