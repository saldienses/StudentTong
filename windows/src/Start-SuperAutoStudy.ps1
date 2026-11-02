param([switch]$OpenBrowser)
& (Join-Path $PSScriptRoot 'Start-StudentTong.ps1') -NoClient
exit $LASTEXITCODE
