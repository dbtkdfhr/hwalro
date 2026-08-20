[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$SetupScript = Join-Path $PSScriptRoot '..\..\..\scripts\setup-simulation-engine.mjs'
& node $SetupScript
if ($LASTEXITCODE -ne 0) {
    throw "Simulation engine setup failed (exit code: $LASTEXITCODE)"
}
