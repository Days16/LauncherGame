$ErrorActionPreference = "Stop"

# Configuration
$AppName = "Minecraft Launcher"
$InstallDir = Join-Path $HOME "Documents\MinecraftLauncher"
$JarName = "minecraft-launcher-dark-0.1.jar"
$JarSource = Join-Path $PSScriptRoot "target\$JarName"

Write-Host "--- $AppName Installer (No Java Check) ---" -ForegroundColor Cyan

# 1. Create Install Directory
if (-not (Test-Path $InstallDir)) {
    Write-Host "Creating installation directory: $InstallDir" -ForegroundColor Gray
    New-Item -ItemType Directory -Path $InstallDir | Out-Null
}

# 2. Copy Application Files
if (-not (Test-Path $JarSource)) {
    Write-Host "Error: Application build not found. Please run 'mvn package' first." -ForegroundColor Red
    exit 1
}

Write-Host "Installing application..." -ForegroundColor Gray
Copy-Item -Path $JarSource -Destination $InstallDir -Force

# 3. Create Desktop Shortcut
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
