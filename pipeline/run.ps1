# Run the locked Python 3.12 pipeline without installing Python or uv on Windows.
# Paths passed to the CLI are relative to pipeline/ inside /repo.
$ErrorActionPreference = 'Stop'
$pipelineRepo = Split-Path -Parent $PSScriptRoot

# 'extract' and 'embed-scripture' are the only commands that need the OpenAI key. It is
# decrypted from the DPAPI secret into this process's environment only, passed to the container
# by name (never as a literal docker CLI argument, so it never lands in process-listing tools),
# and cleared again whether or not the run succeeds. The plaintext key is never written to disk.
$needsOpenAIKey = $args.Count -gt 0 -and $args[0] -in @('extract', 'embed-scripture')
if ($needsOpenAIKey) {
    $secretFile = Join-Path $env:LOCALAPPDATA 'Verbum\secrets\openai-key.dpapi'
    if (-not (Test-Path -LiteralPath $secretFile)) {
        throw "OpenAI key not found at $secretFile. Run backend/scripts/Set-OpenAIKey.ps1 first."
    }
    $secure = ConvertTo-SecureString -String (Get-Content -LiteralPath $secretFile -Raw)
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        $env:OPENAI_API_KEY = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

$dockerArgs = @(
    'run', '--rm', '--network', 'verbum-backend_default',
    '--mount', "type=bind,source=$pipelineRepo,target=/repo",
    '--mount', 'type=volume,source=verbum-pipeline-venv,target=/venv',
    '-e', 'UV_PROJECT_ENVIRONMENT=/venv',
    '-e', 'UV_LINK_MODE=copy',
    '-e', 'VERBUM_DATABASE_URL',
    '-e', 'VERBUM_TEST_DATABASE_URL',
    '-e', 'VERBUM_TEST_API_BINARY',
    '-e', 'VERBUM_OPENAI_MODEL'
)
if ($needsOpenAIKey) {
    $dockerArgs += @('-e', 'OPENAI_API_KEY')
}
$dockerArgs += @(
    '-w', '/repo/pipeline',
    'ghcr.io/astral-sh/uv:python3.12-bookworm-slim',
    'uv', 'run', '--locked'
)
if ($args.Count -gt 0 -and $args[0] -eq 'test') {
    $dockerArgs += 'pytest'
    $dockerArgs += @($args | Select-Object -Skip 1)
} else {
    $dockerArgs += 'verbum-pipeline'
    $dockerArgs += $args
}

try {
    & docker @dockerArgs
    exit $LASTEXITCODE
} finally {
    if ($needsOpenAIKey) {
        Remove-Item Env:\OPENAI_API_KEY -ErrorAction SilentlyContinue
    }
}
