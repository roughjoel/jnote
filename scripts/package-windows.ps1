[CmdletBinding()]
param(
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$inputDirectory = Join-Path $projectRoot "target\jpackage-input"
$outputDirectory = Join-Path $projectRoot "target\package"
$mainJar = "jnote-1.0.0.jar"
$iconPath = Join-Path $projectRoot "src\main\packaging\jnote.ico"

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven is required but was not found on PATH."
}

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw "jpackage is required but was not found on PATH. Use JDK 17 or newer."
}

Push-Location $projectRoot
try {
    $packageArguments = @("clean", "package")
    if ($SkipTests) {
        $packageArguments += "-DskipTests"
    }

    & mvn @packageArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Maven package failed with exit code $LASTEXITCODE."
    }

    New-Item -ItemType Directory -Force -Path $inputDirectory | Out-Null
    & mvn "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies" `
        "-DincludeScope=runtime" `
        "-DoutputDirectory=$inputDirectory"
    if ($LASTEXITCODE -ne 0) {
        throw "Copying runtime dependencies failed with exit code $LASTEXITCODE."
    }

    Copy-Item -LiteralPath (Join-Path $projectRoot "target\$mainJar") -Destination $inputDirectory
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

    & jpackage `
        --type app-image `
        --name Jnote `
        --app-version 1.0.0 `
        --vendor Jnote `
        --description "A lightweight desktop note-taking application" `
        --dest $outputDirectory `
        --input $inputDirectory `
        --main-jar $mainJar `
        --main-class com.jnote.JnoteLauncher `
        --icon $iconPath `
        --add-modules java.se,jdk.charsets,jdk.jsobject,jdk.unsupported,jdk.xml.dom `
        --java-options "-Dfile.encoding=UTF-8"
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE."
    }

    Write-Host "Jnote application created at: $(Join-Path $outputDirectory 'Jnote\Jnote.exe')"
}
finally {
    Pop-Location
}
