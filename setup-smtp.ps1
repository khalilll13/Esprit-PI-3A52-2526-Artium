param(
    [string]$SmtpHost,
    [string]$SmtpPort,
    [string]$SmtpUsername,
    [string]$SmtpPassword,
    [string]$SmtpFrom,
    [string]$SmtpStartTls,
    [string]$SmtpSsl
)

function Resolve-Input {
    param(
        [string]$CurrentValue,
        [string]$Prompt,
        [string]$DefaultValue = "",
        [bool]$Required = $false
    )

    $value = $CurrentValue
    if ([string]::IsNullOrWhiteSpace($value)) {
        $raw = Read-Host $Prompt
        if ([string]::IsNullOrWhiteSpace($raw) -and -not [string]::IsNullOrWhiteSpace($DefaultValue)) {
            $value = $DefaultValue
        } else {
            $value = $raw
        }
    }

    $value = if ($null -eq $value) { "" } else { $value.Trim() }
    if ($Required -and [string]::IsNullOrWhiteSpace($value)) {
        throw "Valeur obligatoire manquante: $Prompt"
    }
    return $value
}

function Normalize-Bool {
    param([string]$Value, [string]$DefaultValue)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $DefaultValue
    }
    $normalized = $Value.Trim().ToLowerInvariant()
    switch ($normalized) {
        "1" { return "true" }
        "0" { return "false" }
        "yes" { return "true" }
        "no" { return "false" }
        "y" { return "true" }
        "n" { return "false" }
        "true" { return "true" }
        "false" { return "false" }
        default { throw "Valeur booléenne invalide: $Value (utilisez true/false)." }
    }
}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$configFile = Join-Path $root '.env'

Write-Host 'Configuration SMTP minimale' -ForegroundColor Cyan
Write-Host 'Les valeurs seront écrites dans le fichier .env a la racine du projet.'

try {
    $smtpHost = Resolve-Input -CurrentValue $SmtpHost -Prompt 'SMTP host (ex: smtp.gmail.com)' -Required $true
    $smtpPort = Resolve-Input -CurrentValue $SmtpPort -Prompt 'SMTP port (ex: 587)' -DefaultValue '587' -Required $true
    if ($smtpPort -notmatch '^[0-9]+$') {
        throw 'SMTP port invalide: utilisez uniquement des chiffres (ex: 587).'
    }

    $smtpUsername = Resolve-Input -CurrentValue $SmtpUsername -Prompt 'SMTP username' -Required $true
    $smtpPassword = Resolve-Input -CurrentValue $SmtpPassword -Prompt 'SMTP password' -Required $true
    $smtpFrom = Resolve-Input -CurrentValue $SmtpFrom -Prompt 'SMTP from (laisser vide pour utiliser username)'
    if ([string]::IsNullOrWhiteSpace($smtpFrom)) { $smtpFrom = $smtpUsername }

    $smtpStartTls = Resolve-Input -CurrentValue $SmtpStartTls -Prompt 'Use STARTTLS? (true/false) [true]' -DefaultValue 'true'
    $smtpSsl = Resolve-Input -CurrentValue $SmtpSsl -Prompt 'Use SSL? (true/false) [false]' -DefaultValue 'false'
    $smtpStartTls = Normalize-Bool -Value $smtpStartTls -DefaultValue 'true'
    $smtpSsl = Normalize-Bool -Value $smtpSsl -DefaultValue 'false'

    $lines = @()
    if (Test-Path $configFile) {
        $lines = Get-Content $configFile
    }

    $smtpVars = @{
        "SMTP_HOST" = $smtpHost
        "SMTP_PORT" = $smtpPort
        "SMTP_USERNAME" = $smtpUsername
        "SMTP_PASSWORD" = $smtpPassword
        "SMTP_FROM" = $smtpFrom
        "SMTP_STARTTLS" = $smtpStartTls
        "SMTP_SSL" = $smtpSsl
    }

    $newLines = @()
    $processedKeys = @{}

    foreach ($line in $lines) {
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith("#") -or -not $trimmed.Contains("=")) {
            $newLines += $line
            continue
        }
        
        $eqIndex = $trimmed.IndexOf('=')
        $key = $trimmed.Substring(0, $eqIndex).Trim()
        
        if ($smtpVars.ContainsKey($key)) {
            $newLines += "$key=$($smtpVars[$key])"
            $processedKeys[$key] = $true
        } else {
            $newLines += $line
        }
    }

    $headerAdded = $false
    foreach ($key in $smtpVars.Keys) {
        if (-not $processedKeys.ContainsKey($key)) {
            if (-not $headerAdded) {
                $newLines += ""
                $newLines += "# SMTP Configuration"
                $headerAdded = $true
            }
            $newLines += "$key=$($smtpVars[$key])"
        }
    }

    $newLines | Set-Content -Path $configFile -Encoding UTF8

    if (-not (Test-Path -Path $configFile)) {
        throw "Echec creation/modification fichier: $configFile"
    }

    Write-Host "Configuration SMTP enregistree dans $configFile" -ForegroundColor Green
    exit 0
}
catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}

