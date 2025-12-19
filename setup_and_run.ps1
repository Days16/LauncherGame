$ErrorActionPreference = "Stop"

$MavenVersion = "3.9.6"
$MavenUrl = "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/apache-maven-$MavenVersion-bin.zip"
$InstallDir = Join-Path $PSScriptRoot ".mvn_dist"
$MavenBin = Join-Path $InstallDir "apache-maven-$MavenVersion/bin/mvn.cmd"

# Check if Maven is already in PATH
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    Write-Host "Maven found in PATH. Running..." -ForegroundColor Green
    mvn clean javafx:run
    exit
}

# Check if local Maven is installed
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

Write-Host "Running with local Maven..." -ForegroundColor Green
& $MavenBin clean javafx:run
