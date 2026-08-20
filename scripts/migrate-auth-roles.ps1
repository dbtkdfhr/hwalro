$ErrorActionPreference = 'Stop'
$authDbPassword = if ([string]::IsNullOrEmpty($env:AUTH_DB_PASSWORD)) { 'hwalro_auth' } else { $env:AUTH_DB_PASSWORD }
$dmlPath = Join-Path $PSScriptRoot '..\apps\auth-service\src\main\resources\db\dml.sql'
$optionFilePath = [System.IO.Path]::GetTempFileName()
$containerOptionFilePath = "/tmp/hwalro-auth-$([Guid]::NewGuid().ToString('N')).cnf"
$dmlStream = $null
$process = $null
$processStarted = $false

try {
  $dmlStream = [System.IO.File]::OpenRead($dmlPath)
  $escapedPassword = $authDbPassword.Replace('\', '\\').Replace('"', '\"')
  [System.IO.File]::WriteAllText($optionFilePath, "[client]`npassword=`"$escapedPassword`"`n", [System.Text.UTF8Encoding]::new($false))

  $acl = Get-Acl -LiteralPath $optionFilePath
  $acl.SetAccessRuleProtection($true, $false)
  $acl.SetAccessRule([System.Security.AccessControl.FileSystemAccessRule]::new([System.Security.Principal.WindowsIdentity]::GetCurrent().User, 'FullControl', 'Allow'))
  Set-Acl -LiteralPath $optionFilePath -AclObject $acl

  & docker cp $optionFilePath "hwalro-mysql:$containerOptionFilePath"
  if ($LASTEXITCODE -ne 0) {
    throw 'Failed to copy the MySQL option file into the container.'
  }

  & docker exec hwalro-mysql chmod 600 $containerOptionFilePath
  if ($LASTEXITCODE -ne 0) {
    throw 'Failed to secure the MySQL option file in the container.'
  }

  $processInfo = [System.Diagnostics.ProcessStartInfo]::new()
  $processInfo.FileName = 'docker'
  $processInfo.Arguments = "exec -i hwalro-mysql mysql --defaults-extra-file=$containerOptionFilePath --default-character-set=utf8mb4 -uhwalro_auth hwalro_auth"
  $processInfo.UseShellExecute = $false
  $processInfo.RedirectStandardInput = $true

  $process = [System.Diagnostics.Process]::new()
  $process.StartInfo = $processInfo
  $null = $process.Start()
  $processStarted = $true

  try {
    $dmlStream.CopyTo($process.StandardInput.BaseStream)
  } finally {
    $process.StandardInput.Close()
  }

  $process.WaitForExit()
  $exitCode = $process.ExitCode
} finally {
  if ($null -ne $dmlStream) {
    $dmlStream.Dispose()
  }

  if ($null -ne $process) {
    try {
      $process.StandardInput.Close()
      if ($processStarted -and -not $process.HasExited) {
        $process.Kill()
        $process.WaitForExit()
      }
    } finally {
      $process.Dispose()
    }
  }

  & docker exec hwalro-mysql rm -f $containerOptionFilePath | Out-Null
  Remove-Item -LiteralPath $optionFilePath -Force -ErrorAction SilentlyContinue
}

if ($exitCode -ne 0) {
  exit $exitCode
}

Write-Output 'roles DML applied to existing hwalro_auth volume'
