param(
    [ValidateSet('pt-BR', 'en-US')][string]$Language = 'pt-BR',
    [string]$BaseURL = 'http://localhost:8080',
    [string]$OutputFile
)
$ErrorActionPreference = 'Stop'
if (-not $env:VERBUM_FIREBASE_ID_TOKEN) { throw 'Set VERBUM_FIREBASE_ID_TOKEN to a current Firebase client ID token (see backend/SECURITY.md).' }
$backendDirectory = Split-Path -Parent $PSScriptRoot
$repoDirectory = Split-Path -Parent $backendDirectory
$requestFile = Join-Path $repoDirectory "api\examples\tts\request-$Language.json"
if (-not $OutputFile) { $OutputFile = Join-Path $backendDirectory "tmp\tts-$Language.mp3" }
$outputPath = [IO.Path]::GetFullPath($OutputFile)
if (Test-Path -LiteralPath $outputPath) { throw 'Output exists. Supply a new -OutputFile to keep the previous audio.' }
[IO.Directory]::CreateDirectory((Split-Path -Parent $outputPath)) | Out-Null
Invoke-WebRequest -UseBasicParsing -Uri "$($BaseURL.TrimEnd('/'))/v1/tts" `
    -Headers @{ Authorization = "Bearer $($env:VERBUM_FIREBASE_ID_TOKEN)" } `
    -Method Post -ContentType 'application/json' -Body ([IO.File]::ReadAllBytes($requestFile)) `
    -OutFile $outputPath -TimeoutSec 630
Write-Output "MP3 saved: $outputPath"
