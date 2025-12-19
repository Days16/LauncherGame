$ErrorActionPreference = "Stop"

$MavenVersion = "3.9.6"
$MavenUrl = "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/apache-maven-$MavenVersion-bin.zip"
$InstallDir = Join-Path $PSScriptRoot ".mvn_dist"
$MavenBin = Join-Path $InstallDir "apache-maven-$MavenVersion/bin/mvn.cmd"

# 1. Ensure Maven is available
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    if (-not (Test-Path $MavenBin)) {
        Write-Host "Maven not found. Downloading Maven $MavenVersion..." -ForegroundColor Cyan
        if (-not (Test-Path $InstallDir)) {
            New-Item -ItemType Directory -Path $InstallDir | Out-Null
        }
        $ZipPath = Join-Path $InstallDir "maven.zip"
        Invoke-WebRequest -Uri $MavenUrl -OutFile $ZipPath
        Write-Host "Extracting Maven..." -ForegroundColor Cyan
        Expand-Archive -Path $ZipPath -DestinationPath $InstallDir -Force
        Remove-Item $ZipPath
    }
    $mvn = $MavenBin
} else {
    $mvn = "mvn"
}

# 2. Build and Package
Write-Host "Building and packaging as EXE..." -ForegroundColor Green
& $mvn clean package

$ExePath = Join-Path $PSScriptRoot "target\minecraft-launcher-dark.exe"
if (Test-Path $ExePath) {
    Write-Host "--- Build Successful! ---" -ForegroundColor Green
    Write-Host "Your EXE is located at: $ExePath" -ForegroundColor Cyan
} else {
    Write-Host "Error: EXE was not generated. Check the logs above." -ForegroundColor Red
    exit 1
}
