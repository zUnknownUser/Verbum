# Starts a new local API container; never replaces an existing container implicitly.
param(
    [string]$ContainerName = 'verbum-api-local',
    [int]$Port = 8080,
    [string]$Image = 'verbum-api:chirp'
)
$ErrorActionPreference = 'Stop'
$credential = $env:GOOGLE_APPLICATION_CREDENTIALS
if (-not $credential -or -not (Test-Path -LiteralPath $credential -PathType Leaf)) {
    throw 'Set GOOGLE_APPLICATION_CREDENTIALS to the local Service Account JSON path first.'
}
$credential = (Resolve-Path -LiteralPath $credential).Path
$previousOpenAI = $env:OPENAI_API_KEY
$previousDatabase = $env:VERBUM_DATABASE_URL
try {
    # Preserve the existing optional OpenAI services using the established DPAPI mechanism.
    $openAIFile = Join-Path $env:LOCALAPPDATA 'Verbum\secrets\openai-key.dpapi'
    if (-not $env:OPENAI_API_KEY -and (Test-Path -LiteralPath $openAIFile)) {
        $secure = ConvertTo-SecureString (Get-Content -LiteralPath $openAIFile -Raw)
        $buffer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { $env:OPENAI_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($buffer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($buffer) }
    }
    if (-not $env:VERBUM_DATABASE_URL) {
        $env:VERBUM_DATABASE_URL = 'postgres://verbum:verbum@db:5432/verbum?sslmode=disable'
    }
    & docker run -d --name $ContainerName --network verbum-backend_default `
        -p "${Port}:8080" -e VERBUM_DATABASE_URL -e OPENAI_API_KEY -e VERBUM_FIREBASE_PROJECT_ID `
        -e GOOGLE_APPLICATION_CREDENTIALS=/run/secrets/google-tts.json `
        --mount "type=volume,source=verbum-tts-cache,target=/var/cache/verbum/tts" `
        --mount "type=bind,source=$credential,target=/run/secrets/google-tts.json,readonly" $Image
    if ($LASTEXITCODE -ne 0) { throw 'Could not start API container. Existing containers were not modified.' }
} finally {
    $env:OPENAI_API_KEY = $previousOpenAI
    $env:VERBUM_DATABASE_URL = $previousDatabase
}
