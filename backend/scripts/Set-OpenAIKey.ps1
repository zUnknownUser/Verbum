# Windows-only local secret storage. DPAPI binds ciphertext to this Windows user.
# Run interactively when rotating the key; the plaintext is never written to disk.
param([System.Security.SecureString]$Key)

$ErrorActionPreference = 'Stop'
if ($null -eq $Key) {
    $Key = Read-Host 'OpenAI API key' -AsSecureString
}
if ($Key.Length -eq 0) { throw 'The API key cannot be empty.' }

$secretDirectory = Join-Path $env:LOCALAPPDATA 'Verbum\secrets'
[System.IO.Directory]::CreateDirectory($secretDirectory) | Out-Null
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
$system = [System.Security.Principal.SecurityIdentifier]::new('S-1-5-18')
$acl = [System.Security.AccessControl.DirectorySecurity]::new()
$acl.SetOwner($identity)
$acl.SetAccessRuleProtection($true, $false)
foreach ($principal in @($identity, $system)) {
    $rule = [System.Security.AccessControl.FileSystemAccessRule]::new(
        $principal, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'
    )
    $acl.AddAccessRule($rule)
}
Set-Acl -LiteralPath $secretDirectory -AclObject $acl
$secretFile = Join-Path $secretDirectory 'openai-key.dpapi'
$encrypted = ConvertFrom-SecureString -SecureString $Key
[System.IO.File]::WriteAllText($secretFile, $encrypted, [System.Text.Encoding]::ASCII)
Write-Output 'OpenAI key stored with Windows DPAPI outside the repository.'
