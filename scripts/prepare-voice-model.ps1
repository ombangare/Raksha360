$ErrorActionPreference = "Stop"

# Vosk model licenses are Apache 2.0. See https://alphacephei.com/vosk/models
# The models run locally after being bundled into the APK; no audio is sent to Vosk.
$root = Split-Path -Parent $PSScriptRoot
$assets = Join-Path $root "app/src/main/assets"
$tempRoot = Join-Path $env:TEMP "raksha360-vosk-models"
New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null
New-Item -ItemType Directory -Force -Path $assets | Out-Null

$models = @(
    @{
        Name = "vosk-model-small-en-us-0.15"
        Url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        Required = @("am/final.mdl", "conf/model.conf", "graph/HCLr.fst")
    },
    @{
        Name = "vosk-model-spk-0.4"
        Url = "https://alphacephei.com/vosk/models/vosk-model-spk-0.4.zip"
        Required = @("final.ext.raw", "mfcc.conf", "mean.vec", "transform.mat")
    }
)

foreach ($model in $models) {
    $destination = Join-Path $assets $model.Name
    $missingFiles = @($model.Required | Where-Object { !(Test-Path (Join-Path $destination $_)) })
    if ($missingFiles.Count -eq 0) {
        Write-Host "Already prepared: $($model.Name)"
        continue
    }

    $zipPath = Join-Path $tempRoot ($model.Name + ".zip")
    $extractRoot = Join-Path $tempRoot ($model.Name + "-extract")
    if (Test-Path $extractRoot) { Remove-Item -Recurse -Force $extractRoot }
    New-Item -ItemType Directory -Force -Path $extractRoot | Out-Null

    Write-Host "Downloading $($model.Name) from the official Vosk model host..."
    Invoke-WebRequest -Uri $model.Url -OutFile $zipPath
    Expand-Archive -Path $zipPath -DestinationPath $extractRoot -Force

    $source = Join-Path $extractRoot $model.Name
    if (!(Test-Path $source)) {
        throw "Expected extracted model folder not found: $source"
    }
    foreach ($relative in $model.Required) {
        if (!(Test-Path (Join-Path $source $relative))) {
            throw "Model archive is missing expected file: $relative"
        }
    }
    if (Test-Path $destination) { Remove-Item -Recurse -Force $destination }
    Copy-Item -Path $source -Destination $destination -Recurse -Force
    Write-Host "Prepared: $destination"
}

Write-Host "Both offline models are in app/src/main/assets. Rebuild the APK; the app will copy the models to private app storage on first Guard Mode start."
