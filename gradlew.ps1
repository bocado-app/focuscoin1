param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArgs)

$ErrorActionPreference = "Stop"
$version = "8.9"
$gradleUserHome = $env:GRADLE_USER_HOME
if ([string]::IsNullOrWhiteSpace($gradleUserHome)) {
    $gradleUserHome = Join-Path $env:USERPROFILE ".gradle"
}
$distributionDir = Join-Path $gradleUserHome "wrapper\dists\focuscoin-gradle-$version\gradle-$version"
$gradleCommand = Join-Path $distributionDir "bin\gradle.bat"

if (-not (Test-Path -LiteralPath $gradleCommand)) {
    $parent = Split-Path -Parent $distributionDir
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    $archive = Join-Path $parent "gradle-$version-bin.zip"
    $url = "https://services.gradle.org/distributions/gradle-$version-bin.zip"
    Write-Host "Gradle $version 다운로드 중..."
    Invoke-WebRequest -Uri $url -OutFile $archive
    Expand-Archive -LiteralPath $archive -DestinationPath $parent -Force
    Remove-Item -LiteralPath $archive -Force
}

& $gradleCommand @GradleArgs
exit $LASTEXITCODE
