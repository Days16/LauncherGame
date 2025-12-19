$ErrorActionPreference = "Stop"

# Configuration
$AppName = "Minecraft Launcher"
$InstallDir = Join-Path $HOME "Documents\MinecraftLauncher"
$JarName = "minecraft-launcher-dark-0.1.jar"
$JarSource = Join-Path $PSScriptRoot "target\$JarName"
$IconPath = Join-Path $InstallDir "icon.ico" # Optional, if we had one

Write-Host "--- $AppName Installer ---" -ForegroundColor Cyan

# 1. Check Java Version
Write-Host "Checking Java version..." -ForegroundColor Gray
try {
    $javaVersionOutput = java -version 2>&1 | Out-String
    if ($javaVersionOutput -match 'version "(\d+)') {
        $version = [int]$matches[1]
        if ($version -lt 17) {
            Write-Host "Error: Java 17 or higher is required. Found version $version." -ForegroundColor Red
            exit 1
        }
        Write-Host "Java $version found. OK." -ForegroundColor Green
    } else {
        Write-Host "Error: Could not determine Java version. Please ensure Java 17+ is installed." -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "Error: Java is not installed. Please install Java 17 or higher." -ForegroundColor Red
    exit 1
}

# 2. Create Install Directory
if (-not (Test-Path $InstallDir)) {
    Write-Host "Creating installation directory: $InstallDir" -ForegroundColor Gray
    New-Item -ItemType Directory -Path $InstallDir | Out-Null
}

# 3. Copy Application Files
if (-not (Test-Path $JarSource)) {
    Write-Host "Error: Application build not found. Please run 'mvn package' first." -ForegroundColor Red
    exit 1
}

Write-Host "Installing application..." -ForegroundColor Gray
Copy-Item -Path $JarSource -Destination $InstallDir -Force

# 4. Create Desktop Shortcut
Write-Host "Creating desktop shortcut..." -ForegroundColor Gray
$WshShell = New-Object -ComObject WScript.Shell
$ShortcutPath = Join-Path ([Environment]::GetFolderPath("Desktop")) "$AppName.lnk"
$Shortcut = $WshShell.CreateShortcut($ShortcutPath)
$Shortcut.TargetPath = "javaw.exe"
$Shortcut.Arguments = "-jar `"$InstallDir\$JarName`""
$Shortcut.WorkingDirectory = $InstallDir
$Shortcut.Description = "Launch $AppName"
$Shortcut.Save()

Write-Host "--- Installation Complete! ---" -ForegroundColor Green
Write-Host "You can now launch the app from your Desktop." -ForegroundColor Cyan
